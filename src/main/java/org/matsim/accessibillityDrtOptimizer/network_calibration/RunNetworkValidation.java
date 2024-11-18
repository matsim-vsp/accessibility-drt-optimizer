package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.accessibillityDrtOptimizer.utils.CsvUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.application.options.CrsOptions;
import org.matsim.application.options.CsvOptions;
import org.matsim.contrib.analysis.vsp.traveltimedistance.GoogleMapRouteValidator;
import org.matsim.contrib.analysis.vsp.traveltimedistance.HereMapsRouteValidator;
import org.matsim.contrib.analysis.vsp.traveltimedistance.TravelTimeDistanceValidator;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;

import static org.matsim.accessibillityDrtOptimizer.network_calibration.NetworkValidatorBasedOnLocalData.*;

public class RunNetworkValidation implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--od-pairs", description = "plans to be validated", required = true)
    private Path odPairsPath;

    @CommandLine.Option(names = "--data-base", description = "path to the data base", required = true)
    private String dataBase;

    @CommandLine.Option(names = "--output", description = "output folder for the route calculation", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--max-validation", description = "max number of validation to perform", defaultValue = "100000")
    private double maxValidations;

    public static void main(String[] args) {
        new RunNetworkValidation().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkPath);
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1.0);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, new TimeAsTravelDisutility(travelTime), travelTime);
        NetworkValidatorBasedOnLocalData networkValidatorBasedOnLocalData = new NetworkValidatorBasedOnLocalData(dataBase);

        if (!Files.exists(Path.of(outputPath))){
            Files.createDirectories(Path.of(outputPath));
        }
        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputPath + "/network-validation-results.tsv"), CSVFormat.TDF);
        tsvWriter.printRecord("trip_id", "from_x", "from_y", "to_x", "to_y", "network_travel_time", "validated_travel_time", "network_travel_distance", "validated_travel_distance");

        int validated = 0;
        try (CSVParser parser = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(CsvUtils.detectDelimiter(odPairsPath.toString())).setHeader().setSkipHeaderRecord(true)
                .build().parse(Files.newBufferedReader(odPairsPath))) {
            for (CSVRecord record : parser.getRecords()) {
                String fromNodeIdString = record.get(FROM_NODE);
                String toNodeIdString = record.get(TO_NODE);
                Node fromNode = network.getNodes().get(Id.createNodeId(fromNodeIdString));
                Node toNode = network.getNodes().get(Id.createNodeId(toNodeIdString));
                double departureTime = Double.parseDouble(record.get(HOUR));
                LeastCostPathCalculator.Path route = router.calcLeastCostPath(fromNode, toNode, departureTime, null, null);
                double networkDistance = route.links.stream().mapToDouble(Link::getLength).sum();

                Tuple<Double, Double> validatedTimeAndDistance = networkValidatorBasedOnLocalData.validate(fromNode, toNode, departureTime);
                List<String> outputRow = Arrays.asList(
                        Integer.toString(validated),
                        Double.toString(fromNode.getCoord().getX()),
                        Double.toString(fromNode.getCoord().getY()),
                        Double.toString(toNode.getCoord().getX()),
                        Double.toString(toNode.getCoord().getY()),
                        Double.toString(route.travelTime),
                        Double.toString(validatedTimeAndDistance.getFirst()),
                        Double.toString(networkDistance),
                        Double.toString(validatedTimeAndDistance.getSecond())
                );

                tsvWriter.printRecord(outputRow);
                validated++;

                if (validated >= maxValidations) {
                    break;
                }
            }
        }

        tsvWriter.close();

        return 0;
    }
}
