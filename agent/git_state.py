"""Read-only metadata and task-window fingerprints. Never sends raw patches."""
import hashlib
import re
import subprocess
from pathlib import Path


def git(root, *args):
    result = subprocess.run(['git', '-c', f'safe.directory={root.as_posix()}', *args], cwd=root,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
    if result.returncode:
        raise RuntimeError('Git metadata collection failed')
    return result.stdout.decode('utf-8', errors='replace')


def sensitive(path):
    return bool(re.search(r'(^|/)(\.env(?:\..*)?|auth\.json|credentials[^/]*|[^/]+\.(?:pem|key|pfx|p12))$', path, re.I))


def snapshot(root):
    paths = set(git(root, 'ls-files', '--cached', '--others', '--exclude-standard', '-z').split('\0')) - {''}
    fingerprints = {}
    for name in sorted(paths):
        if sensitive(name):
            continue
        path = root / name
        if path.is_symlink():
            fingerprints[name] = 'symlink:' + str(path.readlink())
        elif path.is_file():
            digest = hashlib.sha256()
            with path.open('rb') as source:
                for block in iter(lambda: source.read(65536), b''):
                    digest.update(block)
            fingerprints[name] = digest.hexdigest() + ':' + str(path.stat().st_mode)
        else:
            fingerprints[name] = 'missing'
    return {
        'fingerprints': fingerprints,
        'branch': git(root, 'branch', '--show-current').strip() or '(detached HEAD)',
        'status': git(root, 'status', '--short', '--branch'),
        'diff_stat': git(root, 'diff', '--stat', 'HEAD'),
    }


def changes(before, after):
    old, new = before['fingerprints'], after['fingerprints']
    return [name for name in sorted(old.keys() | new.keys()) if old.get(name) != new.get(name)]
