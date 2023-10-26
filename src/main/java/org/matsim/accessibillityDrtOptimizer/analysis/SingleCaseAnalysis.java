package org.matsim.accessibillityDrtOptimizer.analysis;

import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.accessibillityDrtOptimizer.accessibility_calculator.AlternativeModeData;
import org.matsim.accessibillityDrtOptimizer.accessibility_calculator.DefaultAccessibilityCalculator;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.opengis.feature.simple.SimpleFeature;
import picocli.CommandLine;

import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtils.createGridFromNetwork;
import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtils.createGridFromNetworkWithinServiceArea;
import static org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem.createFromPreparedGeometries;
import static org.matsim.utils.gis.shp2matsim.ShpGeometryUtils.loadPreparedGeometries;

public class SingleCaseAnalysis implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "config file", required = true)
    private String runConfig;

    @CommandLine.Option(names = "--directory", description = "path to output directory. By default: use output directory in config file", defaultValue = "")
    private String directory;

    @CommandLine.Option(names = "--iterations", description = "number of last iteration", defaultValue = "0")
    private String iterationFolder;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--cell-size", description = "cell size for the analysis", defaultValue = "500")
    private double cellSize;

    private static final Logger log = LogManager.getLogger(SingleCaseAnalysis.class);

    public static void main(String[] args) {
        new SingleCaseAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.loadConfig(runConfig);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        TransitSchedule schedule = scenario.getTransitSchedule();
        Network network = scenario.getNetwork();

        if (directory.equals("")) {
            directory = config.controler().getOutputDirectory();
        }

        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, null, RaptorUtils.createStaticConfig(config), network, null);
        SwissRailRaptor raptor = new SwissRailRaptor.Builder(data, config).build();
        DefaultAccessibilityCalculator accessibilityCalculator = new DefaultAccessibilityCalculator(raptor);

        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        Path servedDemandsFile = ApplicationUtils.globFile(Path.of(directory + "/ITERS/it." + iterationFolder), "*drt_legs_drt.csv*");
        Path rejectedDemandsFile = ApplicationUtils.globFile(Path.of(directory + "/ITERS/it." + iterationFolder), "*drt_rejections_drt.csv*");
        Path distanceStatsFile = ApplicationUtils.globFile(Path.of(directory), "*drt_vehicle_stats_drt.csv*");

        // Create Zonal system from Grid
        List<PreparedGeometry> serviceAreas = null;
        if (shp.isDefined()) {
            URL url = shp.getShapeFile().toUri().toURL();
            serviceAreas = (loadPreparedGeometries(url));
        }
        Map<String, PreparedGeometry> grid;
        if (serviceAreas != null) {
            grid = createGridFromNetworkWithinServiceArea(network, cellSize, serviceAreas);
        } else {
            grid = createGridFromNetwork(network, cellSize);
        }
        DrtZonalSystem zonalSystem = createFromPreparedGeometries(network, grid);
        Map<String, List<Double>> statsMap = new HashMap<>();


        // Overall statistics
        int numPersons = 0;
        int tripsServed = 0;
        double sumTotalTravelTime = 0;
        double totalWalkingDistance = 0;
        double totalRevenueDistance = 0;

        // Trip data
        if (!Files.exists(Path.of(directory + "/accessibility-analysis/"))) {
            Files.createDirectories(Path.of(directory + "/accessibility-analysis/"));
        }

        List<String> titleRow = Arrays.asList("trip_id", "departure_time", "from_X", "from_Y", "to_X", "to_Y", "main_mode", "total_travel_time", "total_walk_distance", "direct_travel_time", "delay_ratio");
        CSVPrinter tripsWriter = new CSVPrinter(new FileWriter(directory + "/accessibility-analysis/trips-data.tsv"), CSVFormat.TDF);
        tripsWriter.printRecord(titleRow);

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(rejectedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("time"));
                Link fromLink = network.getLinks().get(Id.createLinkId(record.get("fromLinkId")));
                Link toLink = network.getLinks().get(Id.createLinkId(record.get("toLinkId")));
                double directCarTravelTime = VrpPaths.calcAndCreatePath(fromLink, toLink, departureTime, router, travelTime).getTravelTime();
                AlternativeModeData alternativeModeData = accessibilityCalculator.calculateAlternativeMode(fromLink, toLink, departureTime);
                double delay = alternativeModeData.totalTravelTime() / directCarTravelTime - 1;
                DrtZone zone = zonalSystem.getZoneForLinkId(fromLink.getId());
                if (zone == null) {
                    log.error("cannot find zone for link " + fromLink.getId().toString());
                } else {
                    statsMap.computeIfAbsent(zone.getId(), l -> new ArrayList<>()).add(delay);
                }

                sumTotalTravelTime += alternativeModeData.totalTravelTime();
                totalWalkingDistance += alternativeModeData.TotalWalkingDistance();
                numPersons++;

                List<String> outputRow = Arrays.asList(
                        record.get("personId"), record.get("time"),
                        Double.toString(fromLink.getToNode().getCoord().getX()),
                        Double.toString(fromLink.getToNode().getCoord().getY()),
                        Double.toString(toLink.getToNode().getCoord().getX()),
                        Double.toString(toLink.getToNode().getCoord().getY()),
                        alternativeModeData.mode(), Double.toString(alternativeModeData.totalTravelTime()),
                        Double.toString(alternativeModeData.TotalWalkingDistance()),
                        Double.toString(directCarTravelTime),
                        Double.toString(delay)
                );
                tripsWriter.printRecord(outputRow);
            }
        }

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(servedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("departureTime"));
                double arrivalTime = Double.parseDouble(record.get("arrivalTime"));
                double journeyTime = arrivalTime - departureTime;
                Link fromLink = network.getLinks().get(Id.createLinkId(record.get("fromLinkId")));
                Link toLink = network.getLinks().get(Id.createLinkId(record.get("toLinkId")));
                double directCarTravelTime = VrpPaths.calcAndCreatePath(fromLink, toLink, departureTime, router, travelTime).getTravelTime();
                double delay = journeyTime / directCarTravelTime - 1;

                DrtZone zone = zonalSystem.getZoneForLinkId(fromLink.getId());
                if (zone == null) {
                    log.error("cannot find zone for link " + fromLink.getId().toString());
                } else {
                    statsMap.computeIfAbsent(zone.getId(), l -> new ArrayList<>()).add(delay);
                }
