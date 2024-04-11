package org.matsim.accessibillityDrtOptimizer.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.application.options.CrsOptions;
import org.matsim.contrib.analysis.vsp.traveltimedistance.GoogleMapRouteValidator;
import org.matsim.contrib.analysis.vsp.traveltimedistance.HereMapsRouteValidator;
import org.matsim.contrib.analysis.vsp.traveltimedistance.TravelTimeDistanceValidator;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import picocli.CommandLine;

import java.io.FileWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public class NetworkValidation implements MATSimAppCommand {
    @CommandLine.Option(names = "--api", description = "path to network file", defaultValue = "GOOGLE_MAP")
    private API api;

    @CommandLine.Option(names = "--api-key", description = "API key", required = true)
    private String apiKey;

    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--plans", description = "plans to be validated", required = true)
    private String plansPath;

    @CommandLine.Option(names = "--output", description = "output folder for the route calculation", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--data-base", description = "path to the data base", required = true)
    private String dataBase;

    @CommandLine.Option(names = "--date", description = "The date to validate travel times for, format: YYYY-MM-DD")
    private LocalDate date;

    @CommandLine.Option(names = "--max-validation", description = "output folder for the route calculation", defaultValue = "1000")
    private double maxValidations;

    @CommandLine.Mixin
    private CrsOptions crs = new CrsOptions();

    enum API {
        HERE, GOOGLE_MAP, NETWORK_FILE
    }

    private final String mode = "car";

    public static void main(String[] args) {
        new NetworkValidation().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        CoordinateTransformation ct = crs.getTransformation();
        TravelTimeDistanceValidator validator;
        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

        if (api == API.GOOGLE_MAP) {
            if (date == null) {
                date = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
            }
            validator = new GoogleMapRouteValidator(outputPath, mode, apiKey, date.toString(), ct);

        } else if (api == API.HERE) {
            if (date == null) {
                date = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.WEDNESDAY));
            }
            validator = new HereMapsRouteValidator(outputPath, mode, apiKey, date.toString(), ct, false);

        } else {
            throw new RuntimeException("Wrong API used. Allowed values for --api are: GOOGLE_MAP, HERE. Do not use NETWORK_BASED validator in this analysis");
        }

        Population population = PopulationUtils.readPopulation(plansPath);
        Network network = NetworkUtils.readNetwork(networkPath);
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1.0);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, new TimeAsTravelDisutility(travelTime), travelTime);

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputPath + "/network-validation-" + api.toString() + ".tsv"), CSVFormat.TDF);
        tsvWriter.printRecord("trip_id", "from_x", "from_y", "to_x", "to_y", "network_travel_time", "validated_travel_time", "network_travel_distance", "validated_travel_distance");

        NetworkValidatorWithDataStorage validatorWithDataStorage = new NetworkValidatorWithDataStorage(dataBase, validator);

        int validated = 0;
        for (Person person : population.getPersons().values()) {
            int personTripCounter = 0;
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                String mainMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (!mainMode.equals(TransportMode.car) && !mainMode.equals(TransportMode.drt)) {
                    continue;
                }
                String tripId = person.getId().toString() + "_" + personTripCounter;

                Link fromLink;
                if (trip.getOriginActivity().getLinkId() == null){
                    fromLink = NetworkUtils.getNearestLink(network, trip.getOriginActivity().getCoord());
                } else {
                    fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
                }
                Node fromNode = fromLink.getToNode();

                Link toLink;
                if (trip.getDestinationActivity().getLinkId() == null){
                    toLink = NetworkUtils.getNearestLink(network, trip.getDestinationActivity().getCoord());
                } else {
                    toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());
                }
                Node toNode = toLink.getToNode();

                double departureTime = trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new);
//                double departureTime = 3600;   // To test free speed travel time

                Tuple<Double, Double> validatedResult = validatorWithDataStorage.validate(fromNode, toNode);
                LeastCostPathCalculator.Path route = router.calcLeastCostPath(fromLink.getToNode(), toLink.getToNode(), departureTime, null, null);
                double networkTravelDistance = route.links.stream().mapToDouble(Link::getLength).sum();

                tsvWriter.printRecord(tripId, fromNode.getCoord().getX(), fromNode.getCoord().getY(), toNode.getCoord().getX(), toNode.getCoord().getY(),
                        route.travelTime, validatedResult.getFirst(), networkTravelDistance, validatedResult.getSecond());

                validated++;
                personTripCounter++;
            }

            if (validated >= maxValidations) {
                break;
            }
        }
        tsvWriter.close();

        validatorWithDataStorage.writeDownNewEntriesInDataBase();

        return 0;
    }
}
