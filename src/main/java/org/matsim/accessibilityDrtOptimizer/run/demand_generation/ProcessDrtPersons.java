package org.matsim.accessibilityDrtOptimizer.run.demand_generation;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;

import java.util.Random;

import static org.matsim.accessibilityDrtOptimizer.run.demand_generation.RequestTypes.*;

/**
 * Tag person with has a disability and passengers who are willing to pay extra for the premium service
 */
public class ProcessDrtPersons {
    public static void main(String[] args) {
        Population drtPlans = PopulationUtils.readPopulation("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/v6.4/drt-plans-0.5pct.xml.gz");
        Random rand = new Random(1);
        for (Person drtPerson : drtPlans.getPersons().values()) {
            if (PersonUtils.getAge(drtPerson) != null && PersonUtils.getAge(drtPerson) > 67) {
                drtPerson.getAttributes().putAttribute(ATTRIBUTE_NAME_REMARK, OLD);
            } else if (rand.nextDouble() < 0.1) {
                drtPerson.getAttributes().putAttribute(ATTRIBUTE_NAME_REMARK, DISABLED);
            } else if (rand.nextDouble() < 0.2) {
                drtPerson.getAttributes().putAttribute(ATTRIBUTE_NAME_REMARK, PREMIUM);
            } else {
                drtPerson.getAttributes().putAttribute(ATTRIBUTE_NAME_REMARK, NORMAL);
            }
        }
        new PopulationWriter(drtPlans).write("/Users/luchengqi/Documents/MATSimScenarios/Berlin/accessibility-drt-study/v6.4/drt-plans-0.5pct-with-remarks.xml.gz");
    }
}
