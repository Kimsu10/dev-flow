CREATE TABLE blog.visitor_sessions (
 id uuid PRIMARY KEY,
 visitor_hash char(64) NOT NULL,
 started_at timestamptz NOT NULL,
 last_seen_at timestamptz NOT NULL,
 source varchar(20) NOT NULL CHECK(source IN ('GOOGLE','NAVER','DAUM','GITHUB','OTHER','DIRECT','INTERNAL')),
 referrer_domain varchar(253),
 utm_source varchar(100), utm_medium varchar(100), utm_campaign varchar(100)
);
CREATE INDEX visitor_sessions_started_idx ON blog.visitor_sessions(started_at);
CREATE INDEX visitor_sessions_last_seen_idx ON blog.visitor_sessions(last_seen_at);
CREATE TABLE blog.page_view_events (
 id uuid PRIMARY KEY,
 session_id uuid NOT NULL REFERENCES blog.visitor_sessions(id) ON DELETE CASCADE,
 visitor_hash char(64) NOT NULL,
 post_id bigint REFERENCES blog.posts(id) ON DELETE SET NULL,
 category_id bigint REFERENCES blog.categories(id) ON DELETE SET NULL,
 path varchar(200) NOT NULL,
 occurred_at timestamptz NOT NULL,
 dedup_bucket bigint NOT NULL,
 UNIQUE(session_id,path,dedup_bucket)
);
CREATE INDEX page_view_events_time_idx ON blog.page_view_events(occurred_at);
CREATE INDEX page_view_events_post_time_idx ON blog.page_view_events(post_id,occurred_at);
CREATE INDEX page_view_events_category_time_idx ON blog.page_view_events(category_id,occurred_at);
