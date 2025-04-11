package network_calibration;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.network.NetworkUtils;
import picocli.CommandLine;

public class CompareNetwork implements MATSimAppCommand {

    @CommandLine.Option(names = "--before", description = "path to network file before calibration", required = true)
    private String before;

    @CommandLine.Option(names = "--after", description = "path to network file after calibration", required = true)
    private String after;

    public static void main(String[] args) {
        new CompareNetwork().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Network networkBefore = NetworkUtils.readNetwork(before);
        Network networkAfter = NetworkUtils.readNetwork(after);

        for (Link link : networkAfter.getLinks().values()) {
            double speedBefore = networkBefore.getLinks().get(link.getId()).getFreespeed();
            double speedAfter = link.getFreespeed();
            double delta = (speedAfter - speedBefore) / speedBefore;
            link.getAttributes().putAttribute("delta", delta);
        }

        new NetworkWriter(networkAfter).write(after);
        return 0;
    }
}
