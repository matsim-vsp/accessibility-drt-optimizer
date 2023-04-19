package org.matsim.accessibillityDrtOptimizer.accessibilityCalculator;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.pt.router.FakeFacility;

import java.util.List;

public class DefaultAccessibilityCalculator implements AccessibilityCalculator {
    private final SwissRailRaptor raptor;

    public DefaultAccessibilityCalculator(SwissRailRaptor raptor) {
        this.raptor = raptor;
    }

    @Override
    public AlternativeModeData calculateAlternativeMode(DrtRequest request) {
        Coord fromCoord = request.getFromLink().getToNode().getCoord();
        Coord toCoord = request.getToLink().getToNode().getCoord();
        List<? extends PlanElement> legs =
                raptor.calcRoute(DefaultRoutingRequest.withoutAttributes
                        (new LinkWrapperFacility(request.getFromLink()), new LinkWrapperFacility(request.getToLink()), request.getEarliestStartTime(), null));

        if (legs.size() == 1) {
            // Direct walk is faster
            Leg walking = (Leg) legs.get(0);
            return new AlternativeModeData(0, walking.getTravelTime().orElseThrow(RuntimeException::new), walking.getRoute().getDistance(), walking.getMode());
        } else {
            double totalWalkingDistance = 0;
            double arrivalTimeOfPreviousLeg = 0;
            double totalWaitingTime = 0;

            for (PlanElement planElement : legs) {
                Leg leg = (Leg) planElement;

                if (leg.getMode().equals(TransportMode.walk)) {
                    totalWalkingDistance += leg.getRoute().getDistance();
                }

                if (leg.getMode().equals(TransportMode.pt)) {
                    totalWaitingTime += (leg.getDepartureTime().orElseThrow(RuntimeException::new) - arrivalTimeOfPreviousLeg);
                }

                arrivalTimeOfPreviousLeg = leg.getDepartureTime().orElseThrow(RuntimeException::new) + leg.getTravelTime().orElseThrow(RuntimeException::new);
            }
            return new AlternativeModeData(totalWaitingTime, arrivalTimeOfPreviousLeg - request.getEarliestStartTime(), totalWalkingDistance, TransportMode.pt);
        }
    }
}
