package org.matsim.accessibillityDrtOptimizer.analysis;

import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.AlternativeModeData;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.DefaultAccessibilityCalculator;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class OverallPerformanceAnalysis implements MATSimAppCommand {
    @CommandLine.Option(names = "--directory", description = "path to output directory", required = true)
    private String directory;

    @CommandLine.Option(names = "--iterations", description = "number of last iteration", defaultValue = "0")
    private String iterationFolder;

    public static void main(String[] args) {
        new OverallPerformanceAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        String outputConfigString = ApplicationUtils.globFile(Path.of(directory), "*output_config.xml*").toString();
        Config config = ConfigUtils.loadConfig(outputConfigString);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        TransitSchedule schedule = scenario.getTransitSchedule();
        Network network = scenario.getNetwork();

        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, null, RaptorUtils.createStaticConfig(config), network, null);
        SwissRailRaptor raptor = new SwissRailRaptor.Builder(data, config).build();

        DefaultAccessibilityCalculator accessibilityCalculator = new DefaultAccessibilityCalculator(raptor);

        Path servedDemandsFile = ApplicationUtils.globFile(Path.of(directory + "/ITERS/it." + iterationFolder), "*drt_legs_drt.csv*");
        Path rejectedDemandsFile = ApplicationUtils.globFile(Path.of(directory + "/ITERS/it." + iterationFolder), "*drt_rejections_drt.csv*");
        Path distanceStatsFile = ApplicationUtils.globFile(Path.of(directory), "*drt_vehicle_stats_drt.csv*");

        // Overall statistics
        int numPersons = 0;
        int tripsServed = 0;
        double totalTravelTime = 0;
        double totalWaitingTime = 0;
        double totalWalkingDistance = 0;
        double totalRevenueDistance = 0;

        // Trip data
        List<String> titleRow = Arrays.asList("trip_id", "departure_time", "from_X", "from_Y", "to_X", "to_Y", "main_mode", "total_travel_time", "total_wait_time", "total_walk_distance");
        CSVPrinter tripsWriter = new CSVPrinter(new FileWriter(directory + "/accessibility-analysis/trips-data.tsv"), CSVFormat.TDF);
        tripsWriter.printRecord(titleRow);

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(rejectedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("time"));
                Link fromLink = network.getLinks().get(Id.createLinkId(record.get("fromLinkId")));
                Link toLink = network.getLinks().get(Id.createLinkId(record.get("toLinkId")));
                AlternativeModeData alternativeModeData = accessibilityCalculator.calculateAlternativeMode(fromLink, toLink, departureTime);
                totalTravelTime += alternativeModeData.totalTravelTime();
                totalWalkingDistance += alternativeModeData.TotalWalkingDistance();
                totalWaitingTime += alternativeModeData.waitingTime();
                numPersons++;

                List<String> outputRow = Arrays.asList(
                        record.get("personId"), record.get("time"),
                        Double.toString(fromLink.getToNode().getCoord().getX()),
                        Double.toString(fromLink.getToNode().getCoord().getY()),
                        Double.toString(toLink.getToNode().getCoord().getX()),
                        Double.toString(toLink.getToNode().getCoord().getY()),
                        alternativeModeData.mode(), Double.toString(alternativeModeData.totalTravelTime()),
                        Double.toString(alternativeModeData.waitingTime()),
                        Double.toString(alternativeModeData.TotalWalkingDistance())
                );
                tripsWriter.printRecord(outputRow);
            }
        }

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(servedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("departureTime"));
                double waitTime = Double.parseDouble(record.get("waitTime"));
                double arrivalTime = Double.parseDouble(record.get("arrivalTime"));
                double travelTime = arrivalTime - departureTime;

                totalTravelTime += travelTime;
                totalWaitingTime += waitTime;
                totalRevenueDistance += Double.parseDouble(record.get("directTravelDistance_m"));
                numPersons++;
                tripsServed++;

                List<String> outputRow = Arrays.asList(
                        record.get("personId"), record.get("departureTime"),
                        record.get("fromX"), record.get("fromY"),
                        record.get("toX"), record.get("toY"),
                        TransportMode.drt,
                        Double.toString(travelTime),
                        Double.toString(waitTime),
                        "0"
                );
                tripsWriter.printRecord(outputRow);
            }
        }
        tripsWriter.close();

        String fleetDistance = "0";
        String fleetSize = "0";
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(servedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                fleetSize = record.get("vehicles");
                fleetDistance = record.get("totalDistance");
            }
        }

        List<String> summaryTitleRow = Arrays.asList("num_of_trips", "fleet_size", "trips_served", "total_travel_time",
                "total_waiting_time", "total_walking_distance", "revenue_distance", "fleet_distance");
        CSVPrinter summaryWriter = new CSVPrinter(new FileWriter(directory + "/accessibility-analysis/summary.tsv"), CSVFormat.TDF);

        summaryWriter.printRecord(summaryTitleRow);
        List<String> outputRow = Arrays.asList(
                Integer.toString(numPersons), fleetSize, Integer.toString(tripsServed), Double.toString(totalTravelTime),
                Double.toString(totalWaitingTime), Double.toString(totalWalkingDistance), Double.toString(totalRevenueDistance), fleetDistance
        );
        summaryWriter.printRecord(outputRow);
        summaryWriter.close();

        return 0;
    }
}
