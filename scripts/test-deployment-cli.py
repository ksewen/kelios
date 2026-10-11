#!/usr/bin/env python3
"""TTY/control-flow tests. Python is test tooling, never an installer dependency."""
import os, pathlib, pty, re, select, shutil, signal, subprocess, termios, time, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = ROOT / '.cache/deployment-cli/control'


def conversation(directory, command, answers, env=None, timeout=15):
    pid, fd = pty.fork()
    if pid == 0:
        os.chdir(directory)
        os.execve(str(directory / 'kelios'), [str(directory / 'kelios'), command], env or os.environ.copy())
    transcript = b''
    cursor = 0
    deadline = time.monotonic() + timeout
    try:
        for pattern, value in answers:
            regex = re.compile(pattern.encode())
            while True:
                match = regex.search(transcript, cursor)
                if match:
                    cursor = match.end()
                    # CLI has disabled terminal echo before receiving secret input.
                    if value is None:
                        os.kill(pid, signal.SIGINT)
                    else:
                        os.write(fd, value.encode() + b'\n')
                    break
                if time.monotonic() > deadline:
                    raise AssertionError('Prompt timeout (transcript intentionally not printed)')
                if select.select([fd], [], [], .05)[0]:
                    try:
                        chunk = os.read(fd, 65536)
                    except OSError:
                        chunk = b''
                    if not chunk:
                        safe=transcript.decode(errors='replace')
                        for label, supplied in answers:
                            if 'password' in label.lower() and supplied:
                                safe=safe.replace(supplied,'[REDACTED]')
                        (ROOT/'.cache/deployment-cli/last-error.txt').write_text(safe)
                        raise AssertionError('CLI exited before expected prompt (redacted diagnostic saved)')
                    transcript += chunk
        while time.monotonic() < deadline:
            if select.select([fd], [], [], .05)[0]:
                try:
                    chunk = os.read(fd, 65536)
                except OSError:
                    chunk = b''
                if not chunk:
                    _, status = os.waitpid(pid, 0)
                    if answers and answers[-1][1] is None:
                        assert termios.tcgetattr(fd)[3] & termios.ECHO, 'Terminal echo was not restored'
                    return os.waitstatus_to_exitcode(status), transcript.decode(errors='replace')
                transcript += chunk
        raise AssertionError('CLI timeout')
    finally:
        try:
            os.kill(pid, 15)
        except ProcessLookupError:
            pass
        os.close(fd)


