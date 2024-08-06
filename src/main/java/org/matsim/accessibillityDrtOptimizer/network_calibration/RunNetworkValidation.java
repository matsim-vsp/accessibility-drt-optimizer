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
    @CommandLine.Option(names = "--api", description = "path to network file", defaultValue = "GOOGLE_MAP")
    private API api;

    @CommandLine.Option(names = "--api-key", description = "API key", required = true)
    private String apiKey;

    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--od-pairs", description = "plans to be validated", required = true)
    private Path odPairsPath;

    @CommandLine.Option(names = "--output", description = "output folder for the route calculation", required = true)
    private String outputPath;

    @CommandLine.Option(names = "--data-base", description = "path to the data base", required = true)
    private String dataBase;

    @CommandLine.Option(names = "--date", description = "The date to validate travel times for, format: YYYY-MM-DD")
    private LocalDate date;

    @CommandLine.Option(names = "--max-validation", description = "max number of validation to perform", defaultValue = "1000")
    private double maxValidations;

    @CommandLine.Mixin
    private CrsOptions crs = new CrsOptions();

    enum API {
        HERE, GOOGLE_MAP, NETWORK_FILE
    }

    private final String mode = "car";

    public static void main(String[] args) {
        new RunNetworkValidation().execute(args);
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

        Network network = NetworkUtils.readNetwork(networkPath);
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1.0);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, new TimeAsTravelDisutility(travelTime), travelTime);
        NetworkValidatorBasedOnLocalData networkValidatorBasedOnLocalData = new NetworkValidatorBasedOnLocalData(dataBase);

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputPath + "/network-validation-" + api.toString() + ".tsv"), CSVFormat.TDF);
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
