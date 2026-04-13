UPDATE daily_sentences
SET status = 'USED'
WHERE used_at IS NOT NULL
  AND status = 'ACTIVE';
