from .discord import chunks


class Reporter:
    def __init__(self, store, webhook, redactor):
        self.store, self.webhook, self.redactor = store, webhook, redactor
        self.failures = 0

    def emit(self, task_id, kind, payload, message):
        payload = self.redactor.data(payload)
        message = self.redactor.text(message)
        event_id = self.store.event(task_id, kind, {**payload, 'report': message}, notify=True)
        success = True
        parts = list(chunks(message))
        for index, part in enumerate(parts, 1):
            header = f'[DevFlow report {event_id} {index}/{len(parts)}]\n'
            if not self.webhook.send_part(header + part,
                    lambda attempt, n=index: self.store.attempt(event_id, n, attempt)):
                success = False
                break
        self.store.delivered(event_id, success)
        self.failures += not success
        return success
