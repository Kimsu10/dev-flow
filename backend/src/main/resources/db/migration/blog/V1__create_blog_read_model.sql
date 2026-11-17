-- Only the new blog schema is managed here. Existing reporting data is untouched.
CREATE TABLE blog.categories (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(100) NOT NULL,
    slug varchar(100) NOT NULL UNIQUE,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE blog.posts (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id bigint REFERENCES blog.categories(id) ON DELETE RESTRICT,
    slug varchar(200) NOT NULL UNIQUE,
    title varchar(200) NOT NULL,
    content_markdown text NOT NULL DEFAULT '',
    summary varchar(500) NOT NULL DEFAULT '',
    thumbnail_url text,
    status varchar(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    deleted_at timestamptz,
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX posts_category_id_idx ON blog.posts(category_id);
CREATE INDEX posts_published_idx ON blog.posts(published_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX posts_category_published_idx ON blog.posts(category_id, published_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
