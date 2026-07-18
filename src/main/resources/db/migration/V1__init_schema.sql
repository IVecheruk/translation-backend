CREATE TABLE users
  (
      id            UUID PRIMARY KEY,
      email         VARCHAR(320) NOT NULL UNIQUE,
      password_hash VARCHAR(255) NOT NULL,
      created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
  );

  CREATE TABLE translation_jobs
  (
      id                UUID PRIMARY KEY,
      user_id           UUID NOT NULL,
      source_file_key   VARCHAR(1024) NOT NULL,
      result_file_key   VARCHAR(1024),
      source_lang       VARCHAR(16) NOT NULL,
      target_lang       VARCHAR(16) NOT NULL,
      status            VARCHAR(32) NOT NULL,
      file_format       VARCHAR(16) NOT NULL,
      created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      error_message     TEXT,

      CONSTRAINT fk_translation_jobs_user
          FOREIGN KEY (user_id)
          REFERENCES users (id),

      CONSTRAINT chk_translation_jobs_status
          CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),

      CONSTRAINT chk_translation_jobs_format
          CHECK (file_format IN ('docx', 'doc', 'pdf'))
  );

  CREATE INDEX idx_translation_jobs_user_id
      ON translation_jobs (user_id);

  CREATE INDEX idx_translation_jobs_status
      ON translation_jobs (status);

  CREATE INDEX idx_translation_jobs_created_at
      ON translation_jobs (created_at);