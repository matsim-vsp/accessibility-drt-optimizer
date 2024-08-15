package org.matsim.accessibillityDrtOptimizer.network_calibration.od_pairs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.mutable.MutableInt;
import org.matsim.accessibillityDrtOptimizer.utils.CsvUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.matsim.accessibillityDrtOptimizer.network_calibration.NetworkValidatorBasedOnLocalData.*;

public class OdPairCoverageAnalysis implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--od-pairs", description = "OD pairs used for training or validation", required = true)
    private Path odPairsPath;

    @CommandLine.Option(names = "--output", description = "output folder for the coverage analysis", required = true)
    private String outputPath;

    public static void main(String[] args) {
        new OdPairCoverageAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkPath);
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility disutility = new TimeAsTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, disutility, travelTime);

        Map<Id<Link>, MutableInt> linksCounterMap = new HashMap<>();
        // Initialize the counter map
        for (Id<Link> linkId : network.getLinks().keySet()) {
            if (network.getLinks().get(linkId).getAllowedModes().contains(TransportMode.car)) {
                linksCounterMap.put(linkId, new MutableInt());
            }
        }

        // go through od pairs and analyze the link coverage
        try (CSVParser parser = CSVFormat.Builder.create(CSVFormat.DEFAULT).
                setDelimiter(CsvUtils.detectDelimiter(odPairsPath.toString())).setHeader().setSkipHeaderRecord(true).
                build().parse(Files.newBufferedReader(odPairsPath))) {
            for (CSVRecord record : parser.getRecords()) {
                String fromNodeIdString = record.get(FROM_NODE);
                String toNodeIdString = record.get(TO_NODE);
                Node fromNode = network.getNodes().get(Id.createNodeId(fromNodeIdString));
                Node toNode = network.getNodes().get(Id.createNodeId(toNodeIdString));
                double departureTime = Double.parseDouble(record.get(HOUR));
                LeastCostPathCalculator.Path route = router.calcLeastCostPath(fromNode, toNode, departureTime, null, null);
                for (Link link : route.links) {
                    linksCounterMap.get(link.getId()).increment();
                }
            }
        }

        // Write an additional attribute to each link: coverage
        int atLeastOnce = 0;
        int atLeast3x = 0;
        int atLeast5x = 0;
        for (Id<Link> linkId : linksCounterMap.keySet()) {
            int coverage = linksCounterMap.get(linkId).intValue();
            if (coverage >= 5) {
                atLeast5x++;
            }
            if (coverage >= 3) {
                atLeast3x++;
            }
            if (coverage >= 1) {
                atLeastOnce++;
            }
            network.getLinks().get(linkId).getAttributes().putAttribute("coverage", coverage);
        }

        // write output analysis and output network
        if (!Files.exists(Path.of(outputPath))) {
            Files.createDirectories(Path.of(outputPath));
        }

        double atLeastOnceRatio = (double) atLeastOnce / (double) linksCounterMap.size();
        double atLeast3xRatio = (double) atLeast3x / (double) linksCounterMap.size();
        double atLeast5xRatio = (double) atLeast5x / (double) linksCounterMap.size();

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputPath + "/stats.tsv"), CSVFormat.TDF);
        tsvWriter.printRecord("at_least_once", "at_least_3x", "at_least_5x");
        tsvWriter.printRecord(Double.toString(atLeastOnceRatio), Double.toString(atLeast3xRatio), Double.toString(atLeast5xRatio));
        tsvWriter.close();

        new NetworkWriter(network).write(outputPath + "/network-with-coverage.xml.gz");

        return 0;
    }
}
