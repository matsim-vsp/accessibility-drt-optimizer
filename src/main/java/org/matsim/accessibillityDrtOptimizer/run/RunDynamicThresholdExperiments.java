package org.matsim.accessibillityDrtOptimizer.run;

import com.google.common.base.Preconditions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.accessibillityDrtOptimizer.run.modules.LinearStopDurationModule;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
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
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import picocli.CommandLine;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunDynamicThresholdExperiments implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "path to config file", required = true)
    private String configPath;

    @CommandLine.Option(names = "--output", description = "output root directory", required = true)
    private String outputRootDirectory;

    @CommandLine.Option(names = "--fleet-sizing", description = "a triplet: [from max interval]. ", arity = "1..*", defaultValue = "300 600 10")
    private List<Integer> fleetSizing;

    @CommandLine.Option(names = "--iterations", description = "outer iterations", defaultValue = "20")
    private int outerIterations;

    @CommandLine.Option(names = "--learning-rate", description = "learning rate with exp discount", defaultValue = "0.5")
    private int learningRate;

    @CommandLine.Option(names = "--time-bin-size", description = "time bin size for the travel time analysis", defaultValue = "900")
    private int timeBinSize;

    @CommandLine.Option(names = "--alternative-data", description = "path to alternative mode data", required = true)
    private Path alternativeDataPath;

    private static final Logger log = LogManager.getLogger(RunDynamicThresholdExperiments.class);

    private final Map<Integer, Double> thresholdMap = new HashMap<>();

    private final Map<String, Tuple<Double, Double>> alternativeModeData = new HashMap<>();
    // Tuple: departure time, trip length ratio (against max drt travel time)

    @Override
    public Integer call() throws Exception {
        // Decoding fleet sizing sequence
        Preconditions.checkArgument(fleetSizing.size() == 3);
        int fleetFrom = fleetSizing.get(0);
        int fleetMax = fleetSizing.get(1);
        int fleetInterval = fleetSizing.get(2);

        // Load initial population (i.e., full DRT demands)
        long tempId = System.currentTimeMillis() / 1000;
        Config tempConfig = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Scenario tempScenario = ScenarioUtils.loadScenario(tempConfig);
        Population rawPopulation = tempScenario.getPopulation();
        String temporaryPopulationPath = Path.of(configPath).getParent().toString() + "/temporary-" + tempId + ".plans.xml.gz";
        new PopulationWriter(rawPopulation).write(temporaryPopulationPath);

        // Initialize threshold map
        double simulationEndTime = tempConfig.qsim().getEndTime().orElse(3600 * 30);
        for (int i = 0; i < simulationEndTime; i += timeBinSize) {
            thresholdMap.put(i, 0.0);
        }

        // Read alternative mode data
        log.info("Reading alternative mode data...");
        DrtConfigGroup tempDrtConfigGroup = DrtConfigGroup.getSingleModeDrtConfig(tempConfig);
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(alternativeDataPath),
                CSVFormat.TDF.withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                String personId = record.get("id");
                double departureTime = Double.parseDouble(record.get("departure_time"));
                double alternativeTravelTime = Double.parseDouble(record.get("total_travel_time"));
                double directTravelTime = Double.parseDouble(record.get("direct_travel_time"));
                double ratio = alternativeTravelTime / (tempDrtConfigGroup.maxTravelTimeAlpha * directTravelTime + tempDrtConfigGroup.maxTravelTimeBeta);
                alternativeModeData.put(personId, new Tuple<>(departureTime, ratio));
            }
        }

        for (int fleetSize = fleetFrom; fleetSize <= fleetMax; fleetSize += fleetInterval) {
            // Start outer iterations
            for (int i = 0; i < outerIterations; i++) {
                String outputFolder = outputRootDirectory + "/" + fleetSize + "-veh/iter-" + i;

                Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
                config.controler().setOutputDirectory(outputFolder);
                config.plans().setInputFile(temporaryPopulationPath);

                // Currently we only focus on single DRT mode
                DrtConfigGroup drtConfigGroup = DrtConfigGroup.getSingleModeDrtConfig(config);
                drtConfigGroup.vehiclesFile = "./vehicles/" + fleetSize + "-8_seater-drt-vehicles.xml";

                Controler controler = DrtControlerCreator.createControler(config, false);
                controler.addOverridingModule(new DvrpModule(new DvrpBenchmarkTravelTimeModule()));

                // Add mode module
                controler.addOverridingModule(new DvrpModule(new DrtModeZonalSystemModule(drtConfigGroup)));

                controler.run();

                // Plot DRT stopping tasks
                new DrtVehicleStoppingTaskWriter(Path.of(outputFolder)).
                        addingCustomizedTaskToAnalyze(WaitForStopTask.TYPE).run(WaitForStopTask.TYPE);

                // Analyze and update temp population
                // TODO
                
                if (i != outerIterations - 1) {
                    // Process population file based on output
                    Population tempPopulation = processPlan(rawPopulation, outputFolder);
                    // Overwrite old temp population with new temp population
                    new PopulationWriter(tempPopulation).write(temporaryPopulationPath);
                }
            }
        }

        // Perform overall analysis

        return 0;
    }

    private Population processPlan(Population rawPopulation, String outputFolder) throws IOException {
        log.info("Processing plans...");
        // Initialization
        Map<Integer, List<Double>> tripLengthRatiosPerTimeBinMap = new HashMap<>();

        // Read output trips
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(outputFolder + "/output_drt_legs_drt.csv")),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("departureTime"));
                double arrivalTime = Double.parseDouble(record.get("arrivalTime"));
                double latestArrivalTime = Double.parseDouble(record.get("latestArrivalTime"));

                double actualTravelTime = arrivalTime - departureTime;
                double maxTravelTime = latestArrivalTime - departureTime;
                double ratio = actualTravelTime / maxTravelTime;

                int timeBin = (int) Math.floor(departureTime / timeBinSize) * timeBinSize;
                tripLengthRatiosPerTimeBinMap.computeIfAbsent(timeBin, t -> new ArrayList<>()).add(ratio);
            }
        }

        // Update threshold map
        for (int timeBin : tripLengthRatiosPerTimeBinMap.keySet()) {
            double averageTripLengthRatio =
                    tripLengthRatiosPerTimeBinMap.get(timeBin).stream().mapToDouble(d -> d).average().orElseThrow();
            double previousValue = thresholdMap.get(timeBin);
            double updatedValue = learningRate * averageTripLengthRatio + (1 - learningRate) * previousValue;
            thresholdMap.put(timeBin, updatedValue);
        }

        // Write down current threshold map
        CSVPrinter printer = new CSVPrinter(new FileWriter(outputFolder + "/time-varying-threshold-map.tsv"), CSVFormat.TDF);
        printer.printRecord("time", "threshold");
        for (int timeBin : thresholdMap.keySet()) {
            printer.printRecord(timeBin, thresholdMap.get(timeBin));
        }
        printer.close();

        // Filter plans
        Population filteredPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        for (String personIdString : alternativeModeData.keySet()) {
            double departureTime = alternativeModeData.get(personIdString).getFirst();
            double ratio = alternativeModeData.get(personIdString).getSecond();

            int timeBin = (int) Math.floor(departureTime / timeBinSize) * timeBinSize;
            double threshold = thresholdMap.get(timeBin);
            if (ratio > threshold) {
                filteredPopulation.addPerson(rawPopulation.getPersons().get(Id.createPersonId(personIdString)));
            }
        }

        return filteredPopulation;
    }
}
