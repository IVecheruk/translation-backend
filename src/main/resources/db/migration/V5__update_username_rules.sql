ALTER TABLE user_profiles
    DROP CONSTRAINT uk_user_profiles_username;

ALTER TABLE user_profiles
    DROP CONSTRAINT chk_user_profiles_username_format;

ALTER TABLE user_profiles
    ADD CONSTRAINT chk_user_profiles_username_format
        CHECK (
            username IS NULL
            OR username ~ '^[A-Za-z][A-Za-z0-9_]{2,29}$'
        );