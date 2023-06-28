package org.matsim.accessibillityDrtOptimizer.run.modules;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.AccessibilityCalculator;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.DefaultAccessibilityCalculator;
import org.matsim.accessibillityDrtOptimizer.optimizer.OnlineSolverBasicInsertionStrategy;
import org.matsim.accessibillityDrtOptimizer.optimizer.SimpleRejectionDrtOptimizer;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.QSimScopeForkJoinPoolHolder;
import org.matsim.contrib.drt.optimizer.VehicleEntry;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.contrib.zone.skims.TravelTimeMatrix;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelTime;

public class AccessibilityModule extends AbstractDvrpModeQSimModule {

    private final DrtConfigGroup drtConfigGroup;
    private final double threshold;

    public AccessibilityModule(DrtConfigGroup drtConfigGroup, double threshold) {
        super(drtConfigGroup.mode);
        this.drtConfigGroup = drtConfigGroup;
        this.threshold = threshold;
    }

    @Override
    protected void configureQSim() {
        addModalComponent(DrtOptimizer.class, this.modalProvider((getter) -> new SimpleRejectionDrtOptimizer(getter.getModal(Network.class),
                getter.getModal(TravelTime.class), getter.get(MobsimTimer.class), getter.getModal(DrtTaskFactory.class),
                getter.get(EventsManager.class), getter.getModal(ScheduleTimingUpdater.class),
                getter.getModal(TravelDisutilityFactory.class).createTravelDisutility(getter.getModal(TravelTime.class)),
                drtConfigGroup, getter.getModal(Fleet.class),
                getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool(),
                getter.getModal(VehicleEntry.EntryFactory.class),
                getter.getModal(OnlineSolverBasicInsertionStrategy.class),
                getter.getModal(AccessibilityCalculator.class), threshold)));

        bindModal(OnlineSolverBasicInsertionStrategy.class).toProvider(modalProvider(
                getter -> new OnlineSolverBasicInsertionStrategy(getter.getModal(Network.class), drtConfigGroup,
                        getter.getModal(TravelTime.class), getter.getModal(TravelDisutilityFactory.class).createTravelDisutility(getter.getModal(TravelTime.class)))));

        bindModal(AccessibilityCalculator.class).toProvider(modalProvider(
                getter -> new DefaultAccessibilityCalculator(getter.get(SwissRailRaptor.class))
        ));
    }
}
