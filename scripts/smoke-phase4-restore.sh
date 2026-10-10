#!/bin/sh
# Operator-style drill ONLY against the isolated Phase 4 disposable Compose project.
set -eu
cd "$(dirname "$0")/.."
test "${COMPOSE_PROJECT_NAME:-}" = kelios-phase4
test -n "${KELIOS_AUTH_KEY_DIRECTORY:-}"
test -s "$KELIOS_AUTH_KEY_DIRECTORY/main.aes"
test -s "$KELIOS_AUTH_KEY_DIRECTORY/main.private.pem"
test -s "$KELIOS_AUTH_KEY_DIRECTORY/main.public.pem"
mkdir -p .cache/phase4-private docs/verification/phase4
chmod 700 .cache/phase4-private
compose() { docker compose -f deploy/compose.yaml -f deploy/compose.auth.yaml "$@"; }
# Seed an encrypted disposable provider through the real authorized API, without provider network access.
python3 - <<'PYSEED'
import os,json,ssl,urllib.request,http.cookiejar,secrets,uuid
origin=os.environ['KELIOS_AUTH_PUBLIC_ORIGIN'];client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),urllib.request.HTTPSHandler(context=ssl._create_unverified_context()))
csrf=json.load(client.open(origin+'/api/v1/auth/csrf'))['token']
request=urllib.request.Request(origin+'/api/v1/auth/local/login',data=json.dumps({'login':os.environ['KELIOS_TEST_LOGIN'],'password':open(os.environ['KELIOS_TEST_PASSWORD_FILE']).read()}).encode(),headers={'Content-Type':'application/json','Origin':origin,'X-CSRF-Token':csrf})
token=json.load(client.open(request))['accessToken'];headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'}
tenant=json.load(client.open(urllib.request.Request(origin+'/api/v1/tenants',data=json.dumps({'name':'Disposable restoration provider'}).encode(),headers=headers)))
body={'issuer':'https://restore-fixture.example','clientId':'restore-client','clientSecret':secrets.token_urlsafe(32),'enabled':False,'version':None,'allowedGroupIds':[str(uuid.uuid4())]}
client.open(urllib.request.Request(origin+'/api/v1/tenants/'+tenant+'/oidc',method='PUT',data=json.dumps(body).encode(),headers=headers)).read()
with open('.cache/phase4-private/provider-tenant','w') as f:f.write(tenant)
PYSEED
# Dump is secret-bearing material; it stays private/ignored, never in evidence.
umask 077
compose exec -T postgres pg_dump -U kelios -d kelios -Fc > .cache/phase4-private/database.dump
restored="phase4_restore_$(date +%s)"
compose exec -T postgres createdb -U kelios "$restored"
compose exec -T postgres pg_restore -U kelios -d "$restored" --no-owner < .cache/phase4-private/database.dump
# Hash exact row contents without publishing credentials/provider/user payloads.
cat > .cache/phase4-private/digests.sql <<'SQL'
SELECT 'tenant',count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM tenant t;
SQL
for table in app_user asset asset_event category manufacturer product location owner audit_log asset_tag_counter local_global_credential tenant_oidc_config tenant_oidc_allowed_group external_identity global_admin_recovery_guard flyway_schema_history; do
  printf "SELECT '%s',count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM %s t;\n" "$table" "$table" >> .cache/phase4-private/digests.sql
done
compose exec -T postgres psql -U kelios -d kelios -At < .cache/phase4-private/digests.sql > docs/verification/phase4/restore-source-digests.txt
compose exec -T postgres psql -U kelios -d "$restored" -At < .cache/phase4-private/digests.sql > docs/verification/phase4/restore-restored-digests.txt
cmp docs/verification/phase4/restore-source-digests.txt docs/verification/phase4/restore-restored-digests.txt
printf 'services:\n  backend:\n    environment:\n      KELIOS_DATABASE_URL: jdbc:postgresql://postgres:5432/%s\n' "$restored" > .cache/phase4-private/restore.compose.yaml
# Switch only disposable runtime to the restored database; original database is untouched.
docker compose -f deploy/compose.yaml -f deploy/compose.auth.yaml -f .cache/phase4-private/restore.compose.yaml up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 backend
compose exec -T backend sh -ec 'test -s /run/secrets/kelios-auth/main.aes; test -s /run/secrets/kelios-auth/main.private.pem; wget -q -O - http://127.0.0.1:8081/actuator/health/readiness' > docs/verification/phase4/restore-readiness.txt
# Real HTTPS login against restored credentials, keeping CSRF/tokens/password entirely in memory.
KELIOS_RESTORE_DATABASE="$restored" python3 - <<'PY'
import os,json,ssl,urllib.request,http.cookiejar
origin=os.environ['KELIOS_AUTH_PUBLIC_ORIGIN'];cookies=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies),urllib.request.HTTPSHandler(context=ssl._create_unverified_context()))
csrf=json.load(client.open(origin+'/api/v1/auth/csrf'))['token']
password=open(os.environ['KELIOS_TEST_PASSWORD_FILE']).read()
r=client.open(urllib.request.Request(origin+'/api/v1/auth/local/login',data=json.dumps({'login':os.environ['KELIOS_TEST_LOGIN'],'password':password}).encode(),headers={'Content-Type':'application/json','Origin':origin,'X-CSRF-Token':csrf}))
session=json.load(r);assert session['user']['scope']=='GLOBAL';assert session['user']['status']=='ACTIVE'
headers={'Authorization':'Bearer '+session['accessToken']}
assets=json.load(client.open(urllib.request.Request(origin+'/api/v1/assets?size=100',headers=headers)))
assert assets['totalElements']>0
assert any(a.get('localIdentifier') for a in assets['items'])
tenant=open('.cache/phase4-private/provider-tenant').read()
config=json.load(client.open(urllib.request.Request(origin+'/api/v1/tenants/'+tenant+'/oidc',headers=headers)))
assert config['secretConfigured'] is True
body={k:config[k] for k in ['issuer','clientId','enabled','version','allowedGroupIds']};body['clientSecret']=None
# Existing secret is decrypted with the externally mounted AES key and re-encrypted by the production service.
result=json.load(client.open(urllib.request.Request(origin+'/api/v1/tenants/'+tenant+'/oidc',method='PUT',data=json.dumps(body).encode(),headers={**headers,'Content-Type':'application/json'})))
assert result['version']==config['version']+1 and result['secretConfigured'] is True
with open('docs/verification/phase4/restore-authentication.txt','w') as f:f.write('Restored HTTPS local GLOBAL login: passed\nRestored scoped Asset reads and localIdentifier: passed\nExternal RSA/AES keys available; restored OIDC secret decrypted/re-encrypted by production configuration service: passed\nExact source/restored all-table digests: passed\nOIDC envelope decryption and missing-key fail-closed separately verified by real PostgreSQL Phase4MetadataIT\n')
PY
# Restore original disposable runtime selection; do not drop either database or volume.
compose up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 backend
printf 'Disposable PostgreSQL backup/restore/readiness/HTTPS-authentication drill passed.\n'
