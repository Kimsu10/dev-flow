-- Keep existing category IDs and references. Do not silently merge existing data.
CREATE FUNCTION blog.category_name_key(value text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$ SELECT lower(regexp_replace(btrim(value), '[[:space:]]+', ' ', 'g')) $$;
CREATE UNIQUE INDEX categories_name_key_unique ON blog.categories (blog.category_name_key(name));

CREATE TABLE blog.ai_drafts (
    id uuid PRIMARY KEY,
    author_id bigint NOT NULL REFERENCES blog.accounts(id),
    question varchar(2000) NOT NULL,
    content_markdown text NOT NULL,
    sources_json text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE blog.posts ADD COLUMN ai_draft_id uuid REFERENCES blog.ai_drafts(id);
