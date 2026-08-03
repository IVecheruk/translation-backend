package com.translatelab.backend.payment.repository;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanPaymentOfferRepository extends JpaRepository<PlanPaymentOffer, String> {

    Optional<PlanPaymentOffer> findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
            String planCode,
            String provider,
            BillingPeriod billingPeriod
    );

}