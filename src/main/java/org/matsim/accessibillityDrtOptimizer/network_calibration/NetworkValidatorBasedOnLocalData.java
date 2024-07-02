package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.Tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NetworkValidatorBasedOnLocalData {
    final static String FROM_NODE = "from_node";
    final static String TO_NODE = "to_node";
    /**
     * Departure time of the trip in the online API
     */
    final static String HOUR = "hour";
    private final static String TRAVEL_TIME = "travel_time";
    private final static String DISTANCE = "dist";

    private static final Logger log = LogManager.getLogger(NetworkValidatorBasedOnLocalData.class);

    /**
     * Currently we use a time-invariant version: from Node, to Node -> travel time read from online API.
     * Next step, a third argument in the key may be added: departure time (binned to some time stamps)
     */
    private final Map<Tuple<String, String>, Tuple<Double, Double>> dataBase = new HashMap<>();

    public NetworkValidatorBasedOnLocalData(String localDatabase) throws IOException {
        loadLocalDataBase(localDatabase);
    }

    private void loadLocalDataBase(String localDatabase) throws IOException {
        Path dataStoragePath = Path.of(localDatabase);
        if (Files.exists(dataStoragePath)) {
            // Load the file and read in data
            log.info("Reading local database");
            try (CSVParser parser = CSVFormat.Builder.create(CSVFormat.TDF).setHeader().setSkipHeaderRecord(true).
                    build().parse(Files.newBufferedReader(dataStoragePath))) {
                for (CSVRecord record : parser.getRecords()) {
                    String fromNodeIdString = record.get(FROM_NODE);
                    String toNodeIdString = record.get(TO_NODE);
                    double apiTravelTime = Double.parseDouble(record.get(TRAVEL_TIME));
                    double apiTravelDistance = Double.parseDouble(record.get(DISTANCE));
                    dataBase.put(new Tuple<>(fromNodeIdString, toNodeIdString), new Tuple<>(apiTravelTime, apiTravelDistance));
                }
            }
        } else {
            throw new RuntimeException("Database does not exist, please generate local database first! Aborting...");
        }
    }

    /**
     * @param fromNode: from Node
     * @param toNode:   to Node
     * @param departureTime: departure time of the trip (in the unit of hour). Currently unused.
     * @return A tuple consisting travel time (first element of the Tuple) and travel distance (second element of the Tuple) from online API </>
     */
    public Tuple<Double, Double> validate(Node fromNode, Node toNode, double departureTime) {
        String fromNodeIdString = fromNode.getId().toString();
        String toNodeIdString = toNode.getId().toString();
        Tuple<String, String> key = new Tuple<>(fromNodeIdString, toNodeIdString);

        if (dataBase.containsKey(key)) {
            // The database already consists this OD-pair, read from the database
            return dataBase.get(key);
        } else {
            throw new RuntimeException("Local database does not contain this OD pair: from node = " +
                    key.getFirst() + " to node = " + key.getSecond() + ". Please update the local database!");
        }
    }
}
