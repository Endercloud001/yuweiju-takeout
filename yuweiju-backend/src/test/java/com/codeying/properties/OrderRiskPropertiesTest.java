package com.codeying.properties;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

class OrderRiskPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldUseExpectedDefaultValues() {
        OrderRiskProperties properties = new OrderRiskProperties();
        Assertions.assertEquals("v1", properties.getFeatureVersion());
        Assertions.assertEquals("./runtime/order-risk-models", properties.getModelDir());
        Assertions.assertEquals(12, properties.getThresholds().getLow());
        Assertions.assertEquals(16, properties.getThresholds().getMedium());
        Assertions.assertEquals(50, properties.getTraining().getMinAnomalyLabels());
        Assertions.assertEquals(0.02, properties.getTraining().getRejectThresholds().getPrAucDelta());
        Assertions.assertEquals(0.05, properties.getTraining().getRejectThresholds().getRecallDelta());
    }

    @Test
    void shouldFailValidationWhenThresholdsAreOutOfRange() {
        OrderRiskProperties properties = new OrderRiskProperties();
        properties.getThresholds().setLow(-1);
        properties.getThresholds().setMedium(101);

        Set<String> paths = validateAndCollectPaths(properties);
        Assertions.assertTrue(paths.contains("thresholds.low"));
        Assertions.assertTrue(paths.contains("thresholds.medium"));
    }

    @Test
    void shouldFailValidationWhenRejectThresholdsAreNegative() {
        OrderRiskProperties properties = new OrderRiskProperties();
        properties.getTraining().getRejectThresholds().setPrAucDelta(-0.01);
        properties.getTraining().getRejectThresholds().setRecallDelta(-0.01);

        Set<String> paths = validateAndCollectPaths(properties);
        Assertions.assertTrue(paths.contains("training.rejectThresholds.prAucDelta"));
        Assertions.assertTrue(paths.contains("training.rejectThresholds.recallDelta"));
    }

    private Set<String> validateAndCollectPaths(OrderRiskProperties properties) {
        Set<ConstraintViolation<OrderRiskProperties>> violations = validator.validate(properties);
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}

