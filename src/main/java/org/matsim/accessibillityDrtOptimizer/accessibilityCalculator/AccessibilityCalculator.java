package org.matsim.accessibillityDrtOptimizer.accessibilityCalculator;

import org.matsim.contrib.drt.passenger.DrtRequest;

public interface AccessibilityCalculator {
    AlternativeModeData calculateAlternativeMode(DrtRequest request);
}
