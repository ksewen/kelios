#!/usr/bin/env python3
"""TTY/control-flow tests. Python is test tooling, never an installer dependency."""
import hashlib, json, os, pathlib, pty, re, select, shutil, signal, subprocess, termios, time, unittest

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
python3 "$(dirname "$0")/capture.py" "$@" || exit $?
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
        # Observe argv without joining/evaluating it or changing fake Docker outcomes.
        (self.root / 'bin/capture.py').write_text("""import hashlib, json, pathlib, sys
root = pathlib.Path(__file__).resolve().parents[1]
argv = sys.argv[1:]
state = root / '.kelios/installation.conf'
phase = dict(line.split('=', 1) for line in state.read_text().splitlines()).get('PHASE') if state.exists() else None
record = {'argv': argv, 'phase': phase}
if any(arg.startswith('--kelios.recovery.password-file=') for arg in argv):
    mount = argv[argv.index('-v') + 1]
    source = pathlib.Path(mount.rsplit(':', 2)[0])
    record.update(password_sha256=hashlib.sha256(source.read_bytes()).hexdigest(), password_mode=source.stat().st_mode & 0o777)
with (root / 'docker-argv.jsonl').open('a') as out:
    out.write(json.dumps(record) + chr(10))
""")
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
    def managed_install_answers(self, email, password='ControlFixture9!NotReal'):
        prompts=self.tls_prompts()[:6]
        keys=self.root/'keys'; keys.mkdir()
        subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',str(keys/'main.private.pem')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        subprocess.run(['openssl','pkey','-in',str(keys/'main.private.pem'),'-pubout','-out',str(keys/'main.public.pem')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        aes=subprocess.check_output(['openssl','rand','-base64','32']); (keys/'main.aes').write_bytes(aes); (keys/'previous.aes').write_bytes(aes)
        prompts += [('Database mode','managed'),('Runtime theme',''),('Organization logo path',''),('Existing authentication key directory',str(keys)),('GLOBAL ADMIN Email',email),('Display name','Control fixture'),('Administrator password',password),('Confirm password',password)]
        return prompts,aes

    def docker_calls(self):
        return [json.loads(line) for line in (self.root/'docker-argv.jsonl').read_text().splitlines()]

    def assert_install_recovery(self, email, password, success):
        calls=self.docker_calls()
        recovery=[call for call in calls if '--kelios.recovery.enabled=true' in call['argv']]
        self.assertEqual(len(recovery),1)
        call=recovery[0]; args=call['argv']; prefix=args[:args.index('run')]
        state=dict(line.split('=',1) for line in (self.root/'.kelios/installation.conf').read_text().splitlines())
        self.assertEqual(prefix,['compose','--env-file',prefix[2],'-p',state['PROJECT'],'-f',str(self.root/'deploy/compose.yaml'),'-f',str(self.root/'deploy/compose.postgres.yaml'),'-f',str(self.root/'deploy/compose.auth.yaml')])
        self.assertTrue(pathlib.Path(prefix[2]).is_relative_to(self.root/'.kelios/temporary'))
        mount=args[args.index('-v')+1]; host=pathlib.Path(mount.rsplit(':',2)[0])
        self.assertEqual(host.name,'password'); self.assertTrue(host.parent.name.startswith('recovery.'))
        self.assertEqual(host.parent.parent,self.root/'.kelios/temporary')
        self.assertEqual(args[len(prefix):],['run','--rm','--no-deps','-v',str(host)+':/run/secrets/kelios-recovery/password:ro','backend','--spring.main.web-application-type=none','--kelios.recovery.enabled=true','--kelios.recovery.email='+email,'--kelios.recovery.display-name=Control fixture','--kelios.recovery.password-file=/run/secrets/kelios-recovery/password'])
        self.assertEqual(call['password_sha256'],hashlib.sha256(password.encode()).hexdigest())
        self.assertEqual(call['password_mode'],0o444)
        self.assertFalse(host.exists()); self.assertFalse(host.parent.exists())
        self.assertEqual(call['phase'],'prepared')
        phases=[item['phase'] for item in calls if 'up' in item['argv']]
        self.assertEqual(phases,['prepared','initialized'] if success else ['prepared'])
        events=[item['argv'][len(prefix)] for item in calls if item['argv'][:1]==['compose'] and any(arg in item['argv'] for arg in ['build','up','run'])]
        self.assertEqual(events,['build','up','run','up'] if success else ['build','up','run'])
        self.assertEqual(state['PHASE'],'initialized' if success else 'prepared')
        self.assertNotIn(email,(self.root/'.kelios/installation.conf').read_text())
        self.assertNotIn('EMAIL',state); self.assertNotIn('LOGIN',state)
        for persisted in (self.root/'.kelios').rglob('*'):
            if persisted.is_file():
                for spelling in {email,email.strip(),email.strip().lower()}:
                    self.assertNotIn(spelling.encode(),persisted.read_bytes())
        self.assertTrue(all(password not in arg for item in calls for arg in item['argv']))
        self.assertFalse(list((self.root/'.kelios/temporary').iterdir()))
        self.assertFalse((self.root/'.kelios.lock').exists())

    def test_supplied_versioned_keys_are_preserved(self):
        self.env.update(PASSWORD='inherited-placeholder',FIRST='inherited-placeholder',DB_PASSWORD='inherited-placeholder',encoded='inherited-placeholder')
        prompts,aes=self.managed_install_answers('control-admin@example.org')
        rc,out=self.run_cli('install',prompts)
        self.assertEqual(rc,0); self.assertTrue('ControlFixture9!NotReal' not in out)
        self.assertEqual((self.root/'.kelios/secrets/auth/previous.aes').read_bytes(),aes)
        self.assertEqual((self.root/'.kelios').stat().st_mode & 0o777,0o700)
        branding=json.loads((self.root/'.kelios/branding/config.json').read_text())
        self.assertEqual(branding['theme']['primary'],'#cd3a06')
        self.assertEqual(branding['theme']['accent'],'#fff0eb')
        self.assertEqual(branding['theme']['accentForeground'],'#8f2904')
        self.assertNotIn('logoUrl',branding)
        self.assertNotIn('organizationLogoUrl',branding)
        self.assertFalse(list((self.root/'.kelios/temporary').glob('recovery.*')))
        self.assert_install_recovery('control-admin@example.org','ControlFixture9!NotReal',True)
        self.assertIn('KELIOS installation ready:',out)

    def test_install_email_preserves_case_spaces_and_password_bytes(self):
        email='  Mixed.Case+tag@Example.org  '; password='  ControlFixture9!NotReal  '
        prompts,_=self.managed_install_answers(email,password)
        rc,out=self.run_cli('install',prompts)
        self.assertEqual(rc,0); self.assertNotIn(password,out)
        self.assertIn('GLOBAL ADMIN Email []:',out); self.assertNotIn('GLOBAL ADMIN login',out); self.assertNotIn('manual-admin',out)
        self.assert_install_recovery(email,password,True)
        self.assertIn('KELIOS installation ready:',out)
        self.assertLess(out.index('GLOBAL ADMIN created'),out.index('KELIOS installation ready:'))

    def test_install_email_punctuation_is_one_literal_argument(self):
        email="`touch${IFS}O1_EMAIL_EXECUTED`+tag$cash/o'brien@example.org"
        prompts,_=self.managed_install_answers(email)
        rc,out=self.run_cli('install',prompts)
        self.assertEqual(rc,0); self.assertNotIn('ControlFixture9!NotReal',out)
        self.assert_install_recovery(email,'ControlFixture9!NotReal',True)
        self.assertFalse((self.root/'O1_EMAIL_EXECUTED').exists())

    def assert_install_required_email(self,email):
        prompts,_=self.managed_install_answers(email)
        rc,out=self.run_cli('install',prompts[:-3])
        self.assertNotEqual(rc,0); self.assertIn('GLOBAL ADMIN Email []:',out)
        self.assertIn('GLOBAL ADMIN Email is required.',out)
        self.assertNotIn('Display name',out); self.assertNotIn('KELIOS installation ready:',out)
        calls=self.docker_calls(); self.assertFalse(any('run' in call['argv'] for call in calls))
        self.assertEqual([call['phase'] for call in calls if 'up' in call['argv']],['prepared'])
        self.assertIn('PHASE=prepared',(self.root/'.kelios/installation.conf').read_text())
        self.assertFalse(list((self.root/'.kelios/temporary').iterdir()))
        self.assertFalse((self.root/'.kelios.lock').exists())

    def test_install_email_empty_has_no_default(self):
        self.assert_install_required_email('')

    def test_install_email_posix_whitespace_rejected_before_dispatch(self):
        self.assert_install_required_email(' \t\v\f ')

    def test_install_refused_recovery_never_reports_ready(self):
        (self.root/'bin/refuse').touch()
        prompts,_=self.managed_install_answers('refused-admin@example.org')
        rc,out=self.run_cli('install',prompts)
        self.assertNotEqual(rc,0); self.assertIn('Administrator creation refused or failed.',out)
        self.assertNotIn('KELIOS installation ready:',out); self.assertNotIn('GLOBAL ADMIN created',out)
        self.assertNotIn('ControlFixture9!NotReal',out)
        self.assert_install_recovery('refused-admin@example.org','ControlFixture9!NotReal',False)

    def test_install_password_mismatch_never_dispatches_recovery(self):
        prompts,_=self.managed_install_answers('control-admin@example.org')
        prompts[-1]=('Confirm password','DifferentFixture9!NotReal')
        rc,out=self.run_cli('install',prompts)
        self.assertNotEqual(rc,0); self.assertIn('Passwords do not match.',out)
        self.assertNotIn('ControlFixture9!NotReal',out); self.assertNotIn('DifferentFixture9!NotReal',out)
        self.assertFalse(any('run' in call['argv'] for call in self.docker_calls()))
        self.assertIn('PHASE=prepared',(self.root/'.kelios/installation.conf').read_text())
        self.assertFalse(list((self.root/'.kelios/temporary').iterdir()))

    def initialized_recover_admin_fixture(self):
        # Reuse accepted fake install to obtain valid private keys/initialized state.
        self.test_supplied_versioned_keys_are_preserved()
        state=self.root/'.kelios'
        before={str(path.relative_to(state)):path.read_bytes() for path in state.rglob('*') if path.is_file()}
        return len(self.docker_calls()),before

    def assert_recover_admin_state(self, start, before, email, password=None):
        state=self.root/'.kelios'
        self.assertEqual({str(path.relative_to(state)):path.read_bytes() for path in state.rglob('*') if path.is_file()},before)
        self.assertIn('PHASE=initialized',(state/'installation.conf').read_text())
        self.assertFalse(list((state/'temporary').iterdir()))
        self.assertFalse((self.root/'.kelios.lock').exists())
        calls=self.docker_calls()[start:]
        self.assertEqual([call['phase'] for call in calls if 'up' in call['argv']],['initialized'])
        recoveries=[call for call in calls if '--kelios.recovery.enabled=true' in call['argv']]
        self.assertEqual(len(recoveries),int(password is not None))
        if password is not None:
            call=recoveries[0];args=call['argv'];prefix=args[:args.index('run')]
            configuration=dict(line.split('=',1) for line in (state/'installation.conf').read_text().splitlines())
            self.assertEqual(prefix,['compose','--env-file',prefix[2],'-p',configuration['PROJECT'],'-f',str(self.root/'deploy/compose.yaml'),'-f',str(self.root/'deploy/compose.postgres.yaml'),'-f',str(self.root/'deploy/compose.auth.yaml')])
            self.assertTrue(pathlib.Path(prefix[2]).is_relative_to(state/'temporary'))
            mount=args[args.index('-v')+1];source=pathlib.Path(mount.rsplit(':',2)[0])
            self.assertEqual(source.name,'password');self.assertTrue(source.parent.name.startswith('recovery.'))
            self.assertEqual(source.parent.parent,state/'temporary')
            self.assertEqual(args[len(prefix):],['run','--rm','--no-deps','-v',str(source)+':/run/secrets/kelios-recovery/password:ro','backend','--spring.main.web-application-type=none','--kelios.recovery.enabled=true','--kelios.recovery.email='+email,'--kelios.recovery.display-name=Recovery fixture','--kelios.recovery.password-file=/run/secrets/kelios-recovery/password'])
            self.assertEqual(call['phase'],'initialized')
            self.assertEqual(call['password_sha256'],hashlib.sha256(password.encode()).hexdigest())
            self.assertEqual(call['password_mode'],0o444)
            self.assertFalse(source.exists());self.assertFalse(source.parent.exists())
            self.assertTrue(all(password not in arg for item in calls for arg in item['argv']))
        self.assertFalse(any('build' in call['argv'] for call in calls))
        for path in state.rglob('*'):
            if path.is_file():
                for spelling in {email,email.strip(),email.strip().lower()}- {''}:
                    self.assertNotIn(spelling.encode(),path.read_bytes())

    def test_recover_admin_email_argv_and_initialized_privacy(self):
        start,before=self.initialized_recover_admin_fixture()
        email="  Recovery.Case+tag$cash/o'brien@Example.org  ";password='  RecoveryFixture9!NotReal  '
        answers=[('GLOBAL ADMIN Email',email),('Display name','Recovery fixture'),('Administrator password',password),('Confirm password',password)]
        rc,out=self.run_cli('recover-admin',answers)
        self.assertEqual(rc,0);self.assertIn('GLOBAL ADMIN Email []:',out)
        self.assertIn('GLOBAL ADMIN created through the protected backend recovery command.',out)
        self.assertNotIn('GLOBAL ADMIN login',out);self.assertNotIn('manual-admin',out);self.assertNotIn(password,out)
        self.assert_recover_admin_state(start,before,email,password)

    def test_recover_admin_refusal_is_sanitized_and_never_replayed(self):
        start,before=self.initialized_recover_admin_fixture()
        (self.root/'bin/refuse').touch()
        docker=self.root/'bin/docker'
        original='  *" run "*) [ ! -f "$(dirname "$0")/refuse" ]; exit $?;;'
        replacement='  *" run "*) printf "UNSAFE_BACKEND_DETAIL fixture-only\\n"; printf "UNSAFE_BACKEND_TRACE fixture-only\\n" >&2; [ ! -f "$(dirname "$0")/refuse" ]; exit $?;;'
        self.assertIn(original,docker.read_text());docker.write_text(docker.read_text().replace(original,replacement))
        email='refused-recovery@example.org';password='RecoveryFixture9!NotReal'
        answers=[('GLOBAL ADMIN Email',email),('Display name','Recovery fixture'),('Administrator password',password),('Confirm password',password)]
        rc,out=self.run_cli('recover-admin',answers)
        self.assertNotEqual(rc,0);self.assertIn('Administrator creation refused or failed.',out)
        self.assertNotIn('GLOBAL ADMIN created',out);self.assertNotIn('UNSAFE_BACKEND_DETAIL',out);self.assertNotIn('UNSAFE_BACKEND_TRACE',out);self.assertNotIn(password,out)
        self.assert_recover_admin_state(start,before,email,password)

    def assert_recover_admin_required_email(self,email):
        start,before=self.initialized_recover_admin_fixture()
        rc,out=self.run_cli('recover-admin',[('GLOBAL ADMIN Email',email)])
        self.assertNotEqual(rc,0);self.assertIn('GLOBAL ADMIN Email []:',out)
        self.assertIn('GLOBAL ADMIN Email is required.',out);self.assertNotIn('Display name',out);self.assertNotIn('GLOBAL ADMIN created',out)
        self.assert_recover_admin_state(start,before,email)

    def test_recover_admin_empty_email_has_no_default(self):
        self.assert_recover_admin_required_email('')

    def test_recover_admin_whitespace_email_never_dispatches(self):
        self.assert_recover_admin_required_email(' \t\v\f ')

    def test_recover_admin_password_mismatch_preserves_initialized_state(self):
        start,before=self.initialized_recover_admin_fixture()
        email='recovery@example.org';password='RecoveryFixture9!NotReal';different='DifferentFixture9!NotReal'
        answers=[('GLOBAL ADMIN Email',email),('Display name','Recovery fixture'),('Administrator password',password),('Confirm password',different)]
        rc,out=self.run_cli('recover-admin',answers)
        self.assertNotEqual(rc,0);self.assertIn('Passwords do not match.',out)
        self.assertNotIn(password,out);self.assertNotIn(different,out);self.assertNotIn('GLOBAL ADMIN created',out)
        self.assert_recover_admin_state(start,before,email)

    def test_recover_admin_password_interrupt_restores_echo_and_state(self):
        start,before=self.initialized_recover_admin_fixture()
        email='recovery@example.org'
        rc,out=self.run_cli('recover-admin',[('GLOBAL ADMIN Email',email),('Display name','Recovery fixture'),('Administrator password',None)])
        self.assertEqual(rc,130);self.assertNotIn('GLOBAL ADMIN created',out)
        self.assert_recover_admin_state(start,before,email)

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
    def test_legacy_logo_survives_start_and_new_organization_logo_can_be_added(self):
        self.test_supplied_versioned_keys_are_preserved()
        d=self.root/'.kelios'
        saved='\n'.join('LOGO=logo.svg' if line=='LOGO=' else 'PRIMARY=#087f5b' if line=='PRIMARY=#cd3a06' else line for line in (d/'installation.conf').read_text().splitlines() if not line.startswith('ORG_LOGO='))+'\n'
        (d/'installation.conf').write_text(saved)
        shutil.copy2(ROOT/'deploy/branding/green/logo.svg',d/'branding/logos/logo.svg')
        original=(d/'installation.conf').read_bytes()
        self.assertEqual(self.run_cli('start')[0],0)
        self.assertEqual(original,(d/'installation.conf').read_bytes())
        self.assertEqual(json.loads((d/'branding/config.json').read_text())['logoUrl'],'/runtime/logos/logo.svg')
        source=self.root/'organization.svg'; source.write_text('<svg xmlns="http://www.w3.org/2000/svg"/>')
        rc,_=self.run_cli('config',[('Choice','7'),('Organization logo path',str(source)),('Choice','8')]); self.assertEqual(rc,0)
        self.assertTrue((d/'branding/logos/organization.svg').exists())
        self.assertEqual(self.run_cli('start')[0],0)
        branding=json.loads((d/'branding/config.json').read_text())
        self.assertEqual(branding['logoUrl'],'/runtime/logos/logo.svg')
        self.assertEqual(branding['organizationLogoUrl'],'/runtime/logos/organization.svg')
        self.assertEqual(branding['theme']['primary'],'#087f5b')
        rc,_=self.run_cli('config',[('Choice','7'),('Organization logo path',''),('Choice','8')]); self.assertEqual(rc,0)
        self.assertEqual(self.run_cli('start')[0],0)
        self.assertNotIn('organizationLogoUrl',json.loads((d/'branding/config.json').read_text()))
        rc,_=self.run_cli('config',[('Choice','6'),('Legacy logo path',''),('Choice','8')]); self.assertEqual(rc,0)
        self.assertEqual(self.run_cli('start')[0],0)
        branding=json.loads((d/'branding/config.json').read_text())
        self.assertNotIn('logoUrl',branding)
        self.assertEqual(branding['theme']['primary'],'#087f5b')
        self.assertTrue((d/'branding/logos/logo.svg').exists())
    def test_missing_organization_logo_refuses_start(self):
        self.state(ORG_LOGO='organization.svg')
        rc,out=self.run_cli('start')
        self.assertNotEqual(rc,0)
        self.assertIn('Required logo file missing or unsafe',out)
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
