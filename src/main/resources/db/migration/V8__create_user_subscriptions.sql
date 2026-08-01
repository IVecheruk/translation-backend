CREATE TABLE user_subscriptions
  (
      id                        UUID PRIMARY KEY,
      user_id                   UUID NOT NULL,
      plan_code                 VARCHAR(32) NOT NULL,
      status                    VARCHAR(16) NOT NULL,
      current_period_start      TIMESTAMP WITH TIME ZONE NOT NULL,
      current_period_end        TIMESTAMP WITH TIME ZONE NOT NULL,
      cancel_at_period_end      BOOLEAN NOT NULL DEFAULT FALSE,
      provider                  VARCHAR(32),
      external_customer_id      VARCHAR(255),
      external_subscription_id  VARCHAR(255),
      created_at                TIMESTAMP WITH TIME ZONE
                                    NOT NULL
                                    DEFAULT CURRENT_TIMESTAMP,
      updated_at                TIMESTAMP WITH TIME ZONE
                                    NOT NULL
                                    DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT fk_user_subscriptions_user
          FOREIGN KEY (user_id)
          REFERENCES users (id)
          ON DELETE CASCADE,

      CONSTRAINT fk_user_subscriptions_plan
          FOREIGN KEY (plan_code)
          REFERENCES subscription_plans (code)
          ON DELETE RESTRICT,

      CONSTRAINT chk_user_subscriptions_status
          CHECK (
              status IN (
                  'ACTIVE',
                  'PAST_DUE',
                  'CANCELED',
                  'EXPIRED'
              )
          ),

      CONSTRAINT chk_user_subscriptions_period
          CHECK (
              current_period_end > current_period_start
          ),

      CONSTRAINT chk_user_subscriptions_provider_format
          CHECK (
              provider IS NULL
              OR provider ~ '^[A-Z][A-Z0-9_]{0,31}$'
          ),

      CONSTRAINT chk_user_subscriptions_external_customer
          CHECK (
              external_customer_id IS NULL
              OR BTRIM(external_customer_id) <> ''
          ),

      CONSTRAINT chk_user_subscriptions_external_subscription
          CHECK (
              external_subscription_id IS NULL
              OR BTRIM(external_subscription_id) <> ''
          ),

      CONSTRAINT chk_user_subscriptions_provider_binding
          CHECK (
              (
                  provider IS NULL
                  AND external_customer_id IS NULL
                  AND external_subscription_id IS NULL
              )
              OR
              (
                  provider IS NOT NULL
                  AND external_subscription_id IS NOT NULL
              )
          )
  );

  CREATE INDEX idx_user_subscriptions_history
      ON user_subscriptions (
          user_id,
          created_at DESC
      );

  CREATE INDEX idx_user_subscriptions_active_lookup
      ON user_subscriptions (
          user_id,
          status,
          current_period_end
      );

  CREATE UNIQUE INDEX uk_user_subscriptions_active_user
      ON user_subscriptions (user_id)
      WHERE status = 'ACTIVE';

  CREATE UNIQUE INDEX uk_user_subscriptions_external_subscription
      ON user_subscriptions (
          provider,
          external_subscription_id
      )
      WHERE provider IS NOT NULL
        AND external_subscription_id IS NOT NULL;