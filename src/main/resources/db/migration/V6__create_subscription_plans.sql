CREATE TABLE subscription_plans
  (
      code          VARCHAR(32) PRIMARY KEY,
      display_name  VARCHAR(100) NOT NULL,
      active        BOOLEAN NOT NULL DEFAULT TRUE,
      created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT chk_subscription_plans_code_format
          CHECK (
              code ~ '^[A-Z][A-Z0-9_]{0,31}$'
          ),

      CONSTRAINT chk_subscription_plans_display_name
          CHECK (
              BTRIM(display_name) <> ''
          )
  );

  CREATE TABLE plan_entitlements
  (
      plan_code      VARCHAR(32) NOT NULL,
      feature_code   VARCHAR(64) NOT NULL,
      limit_units    INTEGER,
      period_type    VARCHAR(16) NOT NULL,
      unlimited      BOOLEAN NOT NULL DEFAULT FALSE,
      created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT pk_plan_entitlements
          PRIMARY KEY (plan_code, feature_code),

      CONSTRAINT fk_plan_entitlements_plan
          FOREIGN KEY (plan_code)
          REFERENCES subscription_plans (code)
          ON DELETE CASCADE,

      CONSTRAINT chk_plan_entitlements_feature_code
          CHECK (
              feature_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
          ),

      CONSTRAINT chk_plan_entitlements_period_type
          CHECK (
              period_type IN ('MONTH')
          ),

      CONSTRAINT chk_plan_entitlements_limit
          CHECK (
              (
                  unlimited = TRUE
                  AND limit_units IS NULL
              )
              OR
              (
                  unlimited = FALSE
                  AND limit_units IS NOT NULL
                  AND limit_units > 0
              )
          )
  );

  INSERT INTO subscription_plans (
      code,
      display_name,
      active
  )
  VALUES (
      'FREE',
      'Бесплатный',
      TRUE
  );

  INSERT INTO plan_entitlements (
      plan_code,
      feature_code,
      limit_units,
      period_type,
      unlimited
  )
  VALUES (
      'FREE',
      'DOCUMENT_TRANSLATION',
      5,
      'MONTH',
      FALSE
  );