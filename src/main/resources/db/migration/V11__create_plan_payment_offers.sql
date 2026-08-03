CREATE TABLE plan_payment_offers
  (
      code                 VARCHAR(64) PRIMARY KEY,
      plan_code            VARCHAR(32) NOT NULL,
      provider             VARCHAR(32) NOT NULL,
      price_minor          BIGINT NOT NULL,
      currency             VARCHAR(3) NOT NULL,
      billing_period       VARCHAR(16) NOT NULL,
      external_product_id  VARCHAR(255),
      active               BOOLEAN NOT NULL DEFAULT TRUE,
      created_at           TIMESTAMP WITH TIME ZONE
                               NOT NULL
                               DEFAULT CURRENT_TIMESTAMP,
      updated_at           TIMESTAMP WITH TIME ZONE
                               NOT NULL
                               DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT fk_plan_payment_offers_plan
          FOREIGN KEY (plan_code)
          REFERENCES subscription_plans (code)
          ON DELETE RESTRICT,

      CONSTRAINT chk_plan_payment_offers_code
          CHECK (
              code ~ '^[A-Z][A-Z0-9_]{0,63}$'
          ),

      CONSTRAINT chk_plan_payment_offers_provider
          CHECK (
              provider ~ '^[A-Z][A-Z0-9_]{0,31}$'
          ),

      CONSTRAINT chk_plan_payment_offers_price
          CHECK (
              price_minor > 0
          ),

      CONSTRAINT chk_plan_payment_offers_currency
          CHECK (
              currency ~ '^[A-Z]{3}$'
          ),

      CONSTRAINT chk_plan_payment_offers_billing_period
          CHECK (
              billing_period IN (
                  'MONTH'
              )
          ),

      CONSTRAINT chk_plan_payment_offers_external_product
          CHECK (
              external_product_id IS NULL
              OR BTRIM(external_product_id) <> ''
          )
  );

  CREATE UNIQUE INDEX uk_plan_payment_offers_active_scope
      ON plan_payment_offers (
          plan_code,
          provider,
          billing_period
      )
      WHERE active = TRUE;

  CREATE UNIQUE INDEX uk_plan_payment_offers_external_product
      ON plan_payment_offers (
          provider,
          external_product_id
      )
      WHERE external_product_id IS NOT NULL;

  CREATE INDEX idx_plan_payment_offers_active_catalog
      ON plan_payment_offers (
          provider,
          active,
          plan_code
      );