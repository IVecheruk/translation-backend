ALTER TABLE plan_payment_offers
      ADD CONSTRAINT uk_plan_payment_offers_intent_scope
          UNIQUE (
              code,
              plan_code,
              provider
          );

  ALTER TABLE subscription_purchase_intents
      ADD COLUMN offer_code VARCHAR(64);

  ALTER TABLE subscription_purchase_intents
      ADD CONSTRAINT fk_subscription_purchase_intents_offer_scope
          FOREIGN KEY (
              offer_code,
              plan_code,
              provider
          )
          REFERENCES plan_payment_offers (
              code,
              plan_code,
              provider
          )
          ON DELETE RESTRICT;

  CREATE INDEX idx_subscription_purchase_intents_offer
      ON subscription_purchase_intents (offer_code)
      WHERE offer_code IS NOT NULL;