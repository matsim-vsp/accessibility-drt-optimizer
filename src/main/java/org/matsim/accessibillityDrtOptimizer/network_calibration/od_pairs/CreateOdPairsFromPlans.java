package org.matsim.accessibillityDrtOptimizer.network_calibration.od_pairs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import picocli.CommandLine;

import java.io.FileWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.matsim.accessibillityDrtOptimizer.network_calibration.NetworkValidatorBasedOnLocalData.*;

public class CreateOdPairsFromPlans implements MATSimAppCommand {
    @CommandLine.Option(names = "--plans", description = "plans to be validated", required = true)
    private String plansPath;

    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--output", description = "path to output od-pairs file", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--max-od-pairs", description = "min network distance of the trips", defaultValue = "1000000")
    private long maxNumODPairs;

    @CommandLine.Option(names = "--departure-time", description = "departure time (in hour of the day) of the trips", defaultValue = "1")
    private double departureTime;

    @CommandLine.Option(names = "--min-distance", description = "min euclidean distance of the trips", defaultValue = "500")
    private double minEuclideanDistance;

    public static void main(String[] args) {
        new CreateOdPairsFromPlans().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Population plans = PopulationUtils.readPopulation(plansPath);
        Network network = NetworkUtils.readNetwork(networkPath);

        // Only keep the car network
        Set<Link> linksToBeRemoved = new HashSet<>();
        for (Link link : network.getLinks().values()) {
            if (!link.getAllowedModes().contains(TransportMode.car)) {
                linksToBeRemoved.add(link);
            }
        }
        linksToBeRemoved.forEach(link -> network.removeLink(link.getId()));

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputPath), CSVFormat.DEFAULT);
        tsvWriter.printRecord(FROM_NODE, TO_NODE, HOUR);

        int odPairs = 0;
        Set<Tuple<Id<Node>, Id<Node>>> existingOdPairs = new HashSet<>();
        for (Person person : plans.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                Link fromLink;
                if (trip.getOriginActivity().getLinkId() == null) {
                    fromLink = NetworkUtils.getNearestLink(network, trip.getOriginActivity().getCoord());
                } else {
                    fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
                }
                Node fromNode = fromLink.getToNode();

                Link toLink;
                if (trip.getDestinationActivity().getLinkId() == null) {
                    toLink = NetworkUtils.getNearestLink(network, trip.getDestinationActivity().getCoord());
                } else {
                    toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());
                }
                Node toNode = toLink.getToNode();

                Tuple<Id<Node>, Id<Node>> odPair = new Tuple<>(fromNode.getId(), toNode.getId());
                if (existingOdPairs.contains(odPair)) {
                    continue;
                } else {
                    existingOdPairs.add(odPair);
                }

                if (CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord()) < minEuclideanDistance) {
                    continue;
                }

                tsvWriter.printRecord(fromNode.getId().toString(), toNode.getId().toString(), Double.toString(departureTime));
                odPairs++;
            }

            if (odPairs >= maxNumODPairs) {
                break;
            }
        }
        tsvWriter.close();

        return 0;
    }
}
