CREATE TABLE user_profiles
    (
        user_id             UUID PRIMARY KEY,
        username            VARCHAR(30),
        display_name        VARCHAR(80),
        nickname            VARCHAR(50),
        profession          VARCHAR(100),
        bio                 VARCHAR(1000),
        avatar_object_key   VARCHAR(1024),
        created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

        CONSTRAINT fk_user_profiles_user
            FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

        CONSTRAINT uk_user_profiles_username
            UNIQUE (username),

        CONSTRAINT chk_user_profiles_username_format
            CHECK (
                username IS NULL
                OR username ~ '^[a-z][a-z0-9_]{2,29}$'
            )
    );

    INSERT INTO user_profiles (user_id)
    SELECT id
    FROM users;