"""PowerShell: agent/.venv/Scripts/python.exe -m agent.cli --help"""
import argparse
import json
import sys
from .config import ROOT, load_env, database_settings
from .discord import DiscordWebhook
from .redaction import Redactor
from .reporting import Reporter
from .store import Store
from .orchestrator import Orchestrator
from scripts.notify_discord import get_webhook_url


def main():
    # PowerShell's legacy default code page must not corrupt Korean JSON output.
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description='Run Codex tasks with audited Discord reporting')
    commands = parser.add_subparsers(dest='action', required=True)
    run = commands.add_parser('run', help='Run a real Codex task and report its lifecycle')
    run.add_argument('--title', required=True)
    prompt = run.add_mutually_exclusive_group(required=True)
    prompt.add_argument('--prompt')
    prompt.add_argument('--prompt-file', type=str)
    run.add_argument('--checks', choices=['none', 'agent', 'app', 'all'], default='none')
    run.add_argument('--sandbox', choices=['read-only', 'workspace-write'], default='workspace-write')
    run.add_argument('--timeout', type=int, default=1800)
    commands.add_parser('init-db', help='Add reporting schema/tables without modifying application data')
    commands.add_parser('webhook-test', help='Send a labeled connection test and audit delivery')
    inspect = commands.add_parser('inspect', help='Read recent task and delivery records')
    inspect.add_argument('--task-id')
    smoke = commands.add_parser('smoke', help='Explicitly labeled synthetic process fixtures, not a Codex run')
    smoke.add_argument('--scenario', choices=['success', 'failure', 'all'], default='all')
    args = parser.parse_args()
    if getattr(args, 'timeout', 1) < 1:
        parser.error('--timeout must be positive')
    load_env()
    redactor = Redactor()
    store = None
    try:
        store = Store(database_settings(), redactor)
        store.migrate()
        if args.action == 'init-db':
            print('Reporting schema ready (additive only).')
            return 0
        if args.action == 'inspect':
            print(json.dumps(store.inspect(args.task_id), ensure_ascii=False, default=str, indent=2))
            return 0
        webhook = DiscordWebhook(get_webhook_url() or '', redactor)
        reporter = Reporter(store, webhook, redactor)
        if args.action == 'webhook-test':
            success = reporter.emit(None, 'connection.test', {'source': 'explicit verification'},
                'DevFlow Discord Webhook 연결 테스트 — 실제 전송 및 PostgreSQL 기록 확인용입니다.')
            print(json.dumps({'connection_test': 'sent' if success else 'failed'}))
            return 0 if success else 2
        orchestrator = Orchestrator(ROOT, store, reporter, redactor)
        if args.action == 'run':
            from pathlib import Path
            prompt = args.prompt if args.prompt is not None else Path(args.prompt_file).read_text(encoding='utf-8-sig')
            task_id, status, _ = orchestrator.run(args.title, prompt, args.checks, args.sandbox, args.timeout)
            print(json.dumps({'task_id': task_id, 'status': status, 'delivery_failures': reporter.failures}))
            return (0 if status == 'completed' else 1) if not reporter.failures else 2
        scenarios = ['success', 'failure'] if args.scenario == 'all' else [args.scenario]
        for scenario in scenarios:
            fixture = ROOT / 'agent/tests/fixtures/codex_process.py'
            task_id, status, _ = orchestrator.run(
                '[SYNTHETIC TEST] ' + scenario, 'Synthetic lifecycle verification only',
                command=[sys.executable, str(fixture), scenario], verification=True, timeout=30)
            expected = 'completed' if scenario == 'success' else 'failed'
            print(json.dumps({'task_id': task_id, 'status': status, 'synthetic': True,
                              'expected_status': expected, 'delivery_failures': reporter.failures}))
            if status != expected or reporter.failures:
                return 2
        return 0
    except Exception as exc:
        # Driver and network exceptions can contain connection strings/URLs. Do not print them.
        print('Agent failed (' + type(exc).__name__ + '). Check configuration or inspect recorded tasks.', file=sys.stderr)
        return 2
    finally:
        if store:
            store.close()


if __name__ == '__main__':
    raise SystemExit(main())
