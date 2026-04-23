CREATE TABLE link_status_snapshots (
                                       id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       link_id              UUID NOT NULL UNIQUE,
                                       current_status       VARCHAR(20) NOT NULL,
                                       last_changed_at      TIMESTAMP NOT NULL,
                                       consecutive_failures INTEGER NOT NULL DEFAULT 0
);
