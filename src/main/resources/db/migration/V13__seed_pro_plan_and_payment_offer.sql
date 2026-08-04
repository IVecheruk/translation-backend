INSERT INTO subscription_plans (
      code,
      display_name,
      active
  )
  VALUES (
      'PRO',
      'Профессиональный',
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
      'PRO',
      'DOCUMENT_TRANSLATION',
      100,
      'MONTH',
      FALSE
  );

  INSERT INTO plan_payment_offers (
      code,
      plan_code,
      provider,
      price_minor,
      currency,
      billing_period,
      external_product_id,
      active
  )
  VALUES (
      'PRO_TRIBUTE_MONTH',
      'PRO',
      'TRIBUTE',
      49900,
      'RUB',
      'MONTH',
      NULL,
      TRUE
  );
