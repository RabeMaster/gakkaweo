ALTER TABLE daily_sentences
    ADD COLUMN scheduled_at DATE UNIQUE;
CREATE INDEX idx_daily_sentences_unused ON daily_sentences (used_at, status)
    WHERE used_at IS NULL AND status = 'ACTIVE';
