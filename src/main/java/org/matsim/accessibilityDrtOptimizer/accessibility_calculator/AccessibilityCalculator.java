package org.matsim.accessibilityDrtOptimizer.accessibility_calculator;

import org.matsim.contrib.drt.passenger.DrtRequest;

@Deprecated
public interface AccessibilityCalculator {
    AlternativeModeData calculateAlternativeMode(DrtRequest request);
}
