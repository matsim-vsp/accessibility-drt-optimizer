package org.matsim.accessibillityDrtOptimizer.accessibility_calculator;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.List;

@Deprecated
public record DefaultAccessibilityCalculator(SwissRailRaptor raptor) implements AccessibilityCalculator {

    @Override
    public AlternativeModeData calculateAlternativeMode(DrtRequest request) {
        Link fromLink = request.getFromLink();
        Link toLink = request.getToLink();
        double departureTime = request.getEarliestStartTime();
        return calculateAlternativeMode(fromLink, toLink, departureTime);
    }

    public AlternativeModeData calculateAlternativeMode(Link fromLink, Link toLink, double departureTime) {
        Coord fromCoord = fromLink.getToNode().getCoord();
        Coord toCoord = toLink.getToNode().getCoord();
        List<? extends PlanElement> legs =
                raptor.calcRoute(DefaultRoutingRequest.withoutAttributes
                        (new LinkWrapperFacility(fromLink), new LinkWrapperFacility(toLink), departureTime, null));

        if (legs == null) {
            // No route can be found -> walk as alternative mode
            double euclideanDistance = CoordUtils.calcEuclideanDistance(fromCoord, toCoord);
            double walkingDistance = euclideanDistance * 1.3;
            double walkTime = walkingDistance / 0.8333333333333333;
            // This is the default value from config file.
            // TODO Consider read it from config directly.
            return new AlternativeModeData(0, walkTime, walkingDistance, TransportMode.walk);
        }

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
            return new AlternativeModeData(totalWaitingTime, arrivalTimeOfPreviousLeg - departureTime, totalWalkingDistance, TransportMode.pt);
        }
    }

}
