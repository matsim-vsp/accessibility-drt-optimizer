package org.matsim.accessibilityDrtOptimizer.prepare;

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
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;

public class PrepareAlternativeModeData implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "path to config file", required = true)
    private String configPath;

    @CommandLine.Option(names = "--plans", description = "path to plans file", required = true)
    private String plans;

    @CommandLine.Option(names = "--output", description = "output path to alternative mode data", required = true)
    private Path output;

    public static void main(String[] args) {
        new PrepareAlternativeModeData().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup());
        config.global().setCoordinateSystem("EPSG:25832");
        Scenario scenario = ScenarioUtils.loadScenario(config);
        TransitSchedule schedule = scenario.getTransitSchedule();
        Vehicles vehicles = scenario.getTransitVehicles();
        Network network = scenario.getNetwork();
        Population inputPlans = PopulationUtils.readPopulation(plans);

        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, vehicles, RaptorUtils.createStaticConfig(config), network, null);
        SwissRailRaptor raptor = new SwissRailRaptor.Builder(data, config).build();
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);

        AlternativeModeCalculator alternativeModeCalculator = new AlternativeModeCalculator(raptor, network, travelTime, travelDisutility);

        CSVPrinter writer = new CSVPrinter(new FileWriter(output.toString(), false), CSVFormat.TDF);
        writer.printRecord(AlternativeModeTripData.ALTERNATIVE_TRIP_DATA_TITLE_ROW);

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
            alternativeModeTripData.printData(writer);
        }

        writer.close();
        return 0;
    }
}
