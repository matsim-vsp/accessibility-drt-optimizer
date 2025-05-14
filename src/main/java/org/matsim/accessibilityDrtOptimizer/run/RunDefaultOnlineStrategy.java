package org.matsim.accessibilityDrtOptimizer.run;

import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.drt.analysis.afterSimAnalysis.DrtVehicleStoppingTaskWriter;
import org.matsim.contrib.drt.analysis.zonal.DrtModeZonalSystemModule;
import org.matsim.contrib.drt.extension.preplanned.optimizer.WaitForStopTask;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import picocli.CommandLine;

import java.nio.file.Path;

/**
 * Use the current DRT online insertion search strategy. The performance and the speed of the new strategy *
 * can be compared to this benchmark *
 */
public class RunDefaultOnlineStrategy implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "path to config file", required = true)
    private String configPath;

    @CommandLine.Option(names = "--output", description = "path to output directory", required = true)
    private String outputDirectory;

    public static void main(String[] args) {
        new RunDefaultOnlineStrategy().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        config.controller().setOutputDirectory(outputDirectory);

        Controler controler = DrtControlerCreator.createControler(config, false);

        // Add custom module
        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            controler.addOverridingModule(new DvrpModule(new DrtModeZonalSystemModule(drtCfg)));
        }
        controler.run();

        // Plot DRT stopping tasks
        new DrtVehicleStoppingTaskWriter(Path.of(outputDirectory)).addingCustomizedTaskToAnalyze(WaitForStopTask.TYPE).run(WaitForStopTask.TYPE);

        return 0;
    }
}
