package network_calibration;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.core.network.NetworkUtils;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class RunNetworkCalibration implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "Path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--output", description = "Path to output folder", required = true)
    private String outputfolder;

    @CommandLine.Option(names = "--od-pairs", description = "Path to OD pair file (can also be the data base)", required = true)
    private Path odPairsPath;

    @CommandLine.Option(names = "--max-od-pairs-used", description = "At most top x of od pairs from the od pair files are" +
            " used for calibration", defaultValue = "100000")
    private int maxOdPairsUsed;

    @CommandLine.Option(names = "--data-base", description = "Path to local data base (csv / tsv file)", required = true)
    private String dataBase;

    @CommandLine.Option(names = "--iterations", description = "Number of iterations to run", defaultValue = "1")
    private int iterations;

    @CommandLine.Option(names = "--threshold", description = "threshold for an OD pair to be considered too slow/to fast on network", defaultValue = "0.05")
    private double threshold;

    @CommandLine.Option(names = "--cut-off", description = "cut-off line do determine whether a link will be adjusted, range between (0,1)", defaultValue = "0.1")
    private double cutOff;

    @CommandLine.Option(names = "--departure-time", description = "departure time of the trips (in hours)", defaultValue = "1")
    private double departureTime;

    @CommandLine.Mixin
    private CrsOptions crs = new CrsOptions();

    public static void main(String[] args) {
        new RunNetworkCalibration().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network network = NetworkUtils.readNetwork(networkPath);
        NetworkValidatorBasedOnLocalData validator = new NetworkValidatorBasedOnLocalData(dataBase);

        NetworkCalibrator calibrator = new NetworkCalibrator.Builder(network, validator)
                .setIterations(iterations).setCutOff(cutOff).setThreshold(threshold).setDepartureTime(departureTime)
                .build();
        calibrator.performCalibration(odPairsPath, maxOdPairsUsed);
        Map<Integer, Double> scores = calibrator.getScores();

        // write calibrated network and score records
        if (!Files.exists(Path.of(outputfolder))) {
            Files.createDirectories(Path.of(outputfolder));
        }
        new NetworkWriter(network).write(outputfolder + "/calibrated-network.xml.gz");

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputfolder + "/scores-records.tsv"), CSVFormat.TDF);
        tsvWriter.printRecord("iteration", "score");
        for (Map.Entry<Integer, Double> entry : scores.entrySet()) {
            tsvWriter.printRecord(entry.getKey(), entry.getValue());
        }
        tsvWriter.close();

        return 0;
    }

}
