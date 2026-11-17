import io
import json
import os
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

from agent.config import load_env
from agent.discord import DiscordWebhook, chunks
from agent.redaction import Redactor
from agent.reporting import Reporter
from agent.orchestrator import CodexEvents, Orchestrator
from agent.git_state import snapshot, changes

TEST_URL = 'https://discord.com/api/webhooks/123456789012345678/test-webhook-placeholder'


class MemoryStore:
    def __init__(self):
        self.events, self.attempts, self.tasks = [], [], {}

    def start(self, task_id, title):
        self.tasks[task_id] = {'title': title, 'status': 'running'}

    def finish(self, task_id, status, result):
        self.tasks[task_id].update(status=status, result=result)

    def event(self, task_id, kind, payload, notify=False):
        self.events.append(dict(task_id=task_id, kind=kind, payload=payload, notify=notify))
        return str(len(self.events))

    def attempt(self, event_id, part, attempt):
        self.attempts.append((event_id, part, attempt))

    def delivered(self, event_id, success):
        self.events[int(event_id) - 1]['success'] = success


class Response(io.BytesIO):
    status = 200

    def __init__(self):
        super().__init__(b'{"id":"1234"}')


class Opener:
    def __init__(self, outcomes):
        self.outcomes, self.requests = iter(outcomes), []

    def open(self, request, timeout):
        self.requests.append(request)
        result = next(self.outcomes)
        if isinstance(result, Exception):
            raise result
        return result


def http_error(status, body=b'{}', headers=None):
    return urllib.error.HTTPError(TEST_URL, status, 'test', headers or {}, io.BytesIO(body))


class RedactionTest(unittest.TestCase):
    def test_secrets_assignments_tokens_and_encoded_values(self):
        secret = 'test-password+/with-symbols'
        redactor = Redactor({'DB_PASSWORD': secret, 'DISCORD_WEBHOOK_URL': TEST_URL})
        from urllib.parse import quote
        text = redactor.text(f'{secret} {quote(secret, safe="")} {TEST_URL}\nBearer unknown-token\npassword: unknown\n')
        for value in [secret, quote(secret, safe=''), TEST_URL, 'unknown-token', 'password: unknown']:
            self.assertNotIn(value, text)
        self.assertEqual(redactor.data({'api_key': 'unconfigured-value'})['api_key'], '[REDACTED]')

    def test_private_key_and_mention(self):
        text = Redactor({}).text('-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY----- @everyone')
        self.assertNotIn('\nsecret\n', text)
        self.assertNotIn('@everyone', text)

    def test_env_precedence_quotes_bom_and_equals(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {'KEEP_ME': 'shell'}, clear=True):
            Path(folder, '.env').write_text('\ufeffKEEP_ME=file\nOTHER="value=one"\n# ignored\n', encoding='utf-8')
            load_env(Path(folder))
            self.assertEqual(os.environ['KEEP_ME'], 'shell')
            self.assertEqual(os.environ['OTHER'], 'value=one')


