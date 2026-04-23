CREATE TABLE alert_logs (
                            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            site_id     UUID NOT NULL,
                            link_id     UUID NOT NULL,
                            alert_type  VARCHAR(20) NOT NULL,
                            fired_at    TIMESTAMP NOT NULL DEFAULT NOW(),
                            delivered   BOOLEAN NOT NULL DEFAULT FALSE
);