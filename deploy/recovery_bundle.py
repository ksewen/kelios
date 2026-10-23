#!/usr/bin/env python3
"""Private, fixed-layout managed-instance bundles. Docker orchestration stays in kelios."""
import ctypes
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import sys
import tempfile


FORMAT = 1
POSTGRES_MAJOR = 17
AUTH_FILE = re.compile(r"[A-Za-z0-9_-]{1,64}\.(?:aes|private\.pem|public\.pem)")
HASH = re.compile(r"[0-9a-f]{64}")
VERSION = re.compile(r"[0-9a-f]{12}")
BASE = {"database.dump", "instance/installation.conf", "instance/certificate.conf", "instance/secrets/db-password"}


def fail(message):
    raise ValueError(message)


def pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            fail("Duplicate bundle metadata field")
        result[key] = value
    return result


def owned_regular(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid():
        fail("Recovery input contains an unsafe file")


def private_regular(path):
    owned_regular(path)
    if path.stat().st_mode & 0o077:
        fail("Bundle file permissions are not private")


def owned_directory(path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid():
        fail("Recovery input contains an unsafe directory")


def private_directory(path):
    owned_directory(path)
    if path.stat().st_mode & 0o077:
        fail("Bundle directory permissions are not private")


def digest(path):
    sha = hashlib.sha256()
    size = 0
    with os.fdopen(os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)), "rb") as source:
        while chunk := source.read(1024 * 1024):
            sha.update(chunk)
            size += len(chunk)
    return {"sha256": sha.hexdigest(), "size": size}


def kv(path):
    private_regular(path)
    values = {}
    for line in path.read_text().splitlines():
        key, sep, value = line.partition("=")
        if not sep or key in values or not re.fullmatch(r"[A-Z_]+", key):
            fail("Invalid saved installation metadata")
        values[key] = value
    return values


def expected_files(root):
    instance = root / "instance"
    config = kv(instance / "installation.conf")
    certificate = kv(instance / "certificate.conf")
    if config.get("SCHEMA") != "1" or config.get("MODE") != "managed" or config.get("PHASE") != "initialized" or not VERSION.fullmatch(config.get("RELEASE", "")):
        fail("Bundle is not an initialized managed installation")
    version = certificate.get("VERSION", "")
    if not VERSION.fullmatch(version) or certificate.get("HOST") != config.get("BROWSER_HOST"):
        fail("Certificate identity does not match installation")
    files = set(BASE)
    files.update({f"instance/secrets/tls/{version}/certificate.pem", f"instance/secrets/tls/{version}/private.pem"})
    auth = instance / "secrets/auth"
    private_directory(auth)
    names = {item.name for item in auth.iterdir()}
    if not {"main.private.pem", "main.public.pem", "main.aes"} <= names or any(not AUTH_FILE.fullmatch(name) for name in names):
        fail("Authentication key directory is incomplete or unsafe")
    files.update(f"instance/secrets/auth/{name}" for name in names)
    for field, prefix in (("LOGO", "logo"), ("ORG_LOGO", "organization")):
        name = config.get(field, "")
        if name:
            if not re.fullmatch(rf"{prefix}\.(?:svg|png|jpg|webp)", name):
                fail("Invalid saved logo reference")
            files.add(f"instance/branding/logos/{name}")
    return config, files


def tree_files(root):
    private_directory(root)
    found = set()
    for parent, dirs, files in os.walk(root, followlinks=False):
        for name in dirs:
            private_directory(Path(parent) / name)
        for name in files:
            path = Path(parent) / name
            private_regular(path)
            found.add(path.relative_to(root).as_posix())
    return found


def read_manifest(root):
    path = root / "manifest.json"
    private_regular(path)
    if path.stat().st_size > 1024 * 1024:
        fail("Bundle metadata is too large")
    manifest = json.loads(path.read_text(), object_pairs_hook=pairs)
    if not isinstance(manifest, dict) or set(manifest) != {"format", "release", "state_schema", "postgres_major", "flyway", "payloads"}:
        fail("Unsupported bundle metadata")
    config, files = expected_files(root)
    if manifest["format"] != FORMAT or manifest["state_schema"] != 1 or manifest["postgres_major"] != POSTGRES_MAJOR or manifest["release"] != config["RELEASE"]:
        fail("Bundle release, state schema or PostgreSQL version is incompatible")
    migrations = manifest["flyway"]
    if not isinstance(migrations, list) or not migrations or any(not isinstance(row, str) or not re.fullmatch(r"[0-9]+\|-?[0-9]*\|t", row) for row in migrations):
        fail("Invalid or failed Flyway history")
    payloads = manifest["payloads"]
    if not isinstance(payloads, dict) or set(payloads) != files or tree_files(root) != files | {"manifest.json"}:
        fail("Bundle contents are incomplete or unexpected")
    for name, recorded in payloads.items():
        if not isinstance(recorded, dict) or set(recorded) != {"sha256", "size"} or not isinstance(recorded["sha256"], str) or not HASH.fullmatch(recorded["sha256"]) or type(recorded["size"]) is not int or recorded["size"] < 0:
            fail("Invalid bundle checksum metadata")
        if digest(root / name) != recorded:
            fail("Bundle integrity check failed")
    return manifest


