package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.analysis.vsp.traveltimedistance.TravelTimeDistanceValidator;
import org.matsim.core.utils.collections.Tuple;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NetworkValidatorWithDataStorage {

    private final String dataStorage;
    private final TravelTimeDistanceValidator validator;

    final static String FROM_NODE_ID_STRING = "from_node_id";
    final static String TO_NODE_ID_STRING = "to_node_id";
    private final static String API_TRAVEL_TIME_STRING = "travel_time_from_api";
    private final static String API_TRAVEL_DISTANCE_STRING = "travel_distance_from_api";

    private static final Logger log = LogManager.getLogger(NetworkValidatorWithDataStorage.class);

    /**
     * Currently we use a time-invariant version: from Node, to Node -> travel time read from online API.
     * Next step, a third argument in the key may be added: departure time (binned to some time stamps)
     */
    private final Map<Tuple<String, String>, Tuple<Double, Double>> dataBase = new HashMap<>();
    private final Map<Tuple<String, String>, Tuple<Double, Double>> newEntriesInDataBase = new HashMap<>();

    public NetworkValidatorWithDataStorage(String dataStorage, TravelTimeDistanceValidator validator) throws IOException {
        this.dataStorage = dataStorage;
        this.validator = validator;
        loadPreviouslyCalculatedDataConstant();
    }

    private void loadPreviouslyCalculatedDataConstant() throws IOException {
        Path dataStoragePath = Path.of(dataStorage);
        if (Files.exists(dataStoragePath)) {
            // Load the file and read in data
            log.info("Reading pre-calculated data...");
            try (CSVParser parser = CSVFormat.Builder.create(CSVFormat.TDF).setHeader().setSkipHeaderRecord(true).
                    build().parse(Files.newBufferedReader(dataStoragePath))) {
                for (CSVRecord record : parser.getRecords()) {
                    String fromNodeIdString = record.get(FROM_NODE_ID_STRING);
                    String toNodeIdString = record.get(TO_NODE_ID_STRING);
                    double apiTravelTime = Double.parseDouble(record.get(API_TRAVEL_TIME_STRING));
                    double apiTravelDistance = Double.parseDouble(record.get(API_TRAVEL_DISTANCE_STRING));
                    dataBase.put(new Tuple<>(fromNodeIdString, toNodeIdString), new Tuple<>(apiTravelTime, apiTravelDistance));
                }
            }

        } else {
            // Create the empty file with title
            log.info("Previous calculated data does not exist. Will create a new file");
            CSVPrinter tsvPrinter = new CSVPrinter(new FileWriter(dataStoragePath.toString()), CSVFormat.TDF);
            tsvPrinter.printRecord(FROM_NODE_ID_STRING, TO_NODE_ID_STRING, API_TRAVEL_TIME_STRING, API_TRAVEL_DISTANCE_STRING);
            tsvPrinter.close();
        }
    }

    /**
     * @param fromNode: from Node
     * @param toNode:   to Node
     * @return A tuple consisting travel time (first element of the Tuple) and travel distance (second element of the Tuple) from online API </>
     */
    public Tuple<Double, Double> validate(Node fromNode, Node toNode) throws InterruptedException {
        String fromNodeIdString = fromNode.getId().toString();
        String toNodeIdString = toNode.getId().toString();
        Tuple<String, String> key = new Tuple<>(fromNodeIdString, toNodeIdString);

        if (dataBase.containsKey(key)) {
            // The database already consists this OD-pair, read from the database
            return dataBase.get(key);
        } else {
            // Otherwise, we validate it by calling online API, and store the data in our database
            Coord fromCoord = fromNode.getCoord();
            Coord toCoord = toNode.getCoord();
            // Currently, time-invariant travel time is used. We use 1:00 am to get a near free-speed travel time
            Tuple<Double, Double> dataFromApi = validator.getTravelTime(fromCoord, toCoord, 3600, "null");
            // To avoid sending request to online API too frequently, a short pause is added
            Thread.sleep(100);
            dataBase.put(key, dataFromApi);
            newEntriesInDataBase.put(key, dataFromApi);
            return dataFromApi;
        }
    }

    public void writeDownNewEntriesInDataBase() throws IOException {
        CSVPrinter tsvPrinter = new CSVPrinter(new FileWriter(dataStorage, true), CSVFormat.TDF);
        for (Tuple<String, String> newKey : newEntriesInDataBase.keySet()) {
            String fromNodeIdString = newKey.getFirst();
            String toNodeIdString = newKey.getSecond();
            double travelTime = newEntriesInDataBase.get(newKey).getFirst();
            double travelDistance = newEntriesInDataBase.get(newKey).getSecond();
            tsvPrinter.printRecord(fromNodeIdString, toNodeIdString, Double.toString(travelTime), Double.toString(travelDistance));
        }
        tsvPrinter.close();
    }

    public Map<Tuple<String, String>, Tuple<Double, Double>> getDataBase() {
        return dataBase;
    }
}
