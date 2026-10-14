package com.codeying.service.impl;

import com.codeying.risk.RiskCalculations;
import com.codeying.risk.RiskModelFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskCalculationsTest {
    @TempDir Path directory;

    @Test
    void holdoutMetricsPreservePositiveRankingAndNoPositiveSemantics() {
        int[] labels = {0, 1, 0, 1};
        double[] probabilities = {.1, .9, .8, .7};
        assertEquals((1.0 + 2.0 / 3.0) / 2.0, RiskCalculations.computePrAuc(labels, probabilities), 1e-12);
        assertEquals(.5, RiskCalculations.computeRecallAtTopPercent(labels, probabilities, .2));
        assertEquals(.5, RiskCalculations.computeFalsePositiveRate(labels, probabilities, .5));
        assertEquals(0, RiskCalculations.computePrAuc(new int[]{0}, new double[]{.9}));
        assertEquals(0, RiskCalculations.computeRecallAtTopPercent(new int[]{0}, new double[]{.9}, .2));
    }

    @Test
    void constantFeatureDoesNotCreateNanAndTrainingTransformIsReusable() {
        double[][] input = {new double[9], new double[9]};
        input[0][0] = 10; input[1][0] = 30;
        var standardizer = RiskCalculations.buildStandardizer(input);
        var transformed = RiskCalculations.applyStandardizer(input, standardizer);
        assertEquals(-1, transformed[0][0]); assertEquals(1, transformed[1][0]);
        assertTrue(Arrays.stream(transformed[0]).allMatch(Double::isFinite));
        assertArrayEquals(transformed[1], RiskCalculations.standardize(input[1], standardizer));
    }

    @Test
    void modelFilesRoundTripCloseResourcesAndRefuseOverwritingExistingVersion() throws Exception {
        String artifact = RiskModelFiles.save(directory.toString(), "new-version", "synthetic model");
        assertEquals("synthetic model", RiskModelFiles.load(directory.toString(), artifact, String.class));
        assertThrows(FileAlreadyExistsException.class, () -> RiskModelFiles.save(directory.toString(), "new-version", "replacement"));
        assertEquals("synthetic model", RiskModelFiles.load(directory.toString(), artifact, String.class));
        assertThrows(java.io.IOException.class, () -> RiskModelFiles.load(directory.toString(), artifact, ArrayList.class));
        // A move after the closed read succeeds on platforms that lock open files.
        Files.move(Path.of(artifact), directory.resolve("retained.ser"));
    }
}
