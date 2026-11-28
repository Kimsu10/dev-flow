INSERT INTO blog.categories(name,slug)
VALUES ('체험용','trial-' || gen_random_uuid()::text)
ON CONFLICT (blog.category_name_key(name)) DO UPDATE SET name='체험용';

CREATE TABLE blog.trial_ai_usage (
    account_id bigint NOT NULL REFERENCES blog.accounts(id),
    usage_date date NOT NULL,
    requests integer NOT NULL CHECK(requests > 0),
    PRIMARY KEY(account_id,usage_date)
);
