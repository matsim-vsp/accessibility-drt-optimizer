package org.matsim.accessibillityDrtOptimizer.optimizer;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.constraints.ConstraintSetChooser;
import org.matsim.contrib.drt.optimizer.constraints.DefaultDrtOptimizationConstraintsSet;
import org.matsim.contrib.drt.optimizer.constraints.DrtOptimizationConstraintsParams;
import org.matsim.contrib.drt.optimizer.constraints.DrtOptimizationConstraintsSet;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PersonAttributeBasedConstraintSelector implements ConstraintSetChooser {
    private final Map<String, DrtOptimizationConstraintsSet> constraintsMap;

    public PersonAttributeBasedConstraintSelector(DrtConfigGroup drtConfigGroup) {
        this.constraintsMap = new HashMap<>();
        drtConfigGroup.addOrGetDrtOptimizationConstraintsParams().getDrtOptimizationConstraintsSets().
                forEach(constraintSet -> constraintsMap.put(constraintSet.name, constraintSet));
    }

    @Override
    public Optional<DrtOptimizationConstraintsSet> chooseConstraintSet(double time, Link from, Link to, Person person, Attributes attributes) {
        if (person.getAttributes().getAttribute("remark").toString().equals("premium")) {
            return Optional.of(constraintsMap.get("premium"));
        }
        return Optional.of(constraintsMap.get("default"));
    }

    public static void prepareDrtConstraint(DrtConfigGroup drtConfigGroup,
                                            double defaultMaxTravelTimeAlpha, double defaultMaxTravelTimeBeta, double defaultMaxWaitTime,
                                            double premiumMaxTravelTimeAlpha, double premiumMaxTravelTimeBeta, double premiumMaxWaitTime) {
        DrtOptimizationConstraintsParams params = drtConfigGroup.addOrGetDrtOptimizationConstraintsParams();
        DefaultDrtOptimizationConstraintsSet defaultConstraintsSet =
                (DefaultDrtOptimizationConstraintsSet) params.addOrGetDefaultDrtOptimizationConstraintsSet();
        defaultConstraintsSet.maxTravelTimeAlpha = defaultMaxTravelTimeAlpha;
        defaultConstraintsSet.maxTravelTimeBeta = defaultMaxTravelTimeBeta;
        defaultConstraintsSet.maxWaitTime = defaultMaxWaitTime;
        defaultConstraintsSet.rejectRequestIfMaxWaitOrTravelTimeViolated = false;

        DefaultDrtOptimizationConstraintsSet premiumConstraintsSet = new DefaultDrtOptimizationConstraintsSet();
        premiumConstraintsSet.name = "premium";
        premiumConstraintsSet.maxTravelTimeAlpha = premiumMaxTravelTimeAlpha;
        premiumConstraintsSet.maxTravelTimeBeta = premiumMaxTravelTimeBeta;
        premiumConstraintsSet.maxWaitTime = premiumMaxWaitTime;
        premiumConstraintsSet.rejectRequestIfMaxWaitOrTravelTimeViolated = false;
        params.addParameterSet(premiumConstraintsSet);
    }
}