class CliControlTests(unittest.TestCase):
    def setUp(self):
        self.root = BASE / self._testMethodName
        self.root.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / 'kelios', self.root / 'kelios')
        (self.root / 'bin').mkdir(exist_ok=True)
        docker = self.root / 'bin/docker'
        docker.write_text('''#!/bin/sh
if [ -n "${PASSWORD+x}${FIRST+x}${DB_PASSWORD+x}${encoded+x}" ]; then exit 42; fi
case "$*" in
  *"volume inspect"*) if [ -f "$(dirname "$0")/missing-volume" ]; then exit 1; fi;;
esac
case "$*" in
  info|"compose version") exit 0;;
  *"config --images"*) echo kelios-backend:phase0; echo kelios-frontend:phase0; echo postgres:17-alpine; exit 0;;
  *"image inspect"*) [ ! -f "$(dirname "$0")/missing-image" ]; exit $?;;
  *"volume inspect"*"com.docker.compose.project"*) awk -F= '$1=="PROJECT" {print $2}' .kelios/installation.conf; exit 0;;
  *"volume inspect"*"com.docker.compose.volume"*) echo postgres-data; exit 0;;
  *" run "*) [ ! -f "$(dirname "$0")/refuse" ]; exit $?;;
  *) exit 0;;
esac
''')
        docker.chmod(0o755)
        self.env = os.environ.copy()
        self.env['PATH'] = str(self.root / 'bin') + ':' + self.env['PATH']

    def run_cli(self, command, answers=()):
        return conversation(self.root, command, answers, self.env)

    def state(self, **changes):
        values = dict(SCHEMA='1', PROJECT='kelios-local-control', MODE='managed', PHASE='initialized', BIND='127.0.0.1', HTTP_PORT='8080', HTTPS_PORT='8443', BROWSER_HOST='localhost', ISSUER='https://localhost:8443', DB_HOST='postgres', DB_PORT='5432', DB_NAME='kelios', DB_USER='kelios', BACKGROUND='#f2faf5', FOREGROUND='#16372b', PRIMARY='#087f5b', PRIMARY_FOREGROUND='#ffffff', ACCENT='#d8f2e4', ACCENT_FOREGROUND='#14543c', LOGO='logo.svg')
        values.update(changes)
        d = self.root / '.kelios'
        d.mkdir(exist_ok=True)
        for sub in ['temporary', 'secrets/auth', 'secrets/tls', 'branding/logos']:
            (d / sub).mkdir(parents=True, exist_ok=True)
        (d / 'installation.conf').write_text(''.join(f'{k}={v}\n' for k,v in values.items()))
        for f in ['secrets/db-password', 'secrets/auth/main.private.pem', 'secrets/auth/main.public.pem', 'secrets/auth/main.aes', 'secrets/tls/certificate.pem', 'secrets/tls/private.pem', 'branding/config.json', 'branding/logos/logo.svg']:
            (d / f).write_text('fixture-not-a-secret')
        return d

    def test_unknown_command(self):
        rc, out = self.run_cli('restart'); self.assertNotEqual(rc,0); self.assertIn('Usage:',out)
    def test_absent_status(self):
        rc, out = self.run_cli('status'); self.assertEqual(rc,0); self.assertIn('not initialized',out)
    def test_start_without_install(self):
        rc, out = self.run_cli('start'); self.assertNotEqual(rc,0); self.assertIn('not initialized',out)
    def test_install_refuses_existing(self):
        self.state(); rc,out = self.run_cli('install'); self.assertNotEqual(rc,0); self.assertIn('already exists',out)
    def test_config_not_executed(self):
        d=self.state(PROJECT='$(touch forbidden)'); rc,_=self.run_cli('stop'); self.assertNotEqual(rc,0); self.assertFalse((self.root/'forbidden').exists())
    def test_unknown_saved_field_rejected(self):
        d=self.state(); (d/'installation.conf').write_text((d/'installation.conf').read_text()+'EVIL=1\n'); self.assertNotEqual(self.run_cli('stop')[0],0)
    def test_duplicate_saved_field_rejected(self):
        d=self.state(); (d/'installation.conf').write_text((d/'installation.conf').read_text()+'SCHEMA=1\n'); self.assertNotEqual(self.run_cli('stop')[0],0)
    def test_symlink_state_rejected(self):
        (self.root/'.kelios').symlink_to(self.root/'bin',target_is_directory=True); self.assertNotEqual(self.run_cli('status')[0],0)
    def test_abandoned_lock_rejected(self):
        (self.root/'.kelios.lock').mkdir(); self.assertNotEqual(self.run_cli('stop')[0],0)
    def test_invalid_port_rejected(self):
        self.assertNotEqual(self.run_cli('install', [('Listen address', '127.0.0.1'), ('HTTP technical/health port', '0')])[0],0)
    def test_invalid_bind_rejected(self):
        self.assertNotEqual(self.run_cli('install',[('Listen address','999.1.1.1')])[0],0)
    def test_invalid_ipv6_rejected(self):
        self.assertNotEqual(self.run_cli('install',[('Listen address',':::')])[0],0)
    def test_missing_tls_before_state(self):
        rc,out=self.run_cli('install',[('Listen address',''),('HTTP technical/health port',''),('HTTPS application port',''),('Browser hostname',''),('TLS certificate path','/missing'),('TLS private-key path','/missing')]); self.assertNotEqual(rc,0); self.assertFalse((self.root/'.kelios').exists())
    def tls_prompts(self):
        cert=self.root/'certificate.pem'; key=self.root/'private.pem'
        subprocess.run(['openssl','req','-x509','-newkey','rsa:3072','-noenc','-keyout',str(key),'-out',str(cert),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        return [('Listen address',''),('HTTP technical/health port',''),('HTTPS application port',''),('Browser hostname',''),('TLS certificate path',str(cert)),('TLS private-key path',str(key)),('Database mode','external'),('Database host','host.docker.internal'),('Database port',''),('Database name',''),('Database username','')]
    def test_interrupt_password_restores_echo(self):
        rc,_=self.run_cli('install',self.tls_prompts()+[('Database password',None)])
        self.assertEqual(rc,130); self.assertFalse((self.root/'.kelios').exists())
    def test_password_not_echoed(self):
        secret='UnprintedVerification9!Only'
        rc,out=self.run_cli('install',self.tls_prompts()+[('Database password',secret),('Runtime theme','invalid')])
        self.assertNotEqual(rc,0); self.assertNotIn(secret,out)
    def test_supplied_versioned_keys_are_preserved(self):
        self.env.update(PASSWORD='inherited-placeholder',FIRST='inherited-placeholder',DB_PASSWORD='inherited-placeholder',encoded='inherited-placeholder')
        prompts=self.tls_prompts()[:6]
        keys=self.root/'keys'; keys.mkdir()
        subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',str(keys/'main.private.pem')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        subprocess.run(['openssl','pkey','-in',str(keys/'main.private.pem'),'-pubout','-out',str(keys/'main.public.pem')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        aes=subprocess.check_output(['openssl','rand','-base64','32']); (keys/'main.aes').write_bytes(aes); (keys/'previous.aes').write_bytes(aes)
        prompts += [('Database mode','managed'),('Runtime theme','green'),('Logo path',str(ROOT/'deploy/branding/green/logo.svg')),('Existing authentication key directory',str(keys)),('GLOBAL ADMIN login','control-admin'),('Display name','Control fixture'),('Administrator password','ControlFixture9!NotReal'),('Confirm password','ControlFixture9!NotReal')]
        rc,out=self.run_cli('install',prompts)
        self.assertEqual(rc,0); self.assertTrue('ControlFixture9!NotReal' not in out)
        self.assertEqual((self.root/'.kelios/secrets/auth/previous.aes').read_bytes(),aes)
        self.assertEqual((self.root/'.kelios').stat().st_mode & 0o777,0o700)
        self.assertFalse(list((self.root/'.kelios/temporary').glob('recovery.*')))
    def test_initialized_missing_volume_refuses_empty_replacement(self):
        self.test_supplied_versioned_keys_are_preserved()
        (self.root/'bin/missing-volume').touch()
        rc,out=self.run_cli('start')
        self.assertNotEqual(rc,0); self.assertIn('database volume is missing',out)
    def test_start_missing_image_does_not_pull(self):
        self.test_supplied_versioned_keys_are_preserved()
        (self.root/'bin/missing-image').touch()
        rc,out=self.run_cli('start')
        self.assertNotEqual(rc,0); self.assertIn('required image is missing',out)
    def test_nested_symlink_rejected(self):
        d=self.state(); (d/'secrets/link').symlink_to(self.root/'bin',target_is_directory=True)
        self.assertNotEqual(self.run_cli('stop')[0],0)
    def test_config_cancel_preserves_manifest(self):
        d=self.state(); before=(d/'installation.conf').read_bytes(); rc,_=self.run_cli('config',[('Choice','2'),('HTTPS port','9443'),('Choice','0')]); self.assertEqual(rc,0); self.assertEqual(before,(d/'installation.conf').read_bytes())
    def test_default_uninstall_preserves_files(self):
        d=self.state(); rc,_=self.run_cli('uninstall',[('preserving database/configuration/keys','yes'),('Permanently delete','no')]); self.assertEqual(rc,0); self.assertTrue((d/'secrets/auth/main.aes').exists()); self.assertIn('initialized',(d/'installation.conf').read_text())
    def test_wrong_destructive_confirmation_preserves_state(self):
        d=self.state(); rc,out=self.run_cli('uninstall',[('preserving database/configuration/keys','yes'),('Permanently delete','yes'),('Type DELETE','wrong')]); self.assertEqual(rc,0); self.assertIn('preserved',out); self.assertIn('initialized',(d/'installation.conf').read_text())
    def test_external_uninstall_never_offers_deletion(self):
        self.state(MODE='external'); rc,out=self.run_cli('uninstall',[('preserving database/configuration/keys','yes')]); self.assertEqual(rc,0); self.assertNotIn('Permanently delete',out)

if __name__ == '__main__':
    if BASE.exists():
        shutil.rmtree(BASE)  # This test's dedicated disposable fixture tree only.
    unittest.main(verbosity=2)
