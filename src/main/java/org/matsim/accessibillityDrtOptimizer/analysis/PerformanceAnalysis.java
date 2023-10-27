package org.matsim.accessibillityDrtOptimizer.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.contrib.drt.run.DrtConfigGroup;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.accessibillityDrtOptimizer.accessibility_calculator.AlternativeModeTripData.*;

public class PerformanceAnalysis {
    public static final String FLEET_SIZE = "fleet_size";
    public static final String TOTAL_TRIPS = "total_trips";
    public static final String SATISFACTORY_TRIPS = "satisfactory_trips";
    public static final String SATISFACTORY_RATE = "satisfactory_rate";
    public static final String SYSTEM_TOTAL_TRAVEL_TIME = "system_total_travel_time";
    public static final String NUM_DRT_TRIPS_SERVED = "num_drt_trips_served";
    public static final String DRT_TRIPS_SHARE = "drt_trips_share";
    public static final String DRT_SATISFACTORY_RATE = "drt_satisfactory_rate";
    public static final List<String> KPI_TITLE_ROW =
            Arrays.asList(FLEET_SIZE, TOTAL_TRIPS, SATISFACTORY_TRIPS, SATISFACTORY_RATE,
                    SYSTEM_TOTAL_TRAVEL_TIME, NUM_DRT_TRIPS_SERVED, DRT_TRIPS_SHARE, DRT_SATISFACTORY_RATE);

    private final DrtConfigGroup drtConfigGroup;
    private final String outputSummaryPath;

    private final Map<String, Double> alternativeModeTravelTimeMap = new HashMap<>();
    private final Map<String, Double> tripDirectTravelTimeMap = new HashMap<>();
    private final int numOfTotalTrips;

    public PerformanceAnalysis(DrtConfigGroup drtConfigGroup, String alternativeModeDataPath, String outputSummaryPath) throws IOException {
        this.drtConfigGroup = drtConfigGroup;
        this.outputSummaryPath = outputSummaryPath;

        Path parentPath = Path.of(outputSummaryPath).getParent();
        while (!Files.exists(parentPath)) {
            Files.createDirectories(parentPath);
            parentPath = parentPath.getParent();
        }

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(alternativeModeDataPath)),
                CSVFormat.TDF.withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                String personId = record.get(ID);
                alternativeModeTravelTimeMap.put(personId, Double.parseDouble(record.get(ACTUAL_TOTAL_TRAVEL_TIME)));
                tripDirectTravelTimeMap.put(personId, Double.parseDouble(record.get(DIRECT_CAR_TRAVEL_TIME)));
            }
        }
        numOfTotalTrips = alternativeModeTravelTimeMap.size();
    }

    public void writeTitle() throws IOException {
        CSVPrinter summaryWriter = new CSVPrinter(new FileWriter(outputSummaryPath, false), CSVFormat.TDF);
        summaryWriter.printRecord(KPI_TITLE_ROW);
        summaryWriter.close();
    }

    public void writeDataEntry(String outputFolder, int fleetSize) throws IOException {
        CSVPrinter summaryWriter = new CSVPrinter(new FileWriter(outputSummaryPath, true), CSVFormat.TDF);

        Map<String, Double> systemTotalTravelTimeMap = new HashMap<>(alternativeModeTravelTimeMap);

        int numDrtTrips = 0;
        int satisfactoryDrtTrips = 0;
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(outputFolder + "/output_drt_legs_drt.csv")),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                String personId = record.get("personId");
                double arrivalTime = Double.parseDouble(record.get("arrivalTime"));
                double latestArrivalTime = Double.parseDouble(record.get("latestArrivalTime"));
                double departureTime = Double.parseDouble(record.get("departureTime"));
                double totalTravelTime = arrivalTime - departureTime;
                // override with DRT data
                systemTotalTravelTimeMap.put(personId, totalTravelTime);
                numDrtTrips++;
                if (arrivalTime <= latestArrivalTime) {
                    satisfactoryDrtTrips++;
                }
            }
        }

        int satisfactoryTrips = 0;
        for (String personId : systemTotalTravelTimeMap.keySet()) {
            double directTravelTime = tripDirectTravelTimeMap.get(personId);
            double maxTravelTime = drtConfigGroup.maxTravelTimeAlpha * directTravelTime + drtConfigGroup.maxTravelTimeBeta;
            double actualTravelTime = systemTotalTravelTimeMap.get(personId);
            if (actualTravelTime <= maxTravelTime) {
                satisfactoryTrips++;
            }
        }

        double satisfactoryRate = (double) satisfactoryTrips / numOfTotalTrips;
        double systemTotalTravelTime = systemTotalTravelTimeMap.values().stream().mapToDouble(t -> t).sum();
        double drtTripsShare = (double) numDrtTrips / numOfTotalTrips;
        double drtSatisfactoryRate = (double) satisfactoryDrtTrips / numDrtTrips;

        List<String> outputRow = Arrays.asList(
                Integer.toString(fleetSize),
                Integer.toString(numOfTotalTrips),
                Integer.toString(satisfactoryTrips),
                Double.toString(satisfactoryRate),
                Double.toString(systemTotalTravelTime),
                Integer.toString(numDrtTrips),
                Double.toString(drtTripsShare),
                Double.toString(drtSatisfactoryRate)
        );
        summaryWriter.printRecord(outputRow);
        summaryWriter.close();
    }
}
