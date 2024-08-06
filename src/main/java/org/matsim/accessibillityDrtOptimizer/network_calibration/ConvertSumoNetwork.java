package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.matsim.application.prepare.network.CreateNetworkFromSumo;

public class ConvertSumoNetwork {
    public static void main(String[] args) {
        new CreateNetworkFromSumo().execute(
                args[0], "--output", args[1]
        );
    }
}
