package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.matsim.api.core.v01.network.Link;

public class DefaultLinkMaxSpeedTable {
    public static final String MOTOR_WAY = "highway.motorway";
    public static final String TRUNK = "highway.trunk";
    public static final String PRIMARY = "highway.primary";
    public static final String SECONDARY = "highway.secondary";
    public static final String TERTIARY = "highway.tertiary";
    public static final String RESIDENTIAL = "highway.residential";
    public static final String UNCLASSIFIED = "highway.unclassified";

    public static final String MOTOR_WAY_LINK = "highway.motorway_link";
    public static final String TRUNK_LINK = "highway.trunk_link";
    public static final String PRIMARY_LINK = "highway.primary_link";
    public static final String SECONDARY_LINK = "highway.secondary_link";
    public static final String TERTIARY_LINK = "highway.tertiary_link";

    public double getMaxFreeSpeed(Link link) {
        // when allowed speed presents, use allowed speed as the max free speed
        if (link.getAttributes().getAttribute("allowed_speed") != null) {
            double allowedSpeed = Double.parseDouble(link.getAttributes().getAttribute("allowed_speed").toString());
            if (!Double.isNaN(allowedSpeed)) {
                return allowedSpeed;
            }
        }

        String roadType = link.getAttributes().getAttribute("type").toString();
        return switch (roadType) {
            case MOTOR_WAY, MOTOR_WAY_LINK -> 130 / 3.6;
            case RESIDENTIAL -> 30 / 3.6;
            default -> 100 / 3.6;
        };
    }


}
