#!/usr/bin/env python3
"""Fixed-layout recovery bundle safety controls; real PostgreSQL is tested separately."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("recovery_bundle", ROOT / "deploy/recovery_bundle.py")
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class BundleTests(unittest.TestCase):
    def setUp(self):
        os.umask(0o077)
        parent = ROOT / ".cache/verification/gate8"
        parent.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.state = self.root / "state"
        self.output = self.root / "complete"
        self.state.mkdir(mode=0o700)
        self.put("installation.conf", "SCHEMA=1\nMODE=managed\nPHASE=initialized\nRELEASE=0123456789ab\nBROWSER_HOST=localhost\nLOGO=logo.svg\nORG_LOGO=\n")
        self.put("certificate.conf", "HOST=localhost\nVERSION=abcdef012345\n")
        for name in ("secrets/db-password", "secrets/auth/main.private.pem", "secrets/auth/main.public.pem", "secrets/auth/main.aes", "secrets/auth/older.aes", "secrets/tls/abcdef012345/certificate.pem", "secrets/tls/abcdef012345/private.pem", "branding/logos/logo.svg"):
            self.put(name, name)
        self.dump = self.root / "database.dump"
        self.dump.write_bytes(b"disposable PostgreSQL custom dump fixture")
        self.dump.chmod(0o600)
        self.flyway = self.root / "flyway"
        self.flyway.write_text("1|123|t\n2|-456|t\n")
        self.flyway.chmod(0o600)

    def put(self, name, value):
        path = self.state / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
        path.chmod(0o600)

    def make_bundle(self):
        if not sys.platform.startswith("linux"):
            self.skipTest("Atomic renameat2 publication is a Linux gate")
        bundle.pack(self.state, self.dump, self.flyway, self.output, self.root / "source")
        return self.output

    def test_complete_bundle_preserves_all_key_ids_and_refuses_existing_output(self):
        output = self.make_bundle()
        manifest = bundle.read_manifest(output)
        self.assertIn("instance/secrets/auth/older.aes", manifest["payloads"])
        self.assertNotIn("instance/branding/config.json", manifest["payloads"])
        before = (output / "database.dump").read_bytes()
        with self.assertRaisesRegex(ValueError, "new absolute directory"):
            bundle.pack(self.state, self.dump, self.flyway, output, self.root / "source")
        self.assertEqual(before, (output / "database.dump").read_bytes())
        stage = self.root / "stage"
        stage.mkdir(mode=0o700)
        bundle.validate_copy(output, stage)
        self.assertEqual(bundle.read_manifest(stage), manifest)

    def test_corruption_extra_file_and_symlink_fail_before_stage_copy(self):
        output = self.make_bundle()
        stage = self.root / "stage"
        stage.mkdir(mode=0o700)
        (output / "database.dump").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "integrity"):
            bundle.validate_copy(output, stage)
        self.assertEqual(list(stage.iterdir()), [])
        (output / "database.dump").write_bytes(self.dump.read_bytes())
        (output / "unexpected").write_text("extra")
        with self.assertRaisesRegex(ValueError, "unexpected"):
            bundle.validate_copy(output, stage)
        (output / "unexpected").unlink()
        (output / "instance/secrets/auth/older.aes").unlink()
        (output / "instance/secrets/auth/older.aes").symlink_to(self.state / "secrets/auth/older.aes")
        with self.assertRaises(ValueError):
            bundle.validate_copy(output, stage)
        self.assertEqual(list(stage.iterdir()), [])

    def test_manifest_release_and_flyway_mismatch_fail(self):
        output = self.make_bundle()
        path = output / "manifest.json"
        metadata = json.loads(path.read_text())
        metadata["release"] = "deadbeefdead"
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "incompatible"):
            bundle.read_manifest(output)
        metadata["release"] = "0123456789ab"
        path.write_text(json.dumps(metadata))
        actual = self.root / "actual-flyway"
        actual.write_text("1|123|t\n")
        with self.assertRaisesRegex(ValueError, "differs"):
            bundle.flyway_equal(path, actual)


if __name__ == "__main__":
    unittest.main(verbosity=2)
