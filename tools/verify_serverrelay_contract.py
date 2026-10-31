#!/usr/bin/env python3
"""GAP-14: fail when an Android Retrofit route is absent from the supplied backend OpenAPI.

Usage: python tools/verify_serverrelay_contract.py --openapi /path/to/ServerRelay/docs/src/main/docs/api/openapi.yaml
Only validates HTTP verb + path parity. It does not assert auth, DTO field parity, or a deployed server.
"""
import argparse
import re
from pathlib import Path

METHODS = {"get", "put", "post", "delete", "patch"}
ROUTE = re.compile(r'@(?P<verb>GET|POST|PUT|DELETE|PATCH)\(\s*"(?P<path>/[^\"]+)"\s*\)')


def openapi_routes(raw: str) -> set[tuple[str, str]]:
    routes = set()
    in_paths = False
    current_path = None
    for line in raw.splitlines():
        if line == "paths:":
            in_paths = True
            continue
        if in_paths and line and not line[0].isspace() and not line.startswith('#'):
            break
        path = re.match(r'^  (/[^:]+):\s*$', line)
        if path:
            current_path = path.group(1)
            continue
        method = re.match(r'^    (get|post|put|patch|delete):\s*$', line)
        if current_path and method:
            routes.add((method.group(1).upper(), current_path))
    return routes


def retrofit_routes(source: Path) -> set[tuple[str, str]]:
    routes = set()
    for file in source.rglob('*.kt'):
        for match in ROUTE.finditer(file.read_text(encoding='utf-8')):
            routes.add((match['verb'], match['path']))
    return routes


def check(spec: Path, source: Path) -> tuple[set, set, set]:
    backend = openapi_routes(spec.read_text(encoding='utf-8'))
    mobile = retrofit_routes(source)
    if not backend or not mobile:
        raise ValueError('No OpenAPI paths or Retrofit declarations found; check inputs')
    return backend, mobile, mobile - backend


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--openapi', required=True, type=Path)
    ap.add_argument('--mobile', type=Path, default=Path(__file__).resolve().parents[1] /
                    'app/src/main/java/mme/corp/audioshare/data/api')
    args = ap.parse_args()
    backend, mobile, invalid = check(args.openapi, args.mobile)
    print(f'ServerRelay HTTP operations: {len(backend)}')
    print(f'SvReMobile Retrofit routes: {len(mobile)}')
    print(f'Contract route coverage: {len(mobile - invalid)}/{len(backend)}')
    if invalid:
        for method, path in sorted(invalid):
            print(f'NOT IN BACKEND OPENAPI: {method} {path}')
        raise SystemExit(1)
    print('PASS: every declared mobile route matches a ServerRelay HTTP operation')


if __name__ == '__main__':
    main()
