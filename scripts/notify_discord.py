
import json
import os
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def get_webhook_url():
    from agent.config import load_env
    load_env(PROJECT_ROOT)
    return os.environ.get('DISCORD_WEBHOOK_URL')


def main():
    # A managed run is reported by the orchestrator, never by this legacy hook too.
    if os.environ.get('DEVFLOW_ORCHESTRATED') == '1':
        return
    if len(sys.argv) < 2:
        return

    try:
        event = json.loads(sys.argv[1])
        if not isinstance(event, dict):
            return
    except ValueError:
        return

    if event.get("type") != "agent-turn-complete":
        return

    webhook_url = get_webhook_url()
    if not webhook_url:
        print("Discord webhook is not configured.", file=sys.stderr)
        return

    from agent.redaction import Redactor
    redactor = Redactor()
    message = redactor.text(event.get("last-assistant-message") or "작업이 완료되었습니다.")
    thread_id = event.get("thread-id", "unknown")
    turn_id = event.get("turn-id", "unknown")

    content = (
        "✅ **Codex 작업 완료**\n\n"
        f"**프로젝트:** DevFlow\n"
        f"**세션:** `{thread_id}`\n"
        f"**작업:** `{turn_id}`\n\n"
        f"**작업 결과:**\n{message[:1400]}"
    )

    try:
        from agent.discord import DiscordWebhook, chunks
        webhook = DiscordWebhook(webhook_url, redactor)
        success = all(webhook.send_part(part, lambda attempt: None)
                      for part in chunks(redactor.text(content)))
        print('Discord notification sent.' if success else 'Discord notification failed.')
    except Exception:
        print("Discord notification failed.", file=sys.stderr)


if __name__ == "__main__":
    main()
