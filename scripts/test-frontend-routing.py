#!/usr/bin/env python3
"""Read-only regression of the real static server, not Vite's development fallback.
Use --insecure-local only for supplied, untrusted local test certificates.
"""
import argparse
import re
import ssl
from urllib.parse import urlparse, urljoin
from urllib.request import build_opener, HTTPRedirectHandler, HTTPSHandler
from urllib.error import HTTPError

parser = argparse.ArgumentParser()
parser.add_argument('url')
parser.add_argument('--insecure-local', action='store_true')
args = parser.parse_args()
base = urlparse(args.url)
if base.scheme not in ('http', 'https') or base.hostname not in ('localhost', '127.0.0.1', '::1'):
    parser.error('This regression targets a disposable loopback deployment only.')
class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None
context = ssl._create_unverified_context() if args.insecure_local else ssl.create_default_context()
opener = build_opener(NoRedirect(), HTTPSHandler(context=context))
count = 0

def get(path, status=200, mime=None):
    global count
    try:
        response = opener.open(urljoin(args.url, path), timeout=15)
    except HTTPError as error:
        response = error
    with response:
        assert response.code == status, f'{path}: expected {status}, got {response.code}'
        assert 'Location' not in response.headers, f'{path}: unexpected redirect'
        if mime:
            assert mime in response.headers.get('Content-Type', ''), f'{path}: wrong MIME type'
        body = response.read().decode('utf-8')
    count += 1
    return body

routes = ['/', '/assets', '/categories', '/manufacturers', '/products', '/locations', '/owners', '/tenants', '/users', '/audit', '/oidc', '/inventory/assets/00000000-0000-0000-0000-000000000001']
index = get('/', mime='text/html')
for route in routes[1:]:
    assert get(route, mime='text/html') == index, f'{route}: not the SPA document'
assets = re.findall(r'(?:src|href)="(/assets/[^\"]+\.(?:js|css))"', index)
assert any(path.endswith('.js') for path in assets) and any(path.endswith('.css') for path in assets)
for path in assets:
    body = get(path, mime='css' if path.endswith('.css') else 'javascript')
    assert '<!doctype html>' not in body.lower(), f'{path}: static asset became SPA fallback'
get('/assets/does-not-exist.js', status=404)
print(f'{count} static-server checks passed; no application-route redirects; JS/CSS served; missing static file remains 404')
