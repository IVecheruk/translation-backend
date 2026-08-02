CREATE TABLE processed_payment_events
  (
      id                 UUID PRIMARY KEY,
      provider           VARCHAR(32) NOT NULL,
      external_event_id  VARCHAR(255) NOT NULL,
      event_type         VARCHAR(128) NOT NULL,
      processed_at       TIMESTAMP WITH TIME ZONE
                             NOT NULL
                             DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT chk_processed_payment_events_provider
          CHECK (
              provider ~ '^[A-Z][A-Z0-9_]{0,31}$'
          ),

      CONSTRAINT chk_processed_payment_events_external_event_id
          CHECK (
              BTRIM(external_event_id) <> ''
          ),

      CONSTRAINT chk_processed_payment_events_event_type
          CHECK (
              BTRIM(event_type) <> ''
          ),

      CONSTRAINT uk_processed_payment_events_provider_event
          UNIQUE (
              provider,
              external_event_id
          )
  );

  CREATE INDEX idx_processed_payment_events_processed_at
      ON processed_payment_events (processed_at);