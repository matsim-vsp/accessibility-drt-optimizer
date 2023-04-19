package org.matsim.accessibillityDrtOptimizer.utils;

import org.matsim.accessibillityDrtOptimizer.optimizer.basicStructures.GeneralRequest;
import org.matsim.contrib.drt.passenger.DrtRequest;

public class DrtOperationUtils {

    public static GeneralRequest createFromDrtRequest(DrtRequest drtRequest) {
        return new GeneralRequest(drtRequest.getPassengerId(), drtRequest.getFromLink().getId(),
                drtRequest.getToLink().getId(), drtRequest.getEarliestStartTime(), drtRequest.getLatestStartTime(),
                drtRequest.getLatestArrivalTime());
    }

}
