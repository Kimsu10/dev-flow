import json
import os
import shutil
from pathlib import Path
from uuid import uuid4
from .git_state import snapshot, changes
from .process import execute


def codex_command():
    configured = os.environ.get('CODEX_EXECUTABLE')
    package = os.environ.get('CODEX_MANAGED_PACKAGE_ROOT')
    if configured:
        path = Path(configured)
    elif package and (Path(package) / 'bin/codex.js').exists():
        path = Path(package) / 'bin/codex.js'
    else:
        found = shutil.which('codex')
        if not found:
            raise FileNotFoundError('Codex CLI not found; configure CODEX_EXECUTABLE')
        path = Path(found)
        if path.suffix.lower() in ('.cmd', '.ps1'):
            path = path.parent / 'node_modules/@openai/codex/bin/codex.js'
    if path.suffix.lower() == '.js':
        node = shutil.which('node')
        if not node or not path.is_file():
            raise FileNotFoundError('Codex JavaScript entry or Node.js not found')
        return [node, str(path)]
    if path.suffix.lower() in ('.cmd', '.ps1', '.bat'):
        raise ValueError('Use the Codex .exe or bin/codex.js entry, not a shell script')
    return [str(path)]


class CodexEvents:
    def __init__(self, store, task_id, redactor):
        self.store, self.task_id, self.redactor = store, task_id, redactor
        self.completed = False
        self.failure = None
        self.summary = ''
        self.thread_id = None
        self.command_count = 0

    def accept(self, line):
        try:
            event = json.loads(line)
        except (ValueError, TypeError):
            return
        if not isinstance(event, dict):
            return
        kind = event.get('type')
        metadata = {}
        if kind == 'thread.started':
            self.thread_id = str(event.get('thread_id', ''))[:100]
            metadata['thread_id'] = self.thread_id
        elif kind == 'turn.completed':
            self.completed = True
        elif kind == 'turn.failed':
            self.failure = self.redactor.text((event.get('error') or {}).get('message', 'Codex turn failed'))[:2000]
            metadata['error'] = self.failure
        elif kind == 'error':
            # Some CLI versions emit recoverable errors before a successful terminal event.
            metadata['message'] = self.redactor.text(event.get('message', 'Codex error'))[:1000]
        elif kind == 'item.completed':
            item = event.get('item', {})
            metadata['item_type'] = item.get('type')
            if item.get('type') == 'agent_message':
                self.summary = self.redactor.text(item.get('text', ''))[:12000]
                metadata['summary'] = self.summary
            elif item.get('type') == 'command_execution':
                self.command_count += 1
                metadata['exit_code'] = item.get('exit_code')
                # Commands and raw output can contain credentials; persist neither.
            elif item.get('type') == 'file_change':
                metadata['files'] = [self.redactor.text(str(c.get('path', ''))) for c in item.get('changes', [])]
            else:
                return
        elif kind != 'turn.started':
            return
        self.store.event(self.task_id, 'codex.' + kind, metadata)


def check_commands(root, profile):
    commands = []
    if profile in ('agent', 'all'):
        commands.append(('Agent unit tests', root, [os.sys.executable, '-m', 'unittest', 'discover', '-s', 'agent/tests', '-v']))
    if profile in ('app', 'all'):
        npm = 'npm.cmd' if os.name == 'nt' else 'npm'
        commands += [('Frontend tests', root / 'frontend', [npm, 'test']),
                     ('Frontend build', root / 'frontend', [npm, 'run', 'build']),
                     ('Backend build and tests', root / 'backend',
                      [str(root / 'backend/gradlew.bat'), 'build', '--no-daemon'] if os.name == 'nt'
                      else ['sh', './gradlew', 'build', '--no-daemon'])]
    return commands


