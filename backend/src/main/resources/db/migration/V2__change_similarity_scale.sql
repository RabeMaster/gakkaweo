ALTER TABLE game_sessions
    ALTER COLUMN best_similarity TYPE DECIMAL(4, 1);
ALTER TABLE guess_history
    ALTER COLUMN similarity TYPE DECIMAL(4, 1);
