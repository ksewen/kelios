#!/usr/bin/env python3
"""Real disposable Docker verification. No production fixture/bypass or CLI dependency."""
import hashlib, http.cookiejar, importlib.util, json, os, pathlib, secrets, shutil, ssl, subprocess, urllib.request

ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE=ROOT/'.cache/deployment-cli'
BASE.mkdir(parents=True,exist_ok=True); BASE.chmod(0o700)
spec=importlib.util.spec_from_file_location('cli_control',ROOT/'scripts/test-deployment-cli.py')
control=importlib.util.module_from_spec(spec); spec.loader.exec_module(control)
RESULTS=[]

def check(name, condition):
    if not condition: raise AssertionError(name)
    RESULTS.append(name); print('PASS:',name,flush=True)

def run(args, cwd=ROOT, ok=True):
    p=subprocess.run(args,cwd=cwd,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if ok and p.returncode: raise AssertionError('Command failed: '+args[0]+' (output suppressed)')
    return p.stdout.strip()

def fixture(name):
    d=BASE/name
    if d.exists(): raise AssertionError('Disposable fixture already exists: '+name)
    d.mkdir()
    shutil.copy2(ROOT/'kelios',d/'kelios')
    shutil.copytree(ROOT/'deploy',d/'deploy')
    (d/'backend').symlink_to(ROOT/'backend',target_is_directory=True)
    (d/'frontend').symlink_to(ROOT/'frontend',target_is_directory=True)
    return d

def cli(d,command,answers=(),ok=True,timeout=900):
    rc,out=control.conversation(d,command,answers,timeout=timeout)
    if ok and rc: raise AssertionError('CLI failed: '+command+' (private transcript suppressed)')
    for secret in [PASSWORD, EXTERNAL_PASSWORD]:
        check_secret=secret not in out
        if not check_secret: raise AssertionError('Credential disclosed in CLI output')
    return rc,out

def config(d):
    return dict(line.split('=',1) for line in (d/'.kelios/installation.conf').read_text().splitlines())

def dc(d,*args):
    c=config(d); state=d/'.kelios'
    env=BASE/(d.name+'-verify.env')
    env.write_text(f"KELIOS_DB_PASSWORD_FILE={state}/secrets/db-password\nKELIOS_BIND_ADDRESS={c['BIND']}\nKELIOS_HTTP_PORT={c['HTTP_PORT']}\nKELIOS_HTTPS_PORT={c['HTTPS_PORT']}\nKELIOS_DATABASE_URL=jdbc:postgresql://{c['DB_HOST']}:{c['DB_PORT']}/{c['DB_NAME']}\nKELIOS_DATABASE_USER={c['DB_USER']}\nKELIOS_AUTH_KEY_DIRECTORY={state}/secrets/auth\nKELIOS_TLS_DIRECTORY={state}/secrets/tls\nKELIOS_AUTH_PUBLIC_ORIGIN=https://{c['BROWSER_HOST']}:{c['HTTPS_PORT']}\nKELIOS_AUTH_ISSUER={c['ISSUER']}\nKELIOS_BRANDING_DIRECTORY={state}/branding\nKELIOS_LOGO_DIRECTORY={state}/branding/logos\n")
    files=['-f',str(d/'deploy/compose.yaml')]
    if c['MODE']=='managed': files+=['-f',str(d/'deploy/compose.postgres.yaml')]
    files+=['-f',str(d/'deploy/compose.auth.yaml')]
    return run(['docker','compose','--env-file',str(env),'-p',c['PROJECT'],*files,*args],cwd=d)

def sql(d,query): return dc(d,'exec','-T','postgres','psql','-U','kelios','-d','kelios','-Atc',query)

def opener(port):
    jar=http.cookiejar.CookieJar()
    ctx=ssl.create_default_context(cafile=str(BASE/'tls-cert.pem'))
    return urllib.request.build_opener(urllib.request.HTTPSHandler(context=ctx),urllib.request.HTTPCookieProcessor(jar))

def request(client,port,path,data=None,token=None,csrf=None):
    headers={'Origin':f'https://localhost:{port}'}
    if token: headers['Authorization']='Bearer '+token
    if csrf: headers['X-CSRF-Token']=csrf
    if data is not None: headers['Content-Type']='application/json'
    req=urllib.request.Request(f'https://localhost:{port}/api/v1/{path}',data=None if data is None else json.dumps(data).encode(),headers=headers,method='PUT' if path.endswith('/role') else None)
    with client.open(req,timeout=20) as r: return json.load(r)

def login(port,user='cli-admin'):
    client=opener(port); proof=request(client,port,'auth/csrf')['token']
    result=request(client,port,'auth/local/login',{'login':user,'password':PASSWORD},csrf=proof)
    check('normal HTTPS login '+user,result['user']['scope']=='GLOBAL' and result['user']['status']=='ACTIVE')
    return client,result

def browser(port,brand):
    env=os.environ.copy()
    env.update(PATH='/opt/homebrew/opt/node@24/bin:'+env['PATH'], KELIOS_BASE_URL=f'https://localhost:{port}', KELIOS_TEST_TLS='true',KELIOS_TEST_LOGIN='cli-admin',KELIOS_TEST_PASSWORD_FILE=str(BASE/'password'),KELIOS_EXPECTED_BRAND=brand,PLAYWRIGHT_BROWSERS_PATH=str(ROOT/'.cache/playwright'))
    args=['npm','run','test:smoke','--','tests/runtime-branding.spec.ts']
    if brand=='green': args+=['tests/authentication.spec.ts','--grep','real HTTPS|real cookie|runtime|branding|logo|config|neutral|missing|malformed|timeout|theme']
    with (BASE/('browser-'+brand+'.log')).open('w') as out:
        p=subprocess.run(args,cwd=ROOT/'frontend',env=env,stdout=out,stderr=subprocess.STDOUT)
    check('actual HTTPS browser '+brand,p.returncode==0)

def install_answers(port,mode='managed',dbhost='host.docker.internal',dbport='12543'):
    answers=[('Listen address','127.0.0.1'),('HTTP technical/health port',str(port-1)),('HTTPS application port',str(port)),('Browser hostname','localhost'),('TLS certificate path',str(BASE/'tls-cert.pem')),('TLS private-key path',str(BASE/'tls-key.pem')),('Database mode',mode)]
    if mode=='external': answers += [('Database host',dbhost),('Database port',dbport),('Database name','kelios'),('Database username','kelios'),('Database password',EXTERNAL_PASSWORD)]
    answers += [('Runtime theme','green'),('Logo path',''),('Existing authentication key directory',str(MANAGED/'.kelios/secrets/auth') if mode=='external' else ''),('GLOBAL ADMIN login','cli-admin'),('Display name','CLI Verification Administrator'),('Administrator password',PASSWORD),('Confirm password',PASSWORD)]
    return answers

def recover_answers(user='cli-admin'):
    return [('GLOBAL ADMIN login',user),('Display name','CLI Verification Administrator'),('Administrator password',PASSWORD),('Confirm password',PASSWORD)]

PASSWORD='Aa9!'+secrets.token_hex(20)
EXTERNAL_PASSWORD=secrets.token_hex(32)
(BASE/'password').write_text(PASSWORD); (BASE/'password').chmod(0o600)
# Operator-supplied test fixture only: kelios itself NEVER issues certificates.
if not (BASE/'tls-cert.pem').exists():
    run(['openssl','req','-x509','-newkey','rsa:3072','-noenc','-keyout',str(BASE/'tls-key.pem'),'-out',str(BASE/'tls-cert.pem'),'-days','2','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'])
MANAGED=fixture('managed')
EXTERNAL=None
before=run(['docker','ps','-a','--format','{{.ID}}']).splitlines()
try:
    cli(MANAGED,'install',install_answers(12443))
    check('fresh managed V1-V6',sql(MANAGED,"SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success")=='1,2,3,4,5,6')
    check('no fixture Tenants or Assets',sql(MANAGED,'SELECT (SELECT count(*) FROM tenant)+(SELECT count(*) FROM asset)')=='0')
    check('one initial administrator',sql(MANAGED,"SELECT count(*) FROM app_user WHERE scope='GLOBAL' AND role='ADMIN' AND status='ACTIVE'")=='1')
    client,session=login(12443)
    check('minimal running status','Status: running' in cli(MANAGED,'status')[1])
    browser(12443,'green')
    keys={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (MANAGED/'.kelios/secrets/auth').iterdir()}
    check('repeat recovery refuses',cli(MANAGED,'recover-admin',recover_answers('refused-admin'),ok=False)[0]!=0)
    check('temporary password/env removed',not list((MANAGED/'.kelios/temporary').glob('recovery.*')) and not list((MANAGED/'.kelios/temporary').glob('compose.*')))
    # Valid zero-admin condition via normal administration: promote a second admin;
    # second disables first, then the first's still-valid short Access disables second.
    token=session['accessToken']
    user=request(client,12443,'users/global',{'login':'other-admin','displayName':'Other verification administrator','password':PASSWORD},token=token)
    request(client,12443,f"users/{user['id']}/role",{'role':'ADMIN'},token=token)
    second,other=login(12443,'other-admin')
    request(second,12443,f"users/{session['user']['id']}/disable",{},token=other['accessToken'])
    request(client,12443,f"users/{user['id']}/disable",{},token=token)
    check('zero usable admin via approved API actions',sql(MANAGED,"SELECT count(*) FROM app_user WHERE scope='GLOBAL' AND role='ADMIN' AND status='ACTIVE'")=='0')
    cli(MANAGED,'recover-admin',recover_answers('recovered-admin'))
    login(12443,'recovered-admin')
    check('bootstrap and recovery audited',sql(MANAGED,"SELECT count(*) FROM audit_log WHERE action='GLOBAL_ADMIN_BOOTSTRAPPED'")=='2')
    cli(MANAGED,'stop'); cli(MANAGED,'stop')
    check('stopped status','stopped-or-degraded' in cli(MANAGED,'status')[1])
    cli(MANAGED,'start'); cli(MANAGED,'start'); login(12443,'recovered-admin')
    frontend_before=dc(MANAGED,'images','-q','frontend')
    cli(MANAGED,'config',[('Choice','4'),('Theme','blue'),('Choice','6'),('Logo path',str(ROOT/'deploy/branding/blue/logo.svg')),('Choice','7')])
    cli(MANAGED,'start')
    check('theme/logo without image rebuild',frontend_before==dc(MANAGED,'images','-q','frontend') and request(opener(12443),12443,'status')['status']=='ready')
    browser(12443,'blue')
    cli(MANAGED,'config',[('Choice','2'),('HTTPS port','12444'),('Choice','3'),('Listen address','0.0.0.0'),('Choice','7')])
    cli(MANAGED,'start'); login(12444,'recovered-admin')
    check('port/bind applied with stable issuer',config(MANAGED)['ISSUER']=='https://localhost:12443' and 'https://localhost:12444' in cli(MANAGED,'status')[1])
    cli(MANAGED,'uninstall',[('preserving database/configuration/keys','yes'),('Permanently delete','no')])
    c=config(MANAGED)
    check('default uninstall volume preserved',bool(run(['docker','volume','inspect',c['PROJECT']+'_postgres-data','--format','{{.Name}}'])))
    cli(MANAGED,'start'); login(12444,'recovered-admin')
    check('keys preserved',keys=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (MANAGED/'.kelios/secrets/auth').iterdir()})
    cli(MANAGED,'uninstall',[('preserving database/configuration/keys','yes'),('Permanently delete','yes'),('Type DELETE','wrong')])
    check('wrong typed confirmation preserves volume',bool(run(['docker','volume','inspect',c['PROJECT']+'_postgres-data','--format','{{.Name}}'])))
    cli(MANAGED,'uninstall',[('preserving database/configuration/keys','yes'),('Permanently delete','yes'),('Type DELETE','DELETE '+c['PROJECT'])])
    check('exact typed confirmation deletes managed volume',subprocess.run(['docker','volume','inspect',c['PROJECT']+'_postgres-data'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode!=0)
    check('start refuses deleted installation',cli(MANAGED,'start',ok=False)[0]!=0)
    # Independent external disposable server and volume, never owned by kelios CLI.
    external_secret=BASE/'external-password'
    if external_secret.exists(): external_secret.chmod(0o600)
    external_secret.write_text(EXTERNAL_PASSWORD); external_secret.chmod(0o444)
    run(['docker','volume','create','kelios-cli-verification-external-data'])
    run(['docker','run','-d','--name','kelios-cli-verification-external','-p','127.0.0.1:12543:5432','-e','POSTGRES_DB=kelios','-e','POSTGRES_USER=kelios','-e','POSTGRES_PASSWORD_FILE=/run/secrets/db-password','--mount',f'type=bind,src={external_secret},dst=/run/secrets/db-password,readonly','--mount','type=volume,src=kelios-cli-verification-external-data,dst=/var/lib/postgresql/data','postgres:17-alpine'])
    shutil.copy2(MANAGED/'.kelios/secrets/auth/main.aes',MANAGED/'.kelios/secrets/auth/previous.aes')
    EXTERNAL=fixture('external')
    cli(EXTERNAL,'install',install_answers(12643,'external'))
    login(12643)
    check('supplied versioned encryption keys retained',(EXTERNAL/'.kelios/secrets/auth/previous.aes').read_bytes()==(MANAGED/'.kelios/secrets/auth/previous.aes').read_bytes())
    external_versions=run(['docker','exec','kelios-cli-verification-external','psql','-U','kelios','-d','kelios','-Atc',"SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success"])
    check('external fresh V1-V6',external_versions=='1,2,3,4,5,6')
    (ROOT/'docs/verification/deployment-cli/migrations.json').write_text(json.dumps({'managed':'1,2,3,4,5,6','external':external_versions})+'\n')
    check('external mode no managed service','postgres' not in dc(EXTERNAL,'config','--services').splitlines())
    aes_path=EXTERNAL/'.kelios/secrets/auth/main.aes'; aes_path.rename(aes_path.with_suffix('.held'))
    check('missing encryption key refuses start',cli(EXTERNAL,'start',ok=False)[0]!=0)
    aes_path.with_suffix('.held').rename(aes_path)
    check('external minimal status','Database: external PostgreSQL' in cli(EXTERNAL,'status')[1])
    cli(EXTERNAL,'uninstall',[('preserving database/configuration/keys','yes')])
    check('external uninstall preserves database rows',run(['docker','exec','kelios-cli-verification-external','psql','-U','kelios','-d','kelios','-Atc','SELECT count(*) FROM app_user'])=='1')
    cli(EXTERNAL,'start'); login(12643)
    cli(EXTERNAL,'stop')
    original=(EXTERNAL/'.kelios/secrets/db-password').read_bytes()
    (EXTERNAL/'.kelios/secrets/db-password').chmod(0o600); (EXTERNAL/'.kelios/secrets/db-password').write_text('wrong-verification-only-password')
    (EXTERNAL/'.kelios/secrets/db-password').chmod(0o444)
    check('bad external password fails safely',cli(EXTERNAL,'start',ok=False,timeout=230)[0]!=0)
    (EXTERNAL/'.kelios/secrets/db-password').chmod(0o600); (EXTERNAL/'.kelios/secrets/db-password').write_bytes(original); (EXTERNAL/'.kelios/secrets/db-password').chmod(0o444)
    cli(EXTERNAL,'stop')
    conf=EXTERNAL/'.kelios/installation.conf'; original_conf=conf.read_text()
    conf.write_text(original_conf.replace('DB_HOST=host.docker.internal','DB_HOST=unreachable.invalid'))
    check('unreachable external database fails safely',cli(EXTERNAL,'start',ok=False,timeout=230)[0]!=0)
    conf.write_text(original_conf)
    cli(EXTERNAL,'uninstall',[('preserving database/configuration/keys','yes')])
    check('unrelated containers untouched',all(x in run(['docker','ps','-a','--format','{{.ID}}']).splitlines() for x in before))
    result={'passed':len(RESULTS),'failed':0,'checks':RESULTS}
    (ROOT/'docs/verification/deployment-cli/results.json').write_text(json.dumps(result,indent=2)+'\n')
finally:
    # Only this verifier's exact disposable project identities; preserve state for evidence/debug.
    for d in [MANAGED,EXTERNAL]:
        if d is not None and (d/'.kelios/installation.conf').exists():
            try: dc(d,'down')
            except Exception: pass
    run(['docker','rm','-f','kelios-cli-verification-external'],ok=False)
    run(['docker','volume','rm','kelios-cli-verification-external-data'],ok=False)