class Orchestrator:
    def __init__(self, root, store, reporter, redactor):
        self.root, self.store, self.reporter, self.redactor = root, store, reporter, redactor

    def run(self, title, prompt, checks='none', sandbox='workspace-write', timeout=1800,
            command=None, verification=False):
        task_id = str(uuid4())
        title = self.redactor.text(title)[:300]
        self.store.start(task_id, title)
        self.reporter.emit(task_id, 'task.started', {'title': title},
                           f'▶ DevFlow 작업 시작\n작업 ID: {task_id}\n제목: {title}')
        result = {'checks': [], 'summary': '', 'error': None, 'verification_fixture': verification}
        status, before, events = 'failed', None, CodexEvents(self.store, task_id, self.redactor)
        try:
            before = snapshot(self.root)
            child_env = os.environ.copy()
            # Only the orchestrator needs the webhook and DB password. Preserve Orca hook env.
            for key in ('DISCORD_WEBHOOK_URL', 'POSTGRES_PASSWORD', 'DB_PASSWORD'):
                child_env.pop(key, None)
            child_env['DEVFLOW_ORCHESTRATED'] = '1'
            child_env['DEVFLOW_TASK_ID'] = task_id
            args = command or codex_command() + ['exec', '--json', '--color', 'never',
                                                  '--sandbox', sandbox, '-C', str(self.root), '-']
            instructions = ('DevFlow task. Do not git add, commit, push, or delete existing data. '
                'Do not read .env, credentials, auth files, or print secrets. '
                'The orchestrator handles reporting; do not call Discord or notification scripts.\n\n' + prompt)
            code, _ = execute(args, self.root, child_env, timeout, self.redactor,
                                 input_text=instructions, on_line=events.accept)
            result.update(exit_code=code, summary=events.summary or '(Codex 요약 없음)', thread_id=events.thread_id)
            if code != 0 or events.failure or not events.completed:
                result['error'] = events.failure or f'Codex exit={code}; terminal completion={events.completed}. Raw process output omitted.'
            else:
                status = 'completed'
                for label, cwd, check in check_commands(self.root, checks):
                    try:
                        check_code, _ = execute(check, cwd, child_env, min(timeout, 600), self.redactor)
                        entry = {'name': label, 'status': 'passed' if check_code == 0 else 'failed', 'exit_code': check_code}
                    except (OSError, TimeoutError) as exc:
                        entry = {'name': label, 'status': 'failed', 'error': self.redactor.text(str(exc))[:500]}
                    result['checks'].append(entry)
                    self.store.event(task_id, 'check.completed', entry)
                    if entry['status'] == 'failed':
                        status, result['error'] = 'failed', 'One or more build/test checks failed'
        except (Exception, KeyboardInterrupt) as exc:
            result['error'] = self.redactor.text(type(exc).__name__ + ': ' + str(exc))[:2000]
        try:
            after = snapshot(self.root)
            result.update(changed_files=changes(before, after) if before else [],
                          branch=after.get('branch', '(브랜치 확인 불가)'),
                          git_status=after['status'], diff_stat=after['diff_stat'],
                          initial_git_status=before['status'] if before else '(unavailable)')
        except Exception:
            status = 'failed'
            result.update(branch='(브랜치 확인 불가)', git_status='Git metadata unavailable', diff_stat='', changed_files=[])
            result['error'] = result['error'] or 'Git metadata collection failed'
        result = self.redactor.data(result)
        self.store.finish(task_id, status, result)
        files = '\n'.join(result['changed_files']) or '(작업 중 변경 없음; 민감 파일 제외)'
        failed_checks = [c for c in result['checks'] if c['status'] == 'failed']
        message = (f"{'✅ 완료' if status == 'completed' else '❌ 실패'} — DevFlow\n작업 ID: {task_id}\n제목: {title}\n"
                   f"\nCodex 요약:\n{result['summary']}\n\n변경 파일 (작업 시작 이후):\n{files}\n"
                   f"\nGit diff 요약 (현재 전체 추적 변경, 기존 변경 포함):\n{result['diff_stat'] or '(없음)'}\n")
        if failed_checks:
            checks_text = '\n'.join(
                f"{c['name']}: failed (exit={c.get('exit_code', 'n/a')})"
                + (f" — {c['error']}" if c.get('error') else '') for c in failed_checks)
            message += f'\n빌드/테스트 실패:\n{checks_text}\n'
        message += f"\n적용 브랜치: {result['branch']}"
        if result['error']:
            message += '\n오류 요약:\n' + result['error']
        self.reporter.emit(task_id, 'task.' + status, result, message)
        return task_id, status, result
