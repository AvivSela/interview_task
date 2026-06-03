ALTER TABLE click_analytics
    ADD COLUMN IF NOT EXISTS geo_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS country    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS city       VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_click_short_code
    ON click_analytics (short_code);

CREATE INDEX IF NOT EXISTS idx_click_short_code_country
    ON click_analytics (short_code, country);

CREATE INDEX IF NOT EXISTS idx_click_short_code_city
    ON click_analytics (short_code, city);
