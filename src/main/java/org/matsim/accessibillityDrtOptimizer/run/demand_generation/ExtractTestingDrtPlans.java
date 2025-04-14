package org.matsim.accessibillityDrtOptimizer.run.demand_generation;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ExtractTestingDrtPlans implements MATSimAppCommand {
    @CommandLine.Option(names = "--input", description = "path to input plans",
            defaultValue = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input/berlin-v6.4-10pct.plans.xml.gz")
    private String inputPlansPath;

    @CommandLine.Option(names = "--network", description = "path to input network file",
            defaultValue = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input/berlin-v6.4-network-with-pt.xml.gz")
    private String inputNetworkPath;

    @CommandLine.Option(names = "--service-area", description = "path to input plans",
            defaultValue = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input/shp/Berlin_25832.shp")
    private String serviceAreaPath;

    @CommandLine.Option(names = "--min-dist", description = "minimum euclidean distance for trip to be considered", defaultValue = "500")
    private double minEuclideanDistance;

    @CommandLine.Option(names = "--pct-to-keep", description = "keep x percent of suitable trip", defaultValue = "0.05")
    private double pctToKeep;

    @CommandLine.Option(names = "--output", description = "path to output drt plans", required = true)
    private String outputDrtPlansPath;

    @CommandLine.Option(names = "--seed", description = "Random seed", defaultValue = "1")
    private long seed;


    public static void main(String[] args) {
        new ExtractTestingDrtPlans().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Random random = new Random(seed);
        Population inputPlans = PopulationUtils.readPopulation(inputPlansPath);
        ShpOptions shp = new ShpOptions(serviceAreaPath, null, null);
        Geometry serviceArea = shp.getGeometry();

        Network network = NetworkUtils.readNetwork(inputNetworkPath);
        extractDrtNetwork(network, serviceArea);

        Population outputPlans = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        PopulationFactory populationFactory = outputPlans.getFactory();

        int counter = 0;
        for (Person person : inputPlans.getPersons().values()) {

            if (person.getAttributes().getAttribute("subpopulation") != null && person.getAttributes().getAttribute("subpopulation").toString().equals("freight")) {
                continue;
            }

            Plan selectedPlan = person.getSelectedPlan();
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(selectedPlan);
            for (TripStructureUtils.Trip trip : trips) {
                Coord fromCoord = trip.getOriginActivity().getCoord();
                Coord toCoord = trip.getDestinationActivity().getCoord();
                if (fromCoord == null || toCoord == null) {
                    continue;
                }

                if (MGC.coord2Point(fromCoord).within(serviceArea) && MGC.coord2Point(toCoord).within(serviceArea)) {
                    if (CoordUtils.calcEuclideanDistance(fromCoord, toCoord) > minEuclideanDistance) {
                        if (random.nextDouble() < pctToKeep) {
                            Link fromLink = NetworkUtils.getNearestLink(network, fromCoord);
                            Link toLink = NetworkUtils.getNearestLink(network, toCoord);
                            Person drtPerson = populationFactory.createPerson(Id.createPersonId("drt-passenger-" + counter));
                            PersonUtils.setAge(drtPerson, PersonUtils.getAge(person));
                            drtPerson.getAttributes().putAttribute("original_id", person.getId().toString());
                            Plan plan = populationFactory.createPlan();
                            Activity fromAct = populationFactory.createActivityFromLinkId("dummy", fromLink.getId());
                            fromAct.setEndTime(trip.getOriginActivity().getEndTime().orElse(random.nextInt(86400)));
                            plan.addActivity(fromAct);

                            Leg leg = populationFactory.createLeg(TransportMode.drt);
                            plan.addLeg(leg);

                            Activity toAct = populationFactory.createActivityFromLinkId("dummy", toLink.getId());
                            plan.addActivity(toAct);

                            drtPerson.addPlan(plan);
                            outputPlans.addPerson(drtPerson);
                            counter++;
                        }
                    }
                }
            }
        }
        new PopulationWriter(outputPlans).write(outputDrtPlansPath);

        return 0;
    }

    private void extractDrtNetwork(Network network, Geometry serviceArea) {
        List<Link> linksToRemove = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
            if (!link.getAllowedModes().contains(TransportMode.car)) {
                linksToRemove.add(link);
                continue;
            }
            if (!MGC.coord2Point(link.getToNode().getCoord()).within(serviceArea)) {
                linksToRemove.add(link);
            }
        }
        for (Link linkToRemove : linksToRemove) {
            network.removeLink(linkToRemove.getId());
        }
        new NetworkCleaner().run(network);
    }
}
