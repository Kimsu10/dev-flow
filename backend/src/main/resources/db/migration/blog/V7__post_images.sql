CREATE TABLE blog.post_images (
    id uuid PRIMARY KEY,
    post_id bigint NOT NULL REFERENCES blog.posts(id),
    object_key text NOT NULL UNIQUE,
    content_type varchar(40) NOT NULL,
    byte_size integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX post_images_post_id_idx ON blog.post_images(post_id);
