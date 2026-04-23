CREATE TABLE link_check_results (
                                    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    link_id        UUID NOT NULL,
                                    checked_at     TIMESTAMP NOT NULL DEFAULT NOW(),
                                    http_status    INTEGER,
                                    response_time  INTEGER,
                                    status         VARCHAR(20) NOT NULL,
                                    redirect_url   TEXT
);
