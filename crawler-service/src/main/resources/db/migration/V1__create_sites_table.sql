CREATE TABLE sites (
                       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       name            VARCHAR(255) NOT NULL,
                       root_url        TEXT NOT NULL,
                       crawl_depth     INTEGER NOT NULL DEFAULT 2,
                       check_interval  INTEGER NOT NULL DEFAULT 24,
                       webhook_url     TEXT,
                       owner_email     VARCHAR(255),
                       created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                       last_crawled_at TIMESTAMP
);