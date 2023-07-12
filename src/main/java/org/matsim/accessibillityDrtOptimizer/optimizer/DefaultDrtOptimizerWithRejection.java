package org.matsim.accessibillityDrtOptimizer.optimizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.AccessibilityCalculator;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.AlternativeModeData;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.DrtRequestInsertionRetryQueue;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.depot.Depots;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.RequestQueue;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;

import java.util.List;
import java.util.stream.Stream;

public class DefaultDrtOptimizerWithRejection implements DrtOptimizer {
    private static final Logger log = LogManager.getLogger(DefaultDrtOptimizerWithRejection.class);

    private final DrtConfigGroup drtCfg;
    private final Integer rebalancingInterval;
    private final Fleet fleet;
    private final DrtScheduleInquiry scheduleInquiry;
    private final ScheduleTimingUpdater scheduleTimingUpdater;
    private final RebalancingStrategy rebalancingStrategy;
    private final MobsimTimer mobsimTimer;
    private final DepotFinder depotFinder;
    private final EmptyVehicleRelocator relocator;
    private final UnplannedRequestInserter requestInserter;
    private final DrtRequestInsertionRetryQueue insertionRetryQueue;

    private final AccessibilityCalculator accessibilityCalculator;

    private final TravelTime travelTime;
    private final LeastCostPathCalculator router;

    private final RequestQueue<DrtRequest> unplannedRequests;

    private final double threshold;

    private final EventsManager eventsManager;

    public DefaultDrtOptimizerWithRejection(DrtConfigGroup drtCfg, Fleet fleet, MobsimTimer mobsimTimer, DepotFinder depotFinder,
                                            RebalancingStrategy rebalancingStrategy, DrtScheduleInquiry scheduleInquiry, ScheduleTimingUpdater scheduleTimingUpdater,
                                            EmptyVehicleRelocator relocator, UnplannedRequestInserter requestInserter, DrtRequestInsertionRetryQueue insertionRetryQueue,
                                            AccessibilityCalculator accessibilityCalculator, Network network, TravelTime travelTime, double threshold, EventsManager eventsManager) {
        this.drtCfg = drtCfg;
        this.fleet = fleet;
        this.mobsimTimer = mobsimTimer;
        this.depotFinder = depotFinder;
        this.rebalancingStrategy = rebalancingStrategy;
        this.scheduleInquiry = scheduleInquiry;
        this.scheduleTimingUpdater = scheduleTimingUpdater;
        this.relocator = relocator;
        this.requestInserter = requestInserter;
        this.insertionRetryQueue = insertionRetryQueue;
        this.accessibilityCalculator = accessibilityCalculator;
        this.travelTime = travelTime;
        this.router = new SpeedyALTFactory().createPathCalculator(network, new TimeAsTravelDisutility(travelTime), travelTime);

        rebalancingInterval = drtCfg.getRebalancingParams().map(rebalancingParams -> rebalancingParams.interval).orElse(null);
        unplannedRequests = RequestQueue.withLimitedAdvanceRequestPlanningHorizon(drtCfg.advanceRequestPlanningHorizon);
        this.threshold = threshold;
        this.eventsManager = eventsManager;
    }

    @Override
    public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent e) {
        unplannedRequests.updateQueuesOnNextTimeSteps(e.getSimulationTime());

        boolean scheduleTimingUpdated = false;
        if (!unplannedRequests.getSchedulableRequests().isEmpty() || insertionRetryQueue.hasRequestsToRetryNow(e.getSimulationTime())) {
            for (DvrpVehicle v : fleet.getVehicles().values()) {
                scheduleTimingUpdater.updateTimings(v);
            }
            scheduleTimingUpdated = true;

            requestInserter.scheduleUnplannedRequests(unplannedRequests.getSchedulableRequests());
        }

        if (rebalancingInterval != null && e.getSimulationTime() % rebalancingInterval == 0) {
            if (!scheduleTimingUpdated) {
                for (DvrpVehicle v : fleet.getVehicles().values()) {
                    scheduleTimingUpdater.updateTimings(v);
                }
            }

            rebalanceFleet();
        }
    }

    private void rebalanceFleet() {
        // right now we relocate only idle vehicles (vehicles that are being relocated cannot be relocated)
        Stream<? extends DvrpVehicle> rebalancableVehicles = fleet.getVehicles().values().stream().filter(scheduleInquiry::isIdle);
        List<RebalancingStrategy.Relocation> relocations = rebalancingStrategy.calcRelocations(rebalancableVehicles, mobsimTimer.getTimeOfDay());

        if (!relocations.isEmpty()) {
            log.debug("Fleet rebalancing: #relocations=" + relocations.size());
            for (RebalancingStrategy.Relocation r : relocations) {
                Link currentLink = ((DrtStayTask) r.vehicle.getSchedule().getCurrentTask()).getLink();
                if (currentLink != r.link) {
                    relocator.relocateVehicle(r.vehicle, r.link);
                }
            }
        }
    }

    @Override
    public void requestSubmitted(Request request) {
        double now = mobsimTimer.getTimeOfDay();
        DrtRequest drtRequest = (DrtRequest) request;
        AlternativeModeData alternativeModeData = accessibilityCalculator.calculateAlternativeMode(drtRequest);
        double directTravelTime = VrpPaths.calcAndCreatePath(drtRequest.getFromLink(), drtRequest.getToLink(), now, router, travelTime).getTravelTime();
        double maxTravelTime = drtCfg.maxTravelTimeAlpha * directTravelTime + drtCfg.maxTravelTimeBeta;

        if (alternativeModeData.totalTravelTime() < maxTravelTime * threshold) {
            // Reject this request directly
            eventsManager.processEvent(new PassengerRequestRejectedEvent(mobsimTimer.getTimeOfDay(), drtCfg.mode, request.getId(),
                    drtRequest.getPassengerId(), "Request is rejected because alternative mode is also attractive"));
            log.info("DRT request" + drtRequest.getPassengerId().toString() + " is rejected, because a good alternative mode exists!");
        } else {
            unplannedRequests.addRequest((DrtRequest) request);
        }
    }

    @Override
    public void nextTask(DvrpVehicle vehicle) {
        scheduleTimingUpdater.updateBeforeNextTask(vehicle);
        vehicle.getSchedule().nextTask();

        // if STOP->STAY then choose the best depot
        if (drtCfg.idleVehiclesReturnToDepots && Depots.isSwitchingFromStopToStay(vehicle)) {
            Link depotLink = depotFinder.findDepot(vehicle);
            if (depotLink != null) {
                relocator.relocateVehicle(vehicle, depotLink);
            }
        }
    }

}