def copy_private(source, target):
    owned_regular(source)
    target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with os.fdopen(os.open(source, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)), "rb") as src:
        with target.open("xb") as dst:
            shutil.copyfileobj(src, dst, 1024 * 1024)
    target.chmod(0o600)


def no_replace_rename(source, target):
    # Linux renameat2 makes publication atomic and refuses an existing final path.
    libc = ctypes.CDLL(None, use_errno=True)
    rename = getattr(libc, "renameat2", None)
    if rename is not None:
        rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
        result = rename(-100, os.fsencode(source), -100, os.fsencode(target), 1)
    else:
        number = {"x86_64": 316, "aarch64": 276}.get(os.uname().machine)
        if number is None or not hasattr(libc, "syscall"):
            fail("Atomic no-replace bundle publication needs Linux renameat2")
        syscall = libc.syscall
        syscall.argtypes = [ctypes.c_long, ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
        result = syscall(number, -100, os.fsencode(source), -100, os.fsencode(target), 1)
    if result:
        error = ctypes.get_errno()
        if error == errno.EEXIST:
            fail("Backup destination already exists; nothing was overwritten")
        raise OSError(error, os.strerror(error))


def pack(state, dump, flyway, output, source_root):
    if not output.is_absolute() or not output.parent.is_dir() or output.exists() or output.is_symlink():
        fail("Backup destination must be a new absolute directory under an existing parent")
    if output.parent.resolve() != output.parent or ".." in output.parts:
        fail("Backup destination parent must not contain symlinks or dot segments")
    destination = output.resolve(strict=False)
    for forbidden in (state.resolve(), source_root.resolve()):
        if os.path.commonpath((destination, forbidden)) == str(forbidden):
            fail("Backup destination cannot be inside installation state or source")
    source = tempfile.TemporaryDirectory(prefix=".kelios-backup-", dir=output.parent)
    try:
        stage = Path(source.name)
        stage.chmod(0o700)
        copy_private(dump, stage / "database.dump")
        # Read the allowlist from saved state, never from a caller-supplied archive path.
        mock = stage / "instance"
        copy_private(state / "installation.conf", mock / "installation.conf")
        copy_private(state / "certificate.conf", mock / "certificate.conf")
        config, files = expected_files_from_state(state, stage)
        for name in sorted(files - {"database.dump", "instance/installation.conf", "instance/certificate.conf"} - {f"instance/secrets/auth/{item.name}" for item in auth_files(state)}):
            copy_private(state / name.removeprefix("instance/"), stage / name)
        migrations = flyway.read_text().splitlines()
        if not migrations or any(not re.fullmatch(r"[0-9]+\|-?[0-9]*\|t", row) for row in migrations):
            fail("Source Flyway history is unavailable or failed")
        manifest = {"format": FORMAT, "release": config["RELEASE"], "state_schema": 1, "postgres_major": POSTGRES_MAJOR, "flyway": migrations, "payloads": {name: digest(stage / name) for name in sorted(files)}}
        with (stage / "manifest.json").open("x") as target:
            json.dump(manifest, target, sort_keys=True, separators=(",", ":"))
            target.write("\n")
        (stage / "manifest.json").chmod(0o600)
        read_manifest(stage)
        no_replace_rename(stage, output)
    finally:
        source.cleanup()


def expected_files_from_state(state, stage):
    # Use copied metadata, then enumerate the source key directory explicitly.
    auth = stage / "instance/secrets/auth"
    auth.mkdir(mode=0o700, parents=True)
    owned_directory(state / "secrets/auth")
    for item in auth_files(state):
        if not AUTH_FILE.fullmatch(item.name):
            fail("Authentication key directory contains an unsupported file")
        copy_private(item, auth / item.name)
    config, files = expected_files(stage)
    return config, files


def auth_files(state):
    return list((state / "secrets/auth").iterdir())


def validate_copy(bundle, stage):
    if not bundle.is_absolute():
        fail("Recovery bundle path must be absolute")
    manifest = read_manifest(bundle)
    if any(stage.iterdir()):
        fail("Private restore staging directory is not empty")
    for name in sorted(manifest["payloads"]):
        copy_private(bundle / name, stage / name)
    copy_private(bundle / "manifest.json", stage / "manifest.json")
    read_manifest(stage)


def flyway_equal(manifest_path, actual_path):
    manifest = json.loads(manifest_path.read_text(), object_pairs_hook=pairs)
    if actual_path.read_text().splitlines() != manifest["flyway"]:
        fail("Restored Flyway history differs from the bundle")


def promote(source, target):
    if not source.is_dir() or not target.is_absolute() or target.exists() or target.is_symlink():
        fail("Restore target already exists or staging is unavailable")
    no_replace_rename(source, target)


def main():
    try:
        os.umask(0o077)
        action, *args = sys.argv[1:]
        if action == "pack" and len(args) == 5:
            pack(*(Path(arg) for arg in args))
        elif action == "validate" and len(args) == 2:
            validate_copy(*(Path(arg) for arg in args))
        elif action == "flyway-equal" and len(args) == 2:
            flyway_equal(*(Path(arg) for arg in args))
        elif action == "promote" and len(args) == 2:
            promote(*(Path(arg) for arg in args))
        else:
            fail("Invalid recovery bundle helper invocation")
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"KELIOS backup/restore: {error}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
