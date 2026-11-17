-- Additive version counter; timestamps alone cannot distinguish writes in one transaction.
ALTER TABLE blog.posts ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version >= 0);
