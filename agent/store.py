import json
from pathlib import Path
from uuid import uuid4
import psycopg
from psycopg.types.json import Jsonb


class Store:
    def __init__(self, settings, redactor):
        self.redactor = redactor
        self.connection = psycopg.connect(**settings, autocommit=True,
                                         options='-c statement_timeout=5000 -c lock_timeout=5000')

    def close(self):
        self.connection.close()

    def migrate(self):
        # Additive, namespaced tables only. Never touches existing application/bot data.
        with self.connection.transaction():
            self.connection.execute(Path(__file__).with_name('schema.sql').read_text(encoding='utf-8'))

    def start(self, task_id, title):
        self.connection.execute('INSERT INTO devflow_reporting.tasks(id,title,status) VALUES (%s,%s,%s)',
                                (task_id, self.redactor.text(title), 'running'))

    def finish(self, task_id, status, result):
        self.connection.execute('UPDATE devflow_reporting.tasks SET status=%s,finished_at=now(),result=%s WHERE id=%s',
                                (status, Jsonb(self.redactor.data(result)), task_id))

    def event(self, task_id, event_type, payload, notify=False):
        event_id = str(uuid4())
        self.connection.execute('INSERT INTO devflow_reporting.events(id,task_id,event_type,payload,delivery_status) VALUES (%s,%s,%s,%s,%s)',
            (event_id, task_id, event_type, Jsonb(self.redactor.data(payload)), 'pending' if notify else 'not_requested'))
        return event_id

    def attempt(self, event_id, part, attempt):
        self.connection.execute('INSERT INTO devflow_reporting.delivery_attempts(event_id,part_number,attempt_number,outcome,http_status,error,message_id) VALUES (%s,%s,%s,%s,%s,%s,%s)',
            (event_id, part, attempt.number, attempt.outcome, attempt.http_status,
             self.redactor.text(attempt.error) if attempt.error else None, attempt.message_id))

    def delivered(self, event_id, success):
        self.connection.execute('UPDATE devflow_reporting.events SET delivery_status=%s WHERE id=%s',
                                ('sent' if success else 'failed', event_id))

    def inspect(self, task_id=None):
        with self.connection.cursor(row_factory=psycopg.rows.dict_row) as cursor:
            cursor.execute('SELECT id,title,status,started_at,finished_at,result FROM devflow_reporting.tasks '
                           + ('WHERE id=%s ' if task_id else '') + 'ORDER BY started_at DESC LIMIT 10',
                           (task_id,) if task_id else ())
            tasks = cursor.fetchall()
            for task in tasks:
                cursor.execute('SELECT id,event_type,delivery_status FROM devflow_reporting.events WHERE task_id=%s ORDER BY created_at', (task['id'],))
                task['events'] = cursor.fetchall()
                cursor.execute('SELECT a.part_number,a.attempt_number,a.outcome,a.http_status,a.message_id '
                    'FROM devflow_reporting.delivery_attempts a JOIN devflow_reporting.events e ON e.id=a.event_id '
                    'WHERE e.task_id=%s ORDER BY a.id', (task['id'],))
                task['delivery_attempts'] = cursor.fetchall()
        return self.redactor.data(json.loads(json.dumps(tasks, default=str)))
