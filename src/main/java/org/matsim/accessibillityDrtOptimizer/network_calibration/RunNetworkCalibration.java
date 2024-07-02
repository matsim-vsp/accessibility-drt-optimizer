package org.matsim.accessibillityDrtOptimizer.network_calibration;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.contrib.analysis.vsp.traveltimedistance.GoogleMapRouteValidator;
import org.matsim.core.network.NetworkUtils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class RunNetworkCalibration implements MATSimAppCommand {
    @CommandLine.Option(names = "--network", description = "Path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--output", description = "Path to network file", required = true)
    private String outputNetworkPath;

    @CommandLine.Option(names = "--od-pairs", description = "Path to OD pair file (can also be the data base)", required = true)
    private Path odPairsPath;

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
        calibrator.performCalibration(odPairsPath);
        new NetworkWriter(network).write(outputNetworkPath);

        return 0;
    }

}
