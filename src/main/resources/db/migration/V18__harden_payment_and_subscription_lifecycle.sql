ALTER TABLE subscription_purchase_intents
    ADD COLUMN price_minor BIGINT,
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN billing_period VARCHAR(16),
    ADD COLUMN external_product_id VARCHAR(255);

UPDATE subscription_purchase_intents intent
SET price_minor = offer.price_minor,
    currency = offer.currency,
    billing_period = offer.billing_period,
    external_product_id = offer.external_product_id
FROM plan_payment_offers offer
WHERE intent.offer_code = offer.code
  AND intent.plan_code = offer.plan_code
  AND intent.provider = offer.provider;

ALTER TABLE subscription_purchase_intents
    ADD CONSTRAINT chk_purchase_intent_commercial_snapshot
        CHECK (
            (
                price_minor IS NULL
                AND currency IS NULL
                AND billing_period IS NULL
                AND external_product_id IS NULL
            )
            OR
            (
                price_minor > 0
                AND currency ~ '^[A-Z]{3}$'
                AND billing_period IN ('MONTH')
                AND (
                    external_product_id IS NULL
                    OR BTRIM(external_product_id) <> ''
                )
            )
        );

CREATE UNIQUE INDEX uk_purchase_intents_pending_user
    ON subscription_purchase_intents (user_id)
    WHERE status = 'PENDING';

ALTER TABLE user_subscriptions
    ADD COLUMN external_order_id VARCHAR(255),
    ADD COLUMN price_minor BIGINT,
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN billing_period VARCHAR(16),
    ADD COLUMN external_product_id VARCHAR(255);

WITH snapshots AS (
    SELECT DISTINCT ON (plan_code, provider)
           plan_code,
           provider,
           price_minor,
           currency,
           billing_period,
           external_product_id
    FROM plan_payment_offers
    ORDER BY plan_code, provider, active DESC, created_at DESC, code
)
UPDATE user_subscriptions subscription
SET price_minor = snapshot.price_minor,
    currency = snapshot.currency,
    billing_period = snapshot.billing_period,
    external_product_id = snapshot.external_product_id
FROM snapshots snapshot
WHERE subscription.plan_code = snapshot.plan_code
  AND subscription.provider = snapshot.provider;

ALTER TABLE user_subscriptions
    DROP CONSTRAINT chk_user_subscriptions_provider_binding;

UPDATE user_subscriptions
SET external_order_id = external_subscription_id,
    external_subscription_id = NULL
WHERE provider = 'TRIBUTE'
  AND external_order_id IS NULL
  AND external_subscription_id IS NOT NULL;

ALTER TABLE user_subscriptions
    ADD CONSTRAINT chk_user_subscriptions_external_order
        CHECK (
            external_order_id IS NULL
            OR BTRIM(external_order_id) <> ''
        ),
    ADD CONSTRAINT chk_user_subscriptions_provider_binding
        CHECK (
            (
                provider IS NULL
                AND external_customer_id IS NULL
                AND external_order_id IS NULL
                AND external_subscription_id IS NULL
            )
            OR
            (
                provider IS NOT NULL
                AND (
                    external_order_id IS NOT NULL
                    OR external_subscription_id IS NOT NULL
                )
            )
        ),
    ADD CONSTRAINT chk_user_subscriptions_commercial_snapshot
        CHECK (
            (
                price_minor IS NULL
                AND currency IS NULL
                AND billing_period IS NULL
                AND external_product_id IS NULL
            )
            OR
            (
                price_minor > 0
                AND currency ~ '^[A-Z]{3}$'
                AND billing_period IN ('MONTH')
                AND (
                    external_product_id IS NULL
                    OR BTRIM(external_product_id) <> ''
                )
            )
        );

CREATE UNIQUE INDEX uk_user_subscriptions_external_order
    ON user_subscriptions (provider, external_order_id)
    WHERE provider IS NOT NULL
      AND external_order_id IS NOT NULL;

DROP INDEX uk_user_subscriptions_active_user;

CREATE UNIQUE INDEX uk_user_subscriptions_live_user
    ON user_subscriptions (user_id)
    WHERE status IN ('ACTIVE', 'PAST_DUE');

CREATE INDEX idx_user_subscriptions_expiration_reconciliation
    ON user_subscriptions (current_period_end, id)
    WHERE status IN ('ACTIVE', 'PAST_DUE');

CREATE INDEX idx_processed_payment_events_retention
    ON processed_payment_events (processed_at, id);

CREATE INDEX idx_purchase_intents_retention
    ON subscription_purchase_intents (updated_at, id)
    WHERE status IN ('CONSUMED', 'EXPIRED', 'CANCELED');
