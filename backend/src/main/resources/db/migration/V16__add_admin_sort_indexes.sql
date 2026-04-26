-- 어드민 문장 목록 동적 정렬 보조 인덱스
-- status 필터 + created_at 정렬 결합 패턴 (가장 흔한 운영 정렬)
CREATE INDEX IF NOT EXISTS idx_daily_sentences_status_created
    ON daily_sentences (status, created_at DESC);

-- 스케줄 정렬 (NULL 다수 + 미래 일정 소수: B-tree로 충분)
CREATE INDEX IF NOT EXISTS idx_daily_sentences_scheduled_at
    ON daily_sentences (scheduled_at);
