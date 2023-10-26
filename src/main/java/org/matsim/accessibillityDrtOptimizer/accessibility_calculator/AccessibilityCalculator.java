package org.matsim.accessibillityDrtOptimizer.accessibility_calculator;

import org.matsim.contrib.drt.passenger.DrtRequest;

public interface AccessibilityCalculator {
    AlternativeModeData calculateAlternativeMode(DrtRequest request);
}
