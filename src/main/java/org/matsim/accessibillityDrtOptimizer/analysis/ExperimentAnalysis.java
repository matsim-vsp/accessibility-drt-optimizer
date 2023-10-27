package org.matsim.accessibillityDrtOptimizer.analysis;

import com.google.common.base.Preconditions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import picocli.CommandLine;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.matsim.accessibillityDrtOptimizer.accessibility_calculator.AlternativeModeTripData.ACTUAL_TOTAL_TRAVEL_TIME;
import static org.matsim.accessibillityDrtOptimizer.accessibility_calculator.AlternativeModeTripData.ID;

public class ExperimentAnalysis implements MATSimAppCommand {
    @CommandLine.Option(names = "--output", description = "output root directory", required = true)
    private String outputDirectory;

    @CommandLine.Option(names = "--config", description = "path to input config", required = true)
    private String inputConfig;

    @CommandLine.Option(names = "--fleet-sizing", description = "a triplet: [from max interval]. ", arity = "1..*", defaultValue = "300 600 10")
    private List<Integer> fleetSizing;

    @CommandLine.Option(names = "--alternative-data", description = "path to alternative mode data", required = true)
    private String alternativeDataPath;

    public static void main(String[] args) {
        new ExperimentAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Preconditions.checkArgument(fleetSizing.size() == 3);
        int fleetFrom = fleetSizing.get(0);
        int fleetMax = fleetSizing.get(1);
        int fleetInterval = fleetSizing.get(2);

        Config config = ConfigUtils.loadConfig(inputConfig, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        performAnalysis(outputDirectory, fleetFrom, fleetMax, fleetInterval, alternativeDataPath, config);

        return 0;
    }

    public static void performAnalysis(String outputDirectory, int fleetFrom, int fleetMax, int fleetInterval, String alternativeDataPath, Config config) throws IOException {
        DrtConfigGroup drtConfigGroup = DrtConfigGroup.getSingleModeDrtConfig(config);
        PerformanceAnalysis analyzer = new PerformanceAnalysis(drtConfigGroup, alternativeDataPath, outputDirectory + "/summary.tsv");
        analyzer.writeTitle();

        for (int fleetSize = fleetFrom; fleetSize <= fleetMax; fleetSize += fleetInterval) {
            analyzer.writeDataEntry(outputDirectory + "/" + fleetSize + "-veh", fleetSize);
        }
    }
}
