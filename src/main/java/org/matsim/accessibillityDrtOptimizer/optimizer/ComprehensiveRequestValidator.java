package org.matsim.accessibillityDrtOptimizer.optimizer;

import org.matsim.accessibillityDrtOptimizer.accessibility_calculator.AlternativeModeTripData;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.optimizer.constraints.DefaultDrtOptimizationConstraintsSet;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.passenger.PassengerRequest;
import org.matsim.contrib.dvrp.passenger.PassengerRequestValidator;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.matsim.contrib.dvrp.passenger.DefaultPassengerRequestValidator.EQUAL_FROM_LINK_AND_TO_LINK_CAUSE;

public class ComprehensiveRequestValidator implements PassengerRequestValidator {
    private static final String USE_ALTERNATIVE_MODE = "please_use_alternative_mode";
    private final Population population;
    private final Map<Integer, Double> thresholdMap;
    private final double timeBinSize;
    private final Map<String, AlternativeModeTripData> alternativeModeTripDataMap;
    private final DrtConfigGroup drtConfigGroup;

    public ComprehensiveRequestValidator(Population population, Map<Integer, Double> thresholdMap, double timeBinSize,
                                         Map<String, AlternativeModeTripData> alternativeModeTripDataMap, DrtConfigGroup drtConfigGroup) {
        this.population = population;
        this.thresholdMap = thresholdMap;
        this.timeBinSize = timeBinSize;
        this.alternativeModeTripDataMap = alternativeModeTripDataMap;
        this.drtConfigGroup = drtConfigGroup;
    }

    @Override
    public Set<String> validateRequest(PassengerRequest request) {
        // same as in DefaultPassengerRequestValidator, the request is invalid if fromLink == toLink
        if (request.getFromLink() == request.getToLink()) {
            return Collections.singleton(EQUAL_FROM_LINK_AND_TO_LINK_CAUSE);
        }

        // if the request is made by a person with special need (e.g., attribute: "remark" = "old" or "special")
        // Then they will always be valid request
        Person person = population.getPersons().get(request.getPassengerIds().get(0));
        if (!person.getAttributes().getAttribute("remark").toString().equals("normal")) {
            return Set.of();
        }

        // otherwise, we determine if a request is valid based on its alternative modes
        DefaultDrtOptimizationConstraintsSet defaultConstraintsSet =
                (DefaultDrtOptimizationConstraintsSet) drtConfigGroup.addOrGetDrtOptimizationConstraintsParams()
                        .addOrGetDefaultDrtOptimizationConstraintsSet();
        AlternativeModeTripData alternativeData = alternativeModeTripDataMap.get(person.getId().toString());
        double maxDrtTravelTime = defaultConstraintsSet.maxTravelTimeAlpha * alternativeData.directCarTravelTime() + defaultConstraintsSet.maxTravelTimeBeta;
        double threshold = thresholdMap.get((int) Math.floor(request.getEarliestStartTime() / timeBinSize));
        double alternativeModeTotalTravelTime = alternativeData.actualTotalTravelTime();
        if (alternativeModeTotalTravelTime < maxDrtTravelTime * threshold) {
            return Set.of(USE_ALTERNATIVE_MODE);
        }
        return Set.of();
    }
}
