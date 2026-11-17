"""Deterministic child process fixture. Never impersonates a real Codex run."""
import json
import sys

if __name__ == '__main__':
    scenario = sys.argv[1]
    if scenario == 'timeout':
        import time
        time.sleep(30)
    if scenario == 'malformed':
        print('not JSON')
        raise SystemExit(0)
    print(json.dumps({'type': 'thread.started', 'thread_id': 'synthetic-thread'}))
    print(json.dumps({'type': 'turn.started'}))
    if scenario == 'failure':
        print(json.dumps({'type': 'turn.failed', 'error': {'message': 'Synthetic failure for Discord verification'}}))
        raise SystemExit(1)
    print(json.dumps({'type': 'item.completed', 'item': {
        'type': 'agent_message', 'text': 'Synthetic fixture succeeded. No real Codex task or build was executed.'}}))
    print(json.dumps({'type': 'turn.completed'}))
