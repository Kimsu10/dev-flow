-- Existing statuses, authors, creation dates, and publication dates are unchanged.
ALTER TABLE blog.posts ADD COLUMN visibility varchar(10) NOT NULL DEFAULT 'PUBLIC'
    CHECK (visibility IN ('PUBLIC','PRIVATE'));
CREATE INDEX posts_author_publication_idx ON blog.posts(author_id,published_at DESC,id DESC)
    WHERE deleted_at IS NULL;
