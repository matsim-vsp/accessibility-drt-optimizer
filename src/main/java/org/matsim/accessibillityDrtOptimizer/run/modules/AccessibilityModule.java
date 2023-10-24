package org.matsim.accessibillityDrtOptimizer.run.modules;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import org.matsim.accessibillityDrtOptimizer.accessibility_calculator.AccessibilityCalculator;
import org.matsim.accessibillityDrtOptimizer.accessibility_calculator.DefaultAccessibilityCalculator;
import org.matsim.accessibillityDrtOptimizer.optimizer.DefaultDrtOptimizerWithRejection;
import org.matsim.accessibillityDrtOptimizer.optimizer.TimeVaryingRejectionThreshold;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.DrtRequestInsertionRetryQueue;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.util.TravelTime;

public class AccessibilityModule extends AbstractDvrpModeQSimModule {

    private final DrtConfigGroup drtConfigGroup;
    private final double threshold;
    private final boolean timeVarying;

    public AccessibilityModule(DrtConfigGroup drtConfigGroup, double threshold, boolean timeVarying) {
        super(drtConfigGroup.mode);
        this.drtConfigGroup = drtConfigGroup;
        this.threshold = threshold;
        this.timeVarying = timeVarying;
    }

    @Override
    protected void configureQSim() {
        addModalComponent(DrtOptimizer.class, modalProvider(
                getter -> new DefaultDrtOptimizerWithRejection(drtConfigGroup, getter.getModal(Fleet.class), getter.get(MobsimTimer.class),
                        getter.getModal(DepotFinder.class), getter.getModal(RebalancingStrategy.class),
                        getter.getModal(DrtScheduleInquiry.class), getter.getModal(ScheduleTimingUpdater.class),
                        getter.getModal(EmptyVehicleRelocator.class), getter.getModal(UnplannedRequestInserter.class),
                        getter.getModal(DrtRequestInsertionRetryQueue.class), getter.getModal(AccessibilityCalculator.class),
                        getter.getModal(Network.class), getter.getModal(TravelTime.class), threshold, getter.get(EventsManager.class),
                        getter.getModal(TimeVaryingRejectionThreshold.class))));

        bindModal(TimeVaryingRejectionThreshold.class).toProvider(modalProvider(
                getter -> new TimeVaryingRejectionThreshold(getter.getModal(Fleet.class),
                        getter.getModal(DrtScheduleInquiry.class), timeVarying)
        ));


        bindModal(AccessibilityCalculator.class).toProvider(modalProvider(
                getter -> new DefaultAccessibilityCalculator(getter.get(SwissRailRaptor.class))
        ));

        //        addModalComponent(DrtOptimizer.class, this.modalProvider((getter) -> new SimpleRejectionDrtOptimizer(getter.getModal(Network.class),
//                getter.getModal(TravelTime.class), getter.get(MobsimTimer.class), getter.getModal(DrtTaskFactory.class),
//                getter.get(EventsManager.class), getter.getModal(ScheduleTimingUpdater.class),
//                getter.getModal(TravelDisutilityFactory.class).createTravelDisutility(getter.getModal(TravelTime.class)),
//                drtConfigGroup, getter.getModal(Fleet.class),
//                getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool(),
//                getter.getModal(VehicleEntry.EntryFactory.class),
//                getter.getModal(OnlineSolverBasicInsertionStrategy.class),
//                getter.getModal(AccessibilityCalculator.class), threshold)));
//        bindModal(OnlineSolverBasicInsertionStrategy.class).toProvider(modalProvider(
//                getter -> new OnlineSolverBasicInsertionStrategy(getter.getModal(Network.class), drtConfigGroup,
//                        getter.getModal(TravelTime.class), getter.getModal(TravelDisutilityFactory.class).createTravelDisutility(getter.getModal(TravelTime.class)))));

    }
}