//                statsMap.computeIfAbsent(Objects.requireNonNull(zonalSystem.getZoneForLinkId(fromLink.getId())).getId(), l -> new ArrayList<>()).add(delay);

                sumTotalTravelTime += journeyTime;
                totalRevenueDistance += Double.parseDouble(record.get("directTravelDistance_m"));
                numPersons++;
                tripsServed++;

                List<String> outputRow = Arrays.asList(
                        record.get("personId"), record.get("departureTime"),
                        record.get("fromX"), record.get("fromY"),
                        record.get("toX"), record.get("toY"),
                        TransportMode.drt,
                        Double.toString(journeyTime),
                        "0",
                        Double.toString(directCarTravelTime),
                        Double.toString(delay)
                );
                tripsWriter.printRecord(outputRow);
            }
        }
        tripsWriter.close();

        String fleetDistance = "0";
        String fleetSize = "0";
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(distanceStatsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                fleetSize = record.get("vehicles");
                fleetDistance = record.get("totalDistance");
            }
        }
        List<String> summaryTitleRow = Arrays.asList("num_of_trips", "fleet_size", "trips_served", "service_rate", "total_travel_time",
                "total_walking_distance", "sum_direct_distance", "fleet_distance", "d_direct/d_total");
        CSVPrinter summaryWriter = new CSVPrinter(new FileWriter(directory + "/accessibility-analysis/summary.tsv"), CSVFormat.TDF);
        summaryWriter.printRecord(summaryTitleRow);

        double serviceRate = (double) tripsServed / numPersons;
        List<String> outputRow = Arrays.asList(
                Integer.toString(numPersons), fleetSize, Integer.toString(tripsServed), Double.toString(serviceRate), Double.toString(sumTotalTravelTime),
                Double.toString(totalWalkingDistance), Double.toString(totalRevenueDistance), fleetDistance,
                Double.toString(totalRevenueDistance / Double.parseDouble(fleetDistance))
        );
        summaryWriter.printRecord(outputRow);
        summaryWriter.close();

        // Write shp file
        String crs = shp.getShapeCrs();
        Collection<SimpleFeature> features = convertGeometriesToSimpleFeatures(crs, zonalSystem, statsMap);
        ShapeFileWriter.writeGeometries(features, directory + "/accessibility-analysis/accessibility_analysis.shp");
        return 0;
    }


    private Collection<SimpleFeature> convertGeometriesToSimpleFeatures(String targetCoordinateSystem, DrtZonalSystem zones, Map<String, List<Double>> statsMap) {
        SimpleFeatureTypeBuilder simpleFeatureBuilder = new SimpleFeatureTypeBuilder();
        try {
            simpleFeatureBuilder.setCRS(MGC.getCRS(targetCoordinateSystem));
        } catch (IllegalArgumentException e) {
            log.warn("Coordinate reference system \""
                    + targetCoordinateSystem
                    + "\" is unknown! ");
        }

        simpleFeatureBuilder.setName("drtZoneFeature");
        // note: column names may not be longer than 10 characters. Otherwise, the name is cut after the 10th character and the value is NULL in QGis
        simpleFeatureBuilder.add("the_geom", Polygon.class);
        simpleFeatureBuilder.add("zoneIid", String.class);
        simpleFeatureBuilder.add("centerX", Double.class);
        simpleFeatureBuilder.add("centerY", Double.class);
        simpleFeatureBuilder.add("nRequests", Integer.class);
        simpleFeatureBuilder.add("avg_delay", Double.class);
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(simpleFeatureBuilder.buildFeatureType());

        Collection<SimpleFeature> features = new ArrayList<>();

        int counter = 0;
        for (DrtZone zone : zones.getZones().values()) {
            Object[] featureAttributes = new Object[6];
            Geometry geometry = zone.getPreparedGeometry() != null ? zone.getPreparedGeometry().getGeometry() : null;
            featureAttributes[0] = geometry;
            featureAttributes[1] = zone.getId();
            featureAttributes[2] = zone.getCentroid().getX();
            featureAttributes[3] = zone.getCentroid().getY();
            List<Double> delays = statsMap.getOrDefault(zone.getId(), new ArrayList<>());
            featureAttributes[4] = delays.size();
            featureAttributes[5] = delays.stream().mapToDouble(v -> v).average().orElse(0);
            counter += delays.size();

            try {
                features.add(builder.buildFeature(zone.getId(), featureAttributes));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        log.info("There are " + counter + " requests, including rejected ones");
        return features;
    }
}
