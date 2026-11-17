"""Discord-only transport. Never return URL-bearing exceptions or response bodies."""
import json
import math
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass


@dataclass
class Attempt:
    number: int
    outcome: str
    http_status: int | None = None
    error: str | None = None
    message_id: str | None = None


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_webhook(url):
    parsed = urllib.parse.urlsplit(url)
    import re
    if (parsed.scheme != 'https' or parsed.hostname not in
            {'discord.com', 'discordapp.com', 'canary.discord.com', 'ptb.discord.com'}
            or parsed.username or parsed.password or parsed.port not in (None, 443)
            or not re.fullmatch(r'/api/(?:v\d+/)?webhooks/\d+/[A-Za-z0-9._-]+/?', parsed.path)):
        raise ValueError('A valid HTTPS Discord webhook is required')
    query = dict(urllib.parse.parse_qsl(parsed.query))
    query['wait'] = 'true'
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path,
                                    urllib.parse.urlencode(query), ''))


def chunks(text, limit=1800):
    """Count UTF-16 units conservatively, leaving room for correlation headers."""
    current, size = '', 0
    for char in text or '(empty report)':
        units = len(char.encode('utf-16-le')) // 2
        if size + units > limit:
            yield current
            current, size = '', 0
        current += char
        size += units
    if current:
        yield current


class DiscordWebhook:
    def __init__(self, url, redactor, max_attempts=3, opener=None, sleep=time.sleep):
        self.url = validate_webhook(url)
        self.redactor = redactor
        if not 1 <= max_attempts <= 5:
            raise ValueError('max_attempts must be between 1 and 5')
        self.max_attempts = max_attempts
        self.opener = opener or urllib.request.build_opener(NoRedirect())
        self.sleep = sleep

    def send_part(self, content, record):
        content = self.redactor.text(content)
        if len(content.encode('utf-16-le')) // 2 > 2000:
            raise ValueError('Discord content exceeds 2000 UTF-16 units')
        payload = json.dumps({'username': 'DevFlow Reports', 'content': content,
                              'allowed_mentions': {'parse': []}, 'flags': 4}).encode('utf-8')
        for number in range(1, self.max_attempts + 1):
            retry, delay, status, error, message_id = False, 2 ** (number - 1), None, None, None
            try:
                request = urllib.request.Request(self.url, data=payload, method='POST',
                    headers={'Content-Type': 'application/json', 'User-Agent': 'DevFlow-Reporter/1.0'})
                with self.opener.open(request, timeout=10) as response:
                    status = response.status
                    body = json.loads(response.read(65536).decode('utf-8') or '{}')
                    message_id = str(body['id']) if body.get('id') else None
                    if not 200 <= status < 300:
                        raise ValueError('Unexpected HTTP response')
                record(Attempt(number, 'sent', status, message_id=message_id))
                return True
            except urllib.error.HTTPError as exc:
                status = exc.code
                error = 'HTTP ' + str(status)
                retry = status == 429 or 500 <= status < 600
                if status == 429:
                    try:
                        body = json.loads(exc.read(65536))
                        delay = max(float(body.get('retry_after', 1)), float(exc.headers.get('Retry-After', 0)))
                        # Do not send earlier than Discord permits; stop if wait is excessive.
                        if not math.isfinite(delay) or delay > 30 or delay < 0:
                            retry = False
                    except (ValueError, TypeError, AttributeError):
                        delay = 2 ** (number - 1)
                exc.close()
            except (urllib.error.URLError, TimeoutError, OSError):
                error, retry = 'network/timeout error', True
            except (ValueError, TypeError, KeyError):
                error = 'invalid Discord response'
            will_retry = retry and number < self.max_attempts
            record(Attempt(number, 'retry' if will_retry else 'failed', status, error))
            if not will_retry:
                return False
            self.sleep(delay)
        return False
