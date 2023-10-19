package org.matsim.accessibillityDrtOptimizer.optimizer;

import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.dvrp.fleet.Fleet;

public class TimeVaryingRejectionThreshold {
    private final Fleet fleet;
    private final DrtScheduleInquiry scheduleInquiry;

    private final boolean enable;

    public TimeVaryingRejectionThreshold(Fleet fleet, DrtScheduleInquiry scheduleInquiry, boolean enable) {
        this.fleet = fleet;
        this.scheduleInquiry = scheduleInquiry;
        this.enable = enable;
    }

    public double getThresholdFactor() {
        if (!enable){
            return 1;
        }

        int fleetSize = fleet.getVehicles().size();
        long idleVehicles = fleet.getVehicles().values().stream().filter(scheduleInquiry::isIdle).count();
        double freeRatio = (double) idleVehicles / (double) fleetSize;

        // Currently, a simple on/off model. More complex model can be implemented later
        if (freeRatio < 0.75) {
            return 1;
        }

        return 0;
    }
}
