CREATE TABLE discovered_links (
                                  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  site_id    UUID NOT NULL REFERENCES sites(id),
                                  url        TEXT NOT NULL,
                                  found_on   TEXT NOT NULL,
                                  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                  UNIQUE(site_id, url)
);