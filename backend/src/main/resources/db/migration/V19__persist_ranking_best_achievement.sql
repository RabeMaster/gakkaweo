ALTER TABLE game_sessions ADD COLUMN ranking_attempt_count INTEGER;
ALTER TABLE game_sessions ADD COLUMN ranking_achieved_at TIMESTAMPTZ;

-- 이관 전 세션의 최고 기록 도달 시점은 보관된 최고 점수 이력으로 추정합니다.
-- attempt_count는 최초 정답에서 증가를 멈추며 이후 개선된 추측에도 그 횟수를 유지합니다.
WITH first_best AS (
    SELECT DISTINCT ON (h.session_id) h.session_id, h.attempt_number, h.created_at
    FROM guess_history h JOIN game_sessions g ON g.id = h.session_id
    WHERE h.similarity = g.best_similarity AND g.best_similarity > 0
    ORDER BY h.session_id, h.attempt_number ASC, h.id ASC
)
UPDATE game_sessions g
SET ranking_attempt_count = LEAST(g.attempt_count, h.attempt_number),
    ranking_achieved_at = h.created_at
FROM first_best h WHERE g.id = h.session_id;

-- 추측 이력이 없는 이관 세션은 과거 최고 기록 도달 시점의 근거가 없습니다.
UPDATE game_sessions SET ranking_attempt_count = attempt_count, ranking_achieved_at = updated_at
WHERE best_similarity > 0 AND ranking_achieved_at IS NULL;
