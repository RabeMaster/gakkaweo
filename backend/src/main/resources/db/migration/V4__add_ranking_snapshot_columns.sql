ALTER TABLE game_sessions
    ADD COLUMN final_rank INTEGER;

ALTER TABLE daily_sentences
    ADD COLUMN total_players INTEGER;
