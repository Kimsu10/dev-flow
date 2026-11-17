CREATE TABLE blog.accounts (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 username varchar(100) NOT NULL,
 email varchar(254) NOT NULL,
 password_hash varchar(100),
 role varchar(10) NOT NULL CHECK (role IN ('ADMIN','USER','SYSTEM')),
 status varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 last_login_at timestamptz,
 CHECK ((role = 'SYSTEM' AND password_hash IS NULL) OR (role <> 'SYSTEM' AND password_hash IS NOT NULL)),
 CHECK (position('@' in username) = 0)
);
CREATE UNIQUE INDEX accounts_username_ci ON blog.accounts(lower(username));
CREATE UNIQUE INDEX accounts_email_ci ON blog.accounts(lower(email));
CREATE TABLE blog.service_tokens (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 account_id bigint NOT NULL REFERENCES blog.accounts(id),
 token_hash char(64) NOT NULL UNIQUE,
 created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL,
 revoked_at timestamptz
);
ALTER TABLE blog.posts ADD COLUMN author_id bigint REFERENCES blog.accounts(id);
-- PostgreSQL's name for the inline status CHECK in V1; leave the published_at invariant intact.
ALTER TABLE blog.posts DROP CONSTRAINT posts_status_check;
ALTER TABLE blog.posts ADD CONSTRAINT posts_status_check CHECK (status IN ('DRAFT','IN_REVIEW','PUBLISHED','ARCHIVED'));
CREATE INDEX posts_author_idx ON blog.posts(author_id);
