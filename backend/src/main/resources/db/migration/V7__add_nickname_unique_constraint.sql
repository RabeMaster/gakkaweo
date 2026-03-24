ALTER TABLE members
    ADD CONSTRAINT uq_members_nickname UNIQUE (nickname);
