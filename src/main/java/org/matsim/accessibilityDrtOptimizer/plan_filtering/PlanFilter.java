package org.matsim.accessibilityDrtOptimizer.plan_filtering;

import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import com.google.common.base.Preconditions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.accessibilityDrtOptimizer.accessibility_calculator.AlternativeModeCalculator;
import org.matsim.accessibilityDrtOptimizer.accessibility_calculator.AlternativeModeTripData;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.drt.optimizer.constraints.DefaultDrtOptimizationConstraintsSet;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PlanFilter implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "config file", required = true)
    private String configPath;

    @CommandLine.Option(names = "--output", description = "directory to store filtered plans", required = true)
    private String outputDirectory;

    public static void main(String[] args) {
        new PlanFilter().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(Path.of(outputDirectory))){
            Files.createDirectories(Path.of(outputDirectory));
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup());
        config.global().setCoordinateSystem("EPSG:25832");
        Scenario scenario = ScenarioUtils.loadScenario(config);
        TransitSchedule schedule = scenario.getTransitSchedule();
        Vehicles vehicles= scenario.getTransitVehicles();
        Network network = scenario.getNetwork();
        Population inputPlans = scenario.getPopulation();

        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, vehicles, RaptorUtils.createStaticConfig(config), network, null);
        SwissRailRaptor raptor = new SwissRailRaptor.Builder(data, config).build();

        MultiModeDrtConfigGroup multiModeDrtConfigGroup = MultiModeDrtConfigGroup.get(config);
        Preconditions.checkArgument(multiModeDrtConfigGroup.getModalElements().size() == 1, "Only one DRT is currently supported. Check config file");
        DrtConfigGroup drtConfigGroup = multiModeDrtConfigGroup.getModalElements().iterator().next();

        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
        AlternativeModeCalculator alternativeModeCalculator = new AlternativeModeCalculator(raptor, network, travelTime, travelDisutility);

        List<Person> personList02 = new ArrayList<>();
        List<Person> personList04 = new ArrayList<>();
        List<Person> personList06 = new ArrayList<>();
        List<Person> personList08 = new ArrayList<>();
        List<Person> personList10 = new ArrayList<>();

        String alternativeModeDataOutput = outputDirectory + "/alternative-mode-data.csv";
        CSVPrinter tripsWriter = new CSVPrinter(new FileWriter(alternativeModeDataOutput), CSVFormat.TDF);
        tripsWriter.printRecord(AlternativeModeTripData.ALTERNATIVE_TRIP_DATA_TITLE_ROW);

        for (Person person : inputPlans.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            Preconditions.checkArgument(trips.size() == 1, "Only trip based plan are supported. Check the input plans!");
            TripStructureUtils.Trip trip = trips.get(0);
            Link fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
            if (fromLink == null) {
                fromLink = NetworkUtils.getNearestLink(network, trip.getOriginActivity().getCoord());
            }
            Link toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());
            if (toLink == null) {
                toLink = NetworkUtils.getNearestLink(network, trip.getDestinationActivity().getCoord());
            }
            double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);

            AlternativeModeTripData alternativeModeTripData = alternativeModeCalculator.calculateAlternativeTripData(person.getId().toString(), fromLink, toLink, departureTime);
            DefaultDrtOptimizationConstraintsSet constraints = (DefaultDrtOptimizationConstraintsSet) drtConfigGroup.addOrGetDrtOptimizationConstraintsParams().addOrGetDefaultDrtOptimizationConstraintsSet();
            double upperBound = constraints.maxTravelTimeAlpha * alternativeModeTripData.directCarTravelTime() + constraints.maxTravelTimeBeta;
            double alternativeTravelTime = alternativeModeTripData.actualTotalTravelTime();
            if (alternativeTravelTime > 0.2 * upperBound) {
                personList02.add(person);
            }

            if (alternativeTravelTime > 0.4 * upperBound) {
                personList04.add(person);
            }

            if (alternativeTravelTime > 0.6 * upperBound) {
                personList06.add(person);
            }

            if (alternativeTravelTime > 0.8 * upperBound) {
                personList08.add(person);
            }

            if (alternativeTravelTime > upperBound) {
                personList10.add(person);
            }

            // print alternative mode data
            alternativeModeTripData.printData(tripsWriter);
        }
        tripsWriter.close();

        Population population02 = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        personList02.forEach(population02::addPerson);
        new PopulationWriter(population02).write(outputDirectory + "/threshold-0.2.plans.xml.gz");

        Population population04 = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        personList04.forEach(population04::addPerson);
        new PopulationWriter(population04).write(outputDirectory + "/threshold-0.4.plans.xml.gz");

        Population population06 = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        personList06.forEach(population06::addPerson);
        new PopulationWriter(population06).write(outputDirectory + "/threshold-0.6.plans.xml.gz");

        Population population08 = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        personList08.forEach(population08::addPerson);
        new PopulationWriter(population08).write(outputDirectory + "/threshold-0.8.plans.xml.gz");

        Population population10 = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        personList10.forEach(population10::addPerson);
        new PopulationWriter(population10).write(outputDirectory + "/threshold-1.0.plans.xml.gz");

        return 0;
    }
}
