CREATE SCHEMA IF NOT EXISTS devflow_reporting;

CREATE TABLE IF NOT EXISTS devflow_reporting.tasks (
    id uuid PRIMARY KEY,
    title text NOT NULL,
    status text NOT NULL CHECK (status IN ('running', 'completed', 'failed')),
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    result jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS devflow_reporting.events (
    id uuid PRIMARY KEY,
    task_id uuid REFERENCES devflow_reporting.tasks(id),
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    delivery_status text NOT NULL CHECK (delivery_status IN ('pending', 'sent', 'failed', 'not_requested')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS devflow_reporting.delivery_attempts (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES devflow_reporting.events(id),
    part_number integer NOT NULL,
    attempt_number integer NOT NULL,
    outcome text NOT NULL,
    http_status integer,
    error text,
    message_id text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, part_number, attempt_number)
);
