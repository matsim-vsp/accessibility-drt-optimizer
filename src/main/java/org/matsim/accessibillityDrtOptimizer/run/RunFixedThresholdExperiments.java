package org.matsim.accessibillityDrtOptimizer.run;

import com.google.common.base.Preconditions;
import org.matsim.accessibillityDrtOptimizer.analysis.ExperimentAnalysis;
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

import java.nio.file.Path;
import java.util.List;

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
    private String alternativeDataPath;

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
            config.controller().setOutputDirectory(outputDirectory + "/" + fleetSize + "-veh");
            config.plans().setInputFile("plans/threshold-" + threshold + ".plans.xml.gz");

            for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
                drtCfg.vehiclesFile = "./vehicles/" + fleetSize + "-8_seater-drt-vehicles.xml";
            }

            Controler controler = DrtControlerCreator.createControler(config, false);
            controler.addOverridingModule(new DvrpModule(new DvrpBenchmarkTravelTimeModule()));

            // Add mode module
            for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
                controler.addOverridingModule(new DvrpModule(new DrtModeZonalSystemModule(drtCfg)));
            }
            controler.run();

            // Plot DRT stopping tasks
            new DrtVehicleStoppingTaskWriter(Path.of(outputDirectory + "/" + fleetSize + "-veh")).addingCustomizedTaskToAnalyze(WaitForStopTask.TYPE).run(WaitForStopTask.TYPE);
        }

        // Perform analysis
        {
            Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
            ExperimentAnalysis.performAnalysis(outputDirectory, fleetFrom, fleetMax, fleetInterval, alternativeDataPath, config);
        }

        return 0;
    }
}
