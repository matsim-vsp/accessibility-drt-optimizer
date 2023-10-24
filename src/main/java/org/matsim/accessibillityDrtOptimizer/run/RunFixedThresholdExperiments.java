package org.matsim.accessibillityDrtOptimizer.run;

import com.google.common.base.Preconditions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.accessibillityDrtOptimizer.run.modules.LinearStopDurationModule;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.drt.analysis.afterSimAnalysis.DrtVehicleStoppingTaskWriter;
import org.matsim.contrib.drt.analysis.zonal.DrtModeZonalSystemModule;
import org.matsim.contrib.drt.extension.preplanned.optimizer.WaitForStopTask;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.benchmark.DvrpBenchmarkTravelTimeModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import picocli.CommandLine;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunFixedThresholdExperiments implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "path to config file", required = true)
    private String configPath;

    @CommandLine.Option(names = "--output", description = "output root directory", required = true)
    private String outputDirectory;

    @CommandLine.Option(names = "--fleet-sizing", description = "a triplet: [from max interval]. ", arity = "1..*", defaultValue = "300 600 10")
    private List<Integer> fleetSizing;

    @CommandLine.Option(names = "--threshold", description = "Threshold. Choose from [0.0 0.2 0.4 0.6 0.8 1.0]", required = true)
    private String threshold;

    @CommandLine.Option(names = "--alternative-data", description = "path to alternative mode data", required = true)
    private Path alternativeDataPath;

    public static void main(String[] args) {
        new RunFixedThresholdExperiments().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Preconditions.checkArgument(fleetSizing.size() == 3);
        int fleetFrom = fleetSizing.get(0);
        int fleetMax = fleetSizing.get(1);
        int fleetInterval = fleetSizing.get(2);

        // Run simulations
        for (int fleetSize = fleetFrom; fleetSize <= fleetMax; fleetSize += fleetInterval) {
            Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
            MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
            config.controler().setOutputDirectory(outputDirectory + "/" + fleetSize + "-veh");
            config.plans().setInputFile("plans/threshold-" + threshold + ".plans.xml.gz");

            for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
                drtCfg.vehiclesFile = "./vehicles/" + fleetSize + "-8_seater-drt-vehicles.xml";
            }

            Controler controler = DrtControlerCreator.createControler(config, false);
            controler.addOverridingModule(new DvrpModule(new DvrpBenchmarkTravelTimeModule()));

            // Add mode module
            for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
                controler.addOverridingModule(new LinearStopDurationModule(drtCfg));
                controler.addOverridingModule(new DvrpModule(new DrtModeZonalSystemModule(drtCfg)));
            }
            controler.run();

            // Plot DRT stopping tasks
            new DrtVehicleStoppingTaskWriter(Path.of(outputDirectory + "/" + fleetSize + "-veh")).addingCustomizedTaskToAnalyze(WaitForStopTask.TYPE).run(WaitForStopTask.TYPE);
        }

        // Perform analysis
        // Read alternative mode data
        Map<String, Double> backupTravelTimeMap = new HashMap<>();
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(alternativeDataPath),
                CSVFormat.TDF.withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                String personId = record.get("id");
                backupTravelTimeMap.put(personId, Double.parseDouble(record.get("total_travel_time")));
            }
        }
        int totalTrips = backupTravelTimeMap.size();

        String alternativeModeDataOutput = outputDirectory + "/summary.tsv";
        List<String> titleRow = Arrays.asList("fleet_size", "num_late_arrivals", "late_arrivals_rate", "system_total_travel_time", "drt_trips", "service_rate");
        CSVPrinter summaryWriter = new CSVPrinter(new FileWriter(alternativeModeDataOutput), CSVFormat.TDF);
        summaryWriter.printRecord(titleRow);

        for (int fleetSize = fleetFrom; fleetSize <= fleetMax; fleetSize += fleetInterval) {
            String directory = outputDirectory + "/" + fleetSize + "-veh";
            Map<String, Double> systemTotalTravelTimeMap = new HashMap<>(backupTravelTimeMap);
            int lateArrivals = 0;
            int requestsServed = 0;

            try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(directory + "/output_drt_legs_drt.csv")),
                    CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
                for (CSVRecord record : parser.getRecords()) {
                    String personId = record.get("personId");
                    double arrivalTime = Double.parseDouble(record.get("arrivalTime"));
                    double departureTime = Double.parseDouble(record.get("departureTime"));
                    double latestArrivalTime = Double.parseDouble(record.get("latestArrivalTime"));
                    if (arrivalTime > latestArrivalTime + 60) {
                        //TODO the 60 here is hard coded (i.e., stop duration)
                        lateArrivals++;
                    }

                    double totalTravelTime = arrivalTime - departureTime;
                    // override with DRT data
                    systemTotalTravelTimeMap.put(personId, totalTravelTime);

                    requestsServed++;
                }
            }

            double lateArrivalsRate = (double) lateArrivals / totalTrips;
            double systemTotalTravelTime = systemTotalTravelTimeMap.values().stream().mapToDouble(t -> t).sum();
            double serviceRate = (double) requestsServed / totalTrips;

            List<String> outputRow = Arrays.asList(
                    Integer.toString(fleetSize),
                    Integer.toString(lateArrivals),
                    Double.toString(lateArrivalsRate),
                    Double.toString(systemTotalTravelTime),
                    Integer.toString(requestsServed),
                    Double.toString(serviceRate)
            );
            summaryWriter.printRecord(outputRow);
        }
        summaryWriter.close();

        return 0;
    }
}
