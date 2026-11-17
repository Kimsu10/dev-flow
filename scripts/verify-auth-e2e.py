"""Run real browser/session/Agent QA against a new, disposable local PostgreSQL database.
Requires built backend JAR, npm ci, native psql, and Chromium. Never prints credentials.
"""
import os
import re
import secrets
import socket
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from agent.config import load_env, database_settings

def port():
    for _ in range(100):
        with socket.socket() as s:
            try:
                s.bind(('127.0.0.1', secrets.randbelow(10000) + 18000))
                return s.getsockname()[1]
            except OSError:
                continue
    raise RuntimeError('No local QA port available')

def wait(url, process):
    for _ in range(150):
        if process.poll() is not None:
            raise RuntimeError('QA server exited; inspect local QA log')
        try:
            with urllib.request.urlopen(url, timeout=1) as r:
                if r.status == 200:
                    return
        except Exception:
            time.sleep(.3)
    raise RuntimeError('QA server startup timed out')

def main():
    load_env()
    settings = database_settings()
    if settings['host'] not in ('localhost', '127.0.0.1', '::1'):
        raise RuntimeError('QA is restricted to local PostgreSQL')
    psql = os.environ.get('PSQL_EXECUTABLE', 'C:/Program Files/PostgreSQL/17/bin/psql.exe')
    name = 'auth_qa_' + secrets.token_hex(8)
    if not re.fullmatch(r'auth_qa_[0-9a-f]{16}', name):
        raise RuntimeError('Unsafe QA database name')
    env = os.environ.copy()
    env['PGPASSWORD'] = settings['password']
    def sql(statement):
        r = subprocess.run([psql, '-h', settings['host'], '-p', str(settings['port']), '-U', settings['user'],
                            '-d', settings['dbname'], '-X', '-v', 'ON_ERROR_STOP=1', '-c', statement],
                           env=env, capture_output=True)
        if r.returncode:
            raise RuntimeError('QA database operation failed (details suppressed to protect credentials)')
    processes = []
    logs = []
    sql('CREATE DATABASE "' + name + '"')
    try:
        backend_port, frontend_port = port(), port()
        while frontend_port == backend_port:
            frontend_port = port()
        env.update(DB_URL=f"jdbc:postgresql://{settings['host']}:{settings['port']}/{name}",
                   DB_USERNAME=settings['user'], DB_PASSWORD=settings['password'],
                   AUTH_ADMIN_USERNAME='qa-owner', AUTH_ADMIN_EMAIL='qa-owner@example.test',
                   AUTH_ADMIN_PASSWORD=secrets.token_urlsafe(24), SESSION_COOKIE_SECURE='false',
                   SERVER_PORT=str(backend_port), FORWARD_HEADERS_STRATEGY='NONE',
                   API_PROXY_TARGET=f'http://127.0.0.1:{backend_port}', AUTH_TEST_BASE=f'http://127.0.0.1:{frontend_port}')
        output = ROOT / 'artifacts' / 'auth-e2e' / name
        output.mkdir(parents=True)
        def start(args, cwd, label):
            log = (output / (label + '.log')).open('wb')
            logs.append(log)
            process = subprocess.Popen(args, cwd=cwd, env=env, stdout=log, stderr=log,
                                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            processes.append(process)
            return process
        jars = [p for p in (ROOT / 'backend/build/libs').glob('*.jar') if not p.name.endswith('-plain.jar')]
        backend = start(['java', '-jar', str(jars[0])], ROOT, 'backend')
        wait(f'http://127.0.0.1:{backend_port}/api/health', backend)
        frontend = start(['node', 'node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', str(frontend_port), '--strictPort'], ROOT / 'frontend', 'frontend')
        wait(env['AUTH_TEST_BASE'], frontend)
        result = subprocess.run(['node', str(ROOT / 'scripts/verify-auth-ui.mjs')], cwd=ROOT, env=env, capture_output=True, text=True, encoding='utf-8')
        if result.returncode:
            # CDP failure messages contain only test conditions, never submitted credentials.
            print(result.stderr)
            raise RuntimeError('Browser QA failed')
        print(result.stdout)
    finally:
        for process in reversed(processes):
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        for log in logs:
            log.close()
        # Only the randomly named database created by this invocation is removed.
        sql('DROP DATABASE "' + name + '" WITH (FORCE)')
        print('Disposable QA database removed; existing database untouched.')

if __name__ == '__main__':
    main()
