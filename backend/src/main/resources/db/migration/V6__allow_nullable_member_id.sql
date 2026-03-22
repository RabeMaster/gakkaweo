-- 회원 탈퇴 시 게임 기록 익명화를 위해 member_id nullable 변경
ALTER TABLE game_sessions
    ALTER COLUMN member_id DROP NOT NULL;

ALTER TABLE sentence_uploads
    ALTER COLUMN admin_id DROP NOT NULL;
