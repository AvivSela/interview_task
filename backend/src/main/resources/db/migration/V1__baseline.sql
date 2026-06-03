-- Baseline schema snapshot (generated from JPA entities)
CREATE TABLE IF NOT EXISTS short_links (
    id           BIGSERIAL    PRIMARY KEY,
    short_code   VARCHAR(255) UNIQUE,
    original_url TEXT         NOT NULL,
    strategy     VARCHAR(255),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    max_clicks   INTEGER,
    total_clicks INTEGER      NOT NULL DEFAULT 0,
    expires_at   TIMESTAMP,
    tags         VARCHAR(255),
    created_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS click_analytics (
    id         BIGSERIAL    PRIMARY KEY,
    short_code VARCHAR(255) NOT NULL,
    clicked_at TIMESTAMP,
    referer    TEXT,
    user_agent TEXT,
    ip_address VARCHAR(45)
);
