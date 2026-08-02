CREATE TABLE subscription_purchase_intents
  (
      id                    UUID PRIMARY KEY,
      user_id               UUID NOT NULL,
      plan_code             VARCHAR(32) NOT NULL,
      provider              VARCHAR(32) NOT NULL,
      status                VARCHAR(16) NOT NULL,
      external_checkout_id  VARCHAR(255),
      expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
      consumed_at           TIMESTAMP WITH TIME ZONE,
      created_at            TIMESTAMP WITH TIME ZONE
                                NOT NULL
                                DEFAULT CURRENT_TIMESTAMP,
      updated_at            TIMESTAMP WITH TIME ZONE
                                NOT NULL
                                DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT fk_subscription_purchase_intents_user
          FOREIGN KEY (user_id)
          REFERENCES users (id)
          ON DELETE CASCADE,

      CONSTRAINT fk_subscription_purchase_intents_plan
          FOREIGN KEY (plan_code)
          REFERENCES subscription_plans (code)
          ON DELETE RESTRICT,

      CONSTRAINT chk_subscription_purchase_intents_status
          CHECK (
              status IN (
                  'PENDING',
                  'CONSUMED',
                  'EXPIRED',
                  'CANCELED'
              )
          ),

      CONSTRAINT chk_subscription_purchase_intents_provider
          CHECK (
              provider ~ '^[A-Z][A-Z0-9_]{0,31}$'
          ),

      CONSTRAINT chk_subscription_purchase_intents_external_checkout
          CHECK (
              external_checkout_id IS NULL
              OR BTRIM(external_checkout_id) <> ''
          ),

      CONSTRAINT chk_subscription_purchase_intents_expiration
          CHECK (
              expires_at > created_at
          ),

      CONSTRAINT chk_subscription_purchase_intents_consumed_at
          CHECK (
              (
                  status = 'CONSUMED'
                  AND consumed_at IS NOT NULL
              )
              OR
              (
                  status <> 'CONSUMED'
                  AND consumed_at IS NULL
              )
          )
  );

  CREATE INDEX idx_subscription_purchase_intents_user_history
      ON subscription_purchase_intents (
          user_id,
          created_at DESC
      );

  CREATE INDEX idx_subscription_purchase_intents_pending_expiration
      ON subscription_purchase_intents (expires_at)
      WHERE status = 'PENDING';

  CREATE UNIQUE INDEX uk_subscription_purchase_intents_external_checkout
      ON subscription_purchase_intents (
          provider,
          external_checkout_id
      )
      WHERE external_checkout_id IS NOT NULL;