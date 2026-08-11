package com.translatelab.backend.payment.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BillingPeriodTest {

    @Test
    void shouldMatchV11DatabaseContract() {
        assertArrayEquals(
                new BillingPeriod[]{BillingPeriod.MONTH},
                BillingPeriod.values()
        );
    }
}
