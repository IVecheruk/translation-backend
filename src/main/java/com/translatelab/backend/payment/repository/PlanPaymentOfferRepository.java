package com.translatelab.backend.payment.repository;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanPaymentOfferRepository extends JpaRepository<PlanPaymentOffer, String> {

    Optional<PlanPaymentOffer> findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
            String planCode,
            String provider,
            BillingPeriod billingPeriod
    );

    @Query(
            value = """
                        SELECT offer
                          FROM PlanPaymentOffer offer
                          JOIN FETCH offer.plan plan
                          WHERE offer.provider = :provider
                            AND offer.billingPeriod = :billingPeriod
                            AND offer.active = true
                            AND plan.active = true
                          ORDER BY offer.priceMinor ASC, plan.code ASC
                    """
    )
    List<PlanPaymentOffer> findActiveCatalog(
            @Param("provider") String provider,
            @Param("billingPeriod") BillingPeriod billingPeriod
    );
}