package com.translatelab.backend.plan.repository;

import com.translatelab.backend.plan.entity.PlanEntitlement;
import com.translatelab.backend.plan.entity.PlanEntitlementId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, PlanEntitlementId> {

    @EntityGraph(attributePaths = "plan")
    Optional<PlanEntitlement> findByIdAndPlan_ActiveTrue(PlanEntitlementId id);
}