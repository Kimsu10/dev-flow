import json
import os
import re
from urllib.parse import quote, quote_plus

SENSITIVE_NAME = re.compile(r'password|passwd|secret|token|api[_-]?key|webhook|credential|authorization|private[_-]?key', re.I)


class Redactor:
    def __init__(self, env=None):
        env = os.environ if env is None else env
        values = {v for k, v in env.items() if SENSITIVE_NAME.search(k) and v}
        # Also redact tokens embedded in the configured webhook, even without its URL.
        for value in list(values):
            if '/webhooks/' in value:
                values.add(value.rstrip('/').split('/')[-1].split('?')[0])
        self.secrets = sorted({variant for v in values for variant in
                               (v, quote(v, safe=''), quote_plus(v), json.dumps(v)[1:-1])},
                              key=len, reverse=True)

    def text(self, value):
        text = str(value)
        for secret in self.secrets:
            text = text.replace(secret, '[REDACTED]')
        text = re.sub(r'-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----',
                      '[REDACTED_PRIVATE_KEY]', text, flags=re.S)
        text = re.sub(r'https?://[^\s<>"\']*(?:discord(?:app)?\.com)/api/(?:v\d+/)?webhooks/[^\s<>"\']+',
                      '[REDACTED_WEBHOOK]', text, flags=re.I)
        text = re.sub(r'(?i)(Bearer|Bot)\s+[A-Za-z0-9._~+/=-]+', r'\1 [REDACTED]', text)
        text = re.sub(r'\b(?:sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)\b',
                      '[REDACTED_TOKEN]', text)
        text = re.sub(r'(?i)([a-z][a-z0-9+.-]*://[^\s/:@]+:)[^\s@]+@', r'\1[REDACTED]@', text)
        # Drop the entire rest of credential assignment lines (JSON, YAML, shell, headers).
        text = re.sub(r'(?im)([\w.-]*(?:password|passwd|secret|token|api[_-]?key|credential|authorization|webhook_url)[\w.-]*["\']?\s*[:=]\s*).*$',
                      r'\1[REDACTED]', text)
        return text.replace('\x00', '').replace('@everyone', '@\u200beveryone').replace('@here', '@\u200bhere')

    def data(self, value):
        if isinstance(value, dict):
            return {self.text(k): '[REDACTED]' if SENSITIVE_NAME.search(str(k)) else self.data(v)
                    for k, v in value.items()}
        if isinstance(value, (tuple, list)):
            return [self.data(v) for v in value]
        return self.text(value) if isinstance(value, str) else value
