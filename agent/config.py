"""Load local settings without printing or persisting credentials."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load_env(root=ROOT):
    path = root / '.env'
    if path.exists():
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            key, value = line.split('=', 1)
            key = key.strip().removeprefix('export ')
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            os.environ.setdefault(key, value)


def database_settings():
    password = os.environ.get('DB_PASSWORD', os.environ.get('POSTGRES_PASSWORD', ''))
    if not password:
        raise ValueError('DB_PASSWORD or POSTGRES_PASSWORD is required')
    return dict(host=os.environ.get('POSTGRES_HOST', 'localhost'),
                port=int(os.environ.get('POSTGRES_PORT', '15432')),
                dbname=os.environ.get('POSTGRES_DB', 'devflow'),
                user=os.environ.get('DB_USERNAME', os.environ.get('POSTGRES_USER', 'devflow')),
                password=password, connect_timeout=5,
                application_name='devflow-reporting')
