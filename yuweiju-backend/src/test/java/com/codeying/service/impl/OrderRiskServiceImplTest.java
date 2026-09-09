package com.codeying.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class OrderRiskServiceImplTest {

    @Test
    void shouldFallbackToBaseScoreRuleHitWhenRuleSetIsEmpty() {
        List<String> payload = OrderRiskServiceImpl.toRuleHitsPayload(Set.of());
        Assertions.assertEquals(List.of("base_score"), payload);
    }

    @Test
    void shouldKeepRuleHitsOrderWhenRuleSetHasItems() {
        Set<String> hits = new LinkedHashSet<>();
        hits.add("high_freq_1h");
        hits.add("amount_spike_3x");
        List<String> payload = OrderRiskServiceImpl.toRuleHitsPayload(hits);
        Assertions.assertEquals(List.of("high_freq_1h", "amount_spike_3x"), payload);
    }
}

