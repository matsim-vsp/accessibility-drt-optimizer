package org.matsim.accessibilityDrtOptimizer.run;

import org.matsim.accessibilityDrtOptimizer.run.modules.AccessibilityModule;
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

@Deprecated
public class RunSimpleRejectionOptimizer implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "path to config file", required = true)
    private String configPath;

    @CommandLine.Option(names = "--output", description = "path to output directory", required = true)
    private String outputDirectory;

    @CommandLine.Option(names = "--time-varying", description = "enable time varying threshold", defaultValue = "false")
    private boolean timeVarying;

    @CommandLine.Option(names = "--threshold", description = "reject DRT demand if alternative mode is below " +
            "this ratio of the maximum travel time of this DRT request", defaultValue = "0.8")
    private double threshold;

    public static void main(String[] args) {
        new RunSimpleRejectionOptimizer().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        // Record the starting time
        long startTime = System.currentTimeMillis();

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        config.transit().setUseTransit(true);
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        config.controller().setOutputDirectory(outputDirectory);

        Controler controler = DrtControlerCreator.createControler(config, false);
        controler.addOverridingModule(new DvrpModule(new DvrpBenchmarkTravelTimeModule()));

        // Add mode module
        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            controler.addOverridingModule(new DvrpModule(new DrtModeZonalSystemModule(drtCfg)));
//            controler.addOverridingModule(new LinearStopDurationModule(drtCfg));
            controler.addOverridingQSimModule(new AccessibilityModule(drtCfg, threshold, timeVarying));
        }
        controler.run();

        // Plot DRT stopping tasks
        new DrtVehicleStoppingTaskWriter(Path.of(outputDirectory)).addingCustomizedTaskToAnalyze(WaitForStopTask.TYPE).run(WaitForStopTask.TYPE);

        return 0;
    }
}
