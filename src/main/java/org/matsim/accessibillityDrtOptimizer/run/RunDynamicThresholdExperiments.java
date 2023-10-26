package org.matsim.accessibillityDrtOptimizer.run;

import com.google.common.base.Preconditions;
import org.matsim.accessibillityDrtOptimizer.run.modules.LinearStopDurationModule;
import org.matsim.api.core.v01.Scenario;
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
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

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
    private double timeBinSize;
    
    @Override
    public Integer call() throws Exception {
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

        for (int fleetSize = fleetFrom; fleetSize <= fleetMax; fleetSize += fleetInterval) {
            // Start outer iterations
            for (int i = 0; i < outerIterations; i++) {
                String outputFolder = outputRootDirectory + "/" + fleetSize + "-veh/iter-" + i;

                Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
                MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
                config.controler().setOutputDirectory(outputFolder);
                config.plans().setInputFile(temporaryPopulationPath);

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
                new DrtVehicleStoppingTaskWriter(Path.of(outputFolder)).
                        addingCustomizedTaskToAnalyze(WaitForStopTask.TYPE).run(WaitForStopTask.TYPE);

                // Analyze and update temp population
                if (i != outerIterations - 1) {
                    // Process population file based on output
                    Population tempPopulation = processPlan(rawPopulation, outputFolder);
                    // Overwrite old temp population with new temp population
                    new PopulationWriter(tempPopulation).write(temporaryPopulationPath);
                }
            }
        }

        // Perform analysis

        return 0;
    }

    private Population processPlan(Population rawPopulation, String outputFolder) {
        //TODO
        return null;
    }
}
