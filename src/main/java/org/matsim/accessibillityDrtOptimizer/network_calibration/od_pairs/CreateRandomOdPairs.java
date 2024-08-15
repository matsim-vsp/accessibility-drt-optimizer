package org.matsim.accessibillityDrtOptimizer.network_calibration.od_pairs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import picocli.CommandLine;

import java.io.FileWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.matsim.accessibillityDrtOptimizer.network_calibration.NetworkValidatorBasedOnLocalData.*;

public class CreateRandomOdPairs implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--output", description = "path to output od-pairs file", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--max-od-pairs", description = "min network distance of the trips", defaultValue = "5000")
    private long maxNumODPairs;

    @CommandLine.Option(names = "--departure-time", description = "departure time (in hour of the day) of the trips", defaultValue = "1")
    private double departureTime;

    @CommandLine.Option(names = "--min-distance", description = "min euclidean distance of the trips", defaultValue = "500")
    private double minEuclideanDistance;

    @CommandLine.Option(names = "--seed", description = "random seed", defaultValue = "1")
    private long seed;

    public static void main(String[] args) {
        new CreateRandomOdPairs().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Random random = new Random(seed);
        Network network = NetworkUtils.readNetwork(networkPath);
        List<Link> linkList = network.getLinks().values().stream()
                .filter(link -> link.getAllowedModes().contains(TransportMode.car))
                .collect(Collectors.toList());
        int size = linkList.size();
        int generatedOdPairs = 0;

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputPath), CSVFormat.DEFAULT);
        tsvWriter.printRecord(FROM_NODE, TO_NODE, HOUR);
        Set<Tuple<Id<Node>, Id<Node>>> existingOdPairs = new HashSet<>();

        while (generatedOdPairs < maxNumODPairs) {
            Node fromNode = linkList.get(random.nextInt(size)).getToNode();
            Node toNode = linkList.get(random.nextInt(size)).getToNode();

            Tuple<Id<Node>, Id<Node>> odPair = new Tuple<>(fromNode.getId(), toNode.getId());
            if (existingOdPairs.contains(odPair)){
                continue;
            }

            if (fromNode.getId().toString().equals(toNode.getId().toString())) {
                continue;
            }

            double distance = CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord());
            if (distance < minEuclideanDistance) {
                continue;
            }

            tsvWriter.printRecord(fromNode.getId().toString(), toNode.getId().toString(), Double.toString(departureTime));
            existingOdPairs.add(odPair);

            generatedOdPairs++;
        }

        tsvWriter.close();
        return 0;
    }
}