class TransportTest(unittest.TestCase):
    def test_429_wait_and_500_then_success_audit_every_attempt(self):
        opener = Opener([http_error(429, b'{"retry_after":0.2}'), http_error(503), Response()])
        waits, records = [], []
        client = DiscordWebhook(TEST_URL, Redactor({}), opener=opener, sleep=waits.append)
        self.assertTrue(client.send_part('test', records.append))
        self.assertEqual([r.outcome for r in records], ['retry', 'retry', 'sent'])
        self.assertEqual(waits, [0.2, 2])
        self.assertEqual(records[-1].message_id, '1234')
        payload = json.loads(opener.requests[0].data)
        self.assertEqual(payload['allowed_mentions'], {'parse': []})
        self.assertIn('wait=true', opener.requests[0].full_url)

    def test_nonretryable_and_bounded_retry(self):
        for errors, expected in [([http_error(401)], 1),
                                 ([urllib.error.URLError('secret URL')] * 3, 3)]:
            records = []
            client = DiscordWebhook(TEST_URL, Redactor({}), opener=Opener(errors), sleep=lambda _: None)
            self.assertFalse(client.send_part('test', records.append))
            self.assertEqual(len(records), expected)
            self.assertEqual(records[-1].outcome, 'failed')
            self.assertNotIn('secret URL', str(records))

    def test_excessive_rate_limit_stops_without_early_retry(self):
        records, waits = [], []
        client = DiscordWebhook(TEST_URL, Redactor({}), opener=Opener([http_error(429, b'{"retry_after":120}')]), sleep=waits.append)
        self.assertFalse(client.send_part('test', records.append))
        self.assertEqual(waits, [])

    def test_disallow_non_discord_and_http(self):
        for url in ['http://discord.com/api/webhooks/1/token', 'https://attacker.invalid/api/webhooks/1/token']:
            with self.assertRaises(ValueError):
                DiscordWebhook(url, Redactor({}))

    def test_utf16_chunking_and_redact_before_split(self):
        text = '😀' * 2500
        self.assertEqual(''.join(chunks(text)), text)
        self.assertTrue(all(len(part.encode('utf-16-le')) // 2 <= 1800 for part in chunks(text)))
        redactor, store = Redactor({'PASSWORD': 'sensitive-value'}), MemoryStore()
        opener = Opener([Response() for _ in range(5)])
        reporter = Reporter(store, DiscordWebhook(TEST_URL, redactor, opener=opener), redactor)
        reporter.emit(None, 'test', {}, 'x' * 1795 + 'sensitive-value' + '😀' * 1500)
        for request in opener.requests:
            content = json.loads(request.data)['content']
            self.assertLessEqual(len(content.encode('utf-16-le')) // 2, 2000)
            self.assertNotIn('sensitive-value', content)
        self.assertTrue(store.events[0]['success'])

    def test_partial_delivery_failure_is_audited(self):
        redactor, store = Redactor({}), MemoryStore()
        client = DiscordWebhook(TEST_URL, redactor, opener=Opener([Response(), http_error(400)]))
        reporter = Reporter(store, client, redactor)
        self.assertFalse(reporter.emit(None, 'test', {}, 'x' * 3000))
        self.assertEqual(len(store.attempts), 2)
        self.assertFalse(store.events[0]['success'])


class LifecycleTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name)
        # Process lifecycle tests use snapshots without any Git mutations.
        self.store, self.redactor = MemoryStore(), Redactor({})
        self.client = DiscordWebhook(TEST_URL, self.redactor,
                                    opener=Opener([Response() for _ in range(20)]))
        self.reporter = Reporter(self.store, self.client, self.redactor)
        self.fixture = Path(__file__).parent / 'fixtures/codex_process.py'

    def tearDown(self):
        self.folder.cleanup()

    def run_fixture(self, scenario, timeout=10):
        fake = {'fingerprints': {}, 'branch': 'feature/reporting', 'status': '## feature/reporting\n M existing.txt', 'diff_stat': ''}
        with patch('agent.orchestrator.snapshot', return_value=fake):
            return Orchestrator(self.root, self.store, self.reporter, self.redactor).run(
                'test', 'test', command=[sys.executable, str(self.fixture.resolve()), scenario],
                timeout=timeout, verification=True)

    def test_success_and_failure_have_one_terminal_report(self):
        for scenario, expected in [('success', 'completed'), ('failure', 'failed')]:
            task_id, status, _ = self.run_fixture(scenario)
            self.assertEqual(status, expected)
            events = [e['kind'] for e in self.store.events if e['task_id'] == task_id and e['notify']]
            self.assertEqual(events, ['task.started', 'task.' + expected])
            report = [e for e in self.store.events if e['task_id'] == task_id and e['notify']][-1]['payload']['report']
            self.assertIn('적용 브랜치: feature/reporting', report)
            self.assertNotIn('현재 Git 상태', report)
            self.assertNotIn(' M existing.txt', report)
            self.assertNotIn('빌드/테스트', report)

    def test_missing_terminal_event_is_failure(self):
        _, status, result = self.run_fixture('malformed')
        self.assertEqual(status, 'failed')
        self.assertIn('terminal completion=False', result['error'])

    def test_timeout_is_reported_and_child_is_stopped(self):
        _, status, result = self.run_fixture('timeout', timeout=0.2)
        self.assertEqual(status, 'failed')
        self.assertIn('TimeoutError', result['error'])

    def test_start_failure_is_reported(self):
        fake = {'fingerprints': {}, 'status': '', 'diff_stat': ''}
        with patch('agent.orchestrator.snapshot', return_value=fake):
            _, status, result = Orchestrator(self.root, self.store, self.reporter, self.redactor).run(
                'test', 'test', command=['nonexistent-devflow-test-executable'])
        self.assertEqual(status, 'failed')
        self.assertIn('FileNotFoundError', result['error'])

    def test_failed_check_makes_task_fail(self):
        fake = {'fingerprints': {}, 'status': '', 'diff_stat': ''}
        checks = [('successful check', self.root, [sys.executable, '-c', 'pass']),
                  ('forced failed check', self.root, [sys.executable, '-c', 'raise SystemExit(7)'])]
        with patch('agent.orchestrator.snapshot', return_value=fake), patch('agent.orchestrator.check_commands', return_value=checks):
            _, status, result = Orchestrator(self.root, self.store, self.reporter, self.redactor).run(
                'test', 'test', command=[sys.executable, str(self.fixture.resolve()), 'success'])
        self.assertEqual(status, 'failed')
        self.assertEqual(result['checks'][1]['exit_code'], 7)
        report = self.store.events[-1]['payload']['report']
        self.assertIn('빌드/테스트 실패:', report)
        self.assertIn('forced failed check: failed (exit=7)', report)
        self.assertNotIn('successful check', report)
        self.assertEqual(result['checks'][0]['status'], 'passed')

    def test_does_not_persist_command_or_raw_output(self):
        collector = CodexEvents(self.store, 'id', self.redactor)
        collector.accept(json.dumps({'type': 'item.completed', 'item': {
            'type': 'command_execution', 'command': 'a-secret-command', 'aggregated_output': 'raw-secret', 'exit_code': 0}}))
        self.assertNotIn('raw-secret', str(self.store.events))
        self.assertNotIn('a-secret-command', str(self.store.events))

    def test_changed_files_excludes_preexisting_unchanged_content(self):
        before = {'fingerprints': {'existing.txt': 'old', 'new.txt': 'first'}}
        after = {'fingerprints': {'existing.txt': 'old', 'new.txt': 'second'}}
        self.assertEqual(changes(before, after), ['new.txt'])

    def test_snapshot_detects_real_edits_and_excludes_sensitive_files(self):
        Path(self.root, 'existing.txt').write_text('old', encoding='utf-8')
        Path(self.root, '.env').write_text('SECRET=not-for-reporting', encoding='utf-8')
        def metadata(root, *args):
            return 'existing.txt\0.env\0' if args[0] == 'ls-files' else ''
        with patch('agent.git_state.git', side_effect=metadata):
            before = snapshot(self.root)
            self.assertNotIn('.env', before['fingerprints'])
            Path(self.root, 'existing.txt').write_text('edited', encoding='utf-8')
            after = snapshot(self.root)
        self.assertEqual(changes(before, after), ['existing.txt'])

    def test_delivery_failure_does_not_mislabel_codex_success(self):
        opener = Opener([http_error(403), http_error(403)])
        self.reporter = Reporter(self.store, DiscordWebhook(TEST_URL, self.redactor, opener=opener), self.redactor)
        task_id, status, _ = self.run_fixture('success')
        self.assertEqual(status, 'completed')
        self.assertEqual(self.store.tasks[task_id]['status'], 'completed')
        self.assertEqual(self.reporter.failures, 2)

    def test_legacy_hook_suppressed_for_managed_run(self):
        from scripts import notify_discord
        with patch.dict(os.environ, {'DEVFLOW_ORCHESTRATED': '1'}), patch.object(notify_discord, 'get_webhook_url') as getter:
            notify_discord.main()
            getter.assert_not_called()


if __name__ == '__main__':
    unittest.main()
