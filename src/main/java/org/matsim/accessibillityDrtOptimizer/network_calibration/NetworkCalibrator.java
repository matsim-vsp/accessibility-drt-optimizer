package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.contrib.analysis.vsp.traveltimedistance.GoogleMapRouteValidator;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.Tuple;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.matsim.accessibillityDrtOptimizer.network_calibration.NetworkValidatorWithDataStorage.FROM_NODE_ID_STRING;
import static org.matsim.accessibillityDrtOptimizer.network_calibration.NetworkValidatorWithDataStorage.TO_NODE_ID_STRING;

public class NetworkCalibrator implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "Path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--output", description = "Path to network file", required = true)
    private String outputNetworkPath;

    @CommandLine.Option(names = "--api-key", description = "Google MAP API key", required = true)
    private String apiKey;

    @CommandLine.Option(names = "--od-pairs", description = "Path to OD pair file", required = true)
    private Path odPairsPath;

    @CommandLine.Option(names = "--data-base", description = "Path to data base (csv / tsv file)", required = true)
    private String dataBase;

    @CommandLine.Option(names = "--iterations", description = "Number of iterations to run", defaultValue = "1")
    private int iterations;

    @CommandLine.Option(names = "--threshold", description = "threshold for an OD pair to be considered too slow/to fast on network", defaultValue = "0.05")
    private double threshold;

    @CommandLine.Option(names = "--cut-off", description = "cut-off line do determine whether a link will be adjusted, range between (0,1)", defaultValue = "0.5")
    private double cutOff;

    @CommandLine.Mixin
    private CrsOptions crs = new CrsOptions();

    private static final Logger log = LogManager.getLogger(NetworkCalibrator.class);

    public static void main(String[] args) {
        new NetworkCalibrator().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkPath);
        LocalDate date = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        GoogleMapRouteValidator googleMapApiReader = new GoogleMapRouteValidator("null", TransportMode.car, apiKey, date.toString(), crs.getTransformation());
        NetworkValidatorWithDataStorage validator = new NetworkValidatorWithDataStorage(dataBase, googleMapApiReader);

        Calibrator calibrator = new Calibrator(network, validator);
        calibrator.readOdPairs();
        calibrator.adjustNetworkAverageSpeed();
        calibrator.calibrate();

        new NetworkWriter(network).write(outputNetworkPath);

        return 0;
    }

    class Calibrator {
        private final Network network;
        private final NetworkValidatorWithDataStorage validator;
        private final List<Tuple<Id<Node>, Id<Node>>> odPairs = new ArrayList<>();
        private final Map<Tuple<Id<Node>, Id<Node>>, LeastCostPathCalculator.Path> pathMap = new HashMap<>();

        Calibrator(Network network, NetworkValidatorWithDataStorage validator) {
            this.network = network;
            this.validator = validator;
        }

        void calibrate() throws InterruptedException {
            for (int iteration = 0; iteration < iterations; iteration++) {
                Map<Tuple<Id<Node>, Id<Node>>, Double> normalizedTravelTimeMap = calculateNormalizedTravelTime();
                log.info("Score (MSE) before iteration #" + iteration + " is " + calculateScore(normalizedTravelTimeMap));

                Map<Id<Link>, MutableInt> counterMap = new HashMap<>();
                Map<Id<Link>, MutableInt> scoreMap = new HashMap<>();

                for (Tuple<Id<Node>, Id<Node>> odPair : normalizedTravelTimeMap.keySet()) {
                    // If travel time on network is too long : contribute to positive score for each link the od pair covers
                    if (normalizedTravelTimeMap.get(odPair) > 1 + threshold) {
                        pathMap.get(odPair).links.forEach(link -> counterMap.computeIfAbsent(link.getId(), v -> new MutableInt()).increment());
                        pathMap.get(odPair).links.forEach(link -> scoreMap.computeIfAbsent(link.getId(), v -> new MutableInt()).increment());
                    }

                    // If travel time on network is too short : contribute to negative score for each link the od pair covers
                    if (normalizedTravelTimeMap.get(odPair) < 1 - threshold) {
                        pathMap.get(odPair).links.forEach(link -> counterMap.computeIfAbsent(link.getId(), v -> new MutableInt()).increment());
                        pathMap.get(odPair).links.forEach(link -> scoreMap.computeIfAbsent(link.getId(), v -> new MutableInt()).decrement());
                    }
                }

                for (Id<Link> linkId : scoreMap.keySet()) {
                    double linkScore = scoreMap.get(linkId).doubleValue() / counterMap.get(linkId).doubleValue();
                    if (linkScore > cutOff) {
                        double originalSpeed = network.getLinks().get(linkId).getFreespeed();
                        double updatedSpeed = originalSpeed * (1 + threshold);
                        network.getLinks().get(linkId).setFreespeed(updatedSpeed);
                    }

                    if (linkScore < cutOff * -1) {
                        double originalSpeed = network.getLinks().get(linkId).getFreespeed();
                        double updatedSpeed = originalSpeed * (1 - threshold);
                        network.getLinks().get(linkId).setFreespeed(updatedSpeed);
                    }
                }
            }

            // print out final score
            Map<Tuple<Id<Node>, Id<Node>>, Double> normalizedTravelTimeMap = calculateNormalizedTravelTime();
            log.info("Score (MSE) after " + iterations + " iterations is " + calculateScore(normalizedTravelTimeMap));
        }

        void adjustNetworkAverageSpeed() throws InterruptedException {
            // factor: network travel time / online api travel time (taking average over all the od pairs)
            double factor = calculateNormalizedTravelTime().values().stream().mapToDouble(v -> v).average().orElseThrow();
            for (Link link : network.getLinks().values()) {
                if (!link.getAllowedModes().contains(TransportMode.car)) {
                    continue;
                }
                double originalFreeSpeed = link.getFreespeed();
                // factor > 1 --> travel time on the network is too long, we need to increase the free speed of network
                // factor < 1 --> travel time on the network is too short, we need to decrease the free speed of network
                double updatedFreeSpeed = originalFreeSpeed * factor;
                link.setFreespeed(updatedFreeSpeed);
            }
        }

        void readOdPairs() throws IOException {
            try (CSVParser parser = CSVFormat.Builder.create(CSVFormat.TDF).setHeader().setSkipHeaderRecord(true).
                    build().parse(Files.newBufferedReader(odPairsPath))) {
                for (CSVRecord record : parser.getRecords()) {
                    String fromNodeIdString = record.get(FROM_NODE_ID_STRING);
                    String toNodeIdString = record.get(TO_NODE_ID_STRING);
                    odPairs.add(new Tuple<>(Id.createNodeId(fromNodeIdString), Id.createNodeId(toNodeIdString)));
                }
            }
        }

        private double calculateScore(Map<Tuple<Id<Node>, Id<Node>>, Double> normalizedTravelTimeMap) {
            return normalizedTravelTimeMap.values().stream().mapToDouble(value -> (value - 1) * (value - 1) * 1e4).average().orElseThrow();
        }

        private Map<Tuple<Id<Node>, Id<Node>>, Double> calculateNormalizedTravelTime() throws InterruptedException {
            TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
            TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
            LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

            Map<Tuple<Id<Node>, Id<Node>>, Double> normalizedTravelTimeMap = new HashMap<>();
            pathMap.clear();

            for (Tuple<Id<Node>, Id<Node>> odPair : odPairs) {
                Node fromNode = network.getNodes().get(odPair.getFirst());
                Node toNode = network.getNodes().get(odPair.getSecond());
                LeastCostPathCalculator.Path route = router.calcLeastCostPath(fromNode, toNode, 0, null, null);
                double networkTravelTime = route.travelTime;
                pathMap.put(odPair, route);
                double apiTravelTime = validator.validate(fromNode, toNode).getFirst();
                normalizedTravelTimeMap.put(odPair, networkTravelTime / apiTravelTime);
            }
            return normalizedTravelTimeMap;
        }
    }

}
