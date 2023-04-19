package org.matsim.accessibillityDrtOptimizer.optimizer;

import com.google.common.base.Preconditions;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.AccessibilityCalculator;
import org.matsim.accessibillityDrtOptimizer.accessibilityCalculator.AlternativeModeData;
import org.matsim.accessibillityDrtOptimizer.optimizer.basicStructures.FleetSchedules;
import org.matsim.accessibillityDrtOptimizer.optimizer.basicStructures.OnlineVehicleInfo;
import org.matsim.accessibillityDrtOptimizer.optimizer.basicStructures.TimetableEntry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.extension.preplanned.optimizer.WaitForStopTask;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.VehicleEntry;
import org.matsim.contrib.drt.passenger.AcceptedDrtRequest;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtDriveTask;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.schedule.DrtStopTask;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.schedule.*;
import org.matsim.contrib.dvrp.tracker.OnlineDriveTaskTracker;
import org.matsim.contrib.dvrp.util.LinkTimePair;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static org.matsim.contrib.drt.schedule.DrtTaskBaseType.STAY;

public class SimpleRejectionDrtOptimizer implements DrtOptimizer {

    private final Network network;
    private final TravelTime travelTime;
    private final MobsimTimer timer;
    private final DrtTaskFactory taskFactory;
    private final EventsManager eventsManager;
    private final ScheduleTimingUpdater scheduleTimingUpdater;
    private final LeastCostPathCalculator router;
    private final double stopDuration;
    private final String mode;
    private final DrtConfigGroup drtCfg;

    private final Fleet fleet;
    private final ForkJoinPool forkJoinPool;
    private final VehicleEntry.EntryFactory vehicleEntryFactory;

    private final AccessibilityCalculator accessibilityCalculator;
    private final OnlineSolverBasicInsertionStrategy inserter;

    private final Map<Id<Person>, DrtRequest> openRequests = new HashMap<>();

    private double lastUpdateTimeOfFleetStatus;

    private FleetSchedules fleetSchedules;
    Map<Id<DvrpVehicle>, OnlineVehicleInfo> realTimeVehicleInfoMap = new LinkedHashMap<>();

    public SimpleRejectionDrtOptimizer(Network network, TravelTime travelTime, MobsimTimer timer, DrtTaskFactory taskFactory,
                                       EventsManager eventsManager, ScheduleTimingUpdater scheduleTimingUpdater,
                                       TravelDisutility travelDisutility, DrtConfigGroup drtCfg,
                                       Fleet fleet, ForkJoinPool forkJoinPool, VehicleEntry.EntryFactory vehicleEntryFactory,
                                       OnlineSolverBasicInsertionStrategy inserter, AccessibilityCalculator accessibilityCalculator) {
        this.network = network;
        this.travelTime = travelTime;
        this.timer = timer;
        this.taskFactory = taskFactory;
        this.eventsManager = eventsManager;
        this.scheduleTimingUpdater = scheduleTimingUpdater;
        this.router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);
        this.stopDuration = drtCfg.stopDuration;
        this.mode = drtCfg.getMode();
        this.accessibilityCalculator = accessibilityCalculator;
        this.drtCfg = drtCfg;
        this.inserter = inserter;
        this.forkJoinPool = forkJoinPool;
        this.fleet = fleet;
        this.vehicleEntryFactory = vehicleEntryFactory;
    }

    @Override
    public void requestSubmitted(Request request) {
        double now = timer.getTimeOfDay();
        assert now != 0 : "currently we do not support DRT requests submitted at t = 0";

        updateFleetStatus(now);
        if (fleetSchedules == null) {
            fleetSchedules = FleetSchedules.initializeFleetSchedules(realTimeVehicleInfoMap);
        }

        DrtRequest drtRequest = (DrtRequest) request;
        AlternativeModeData alternativeModeData = accessibilityCalculator.calculateAlternativeMode(drtRequest);
        double directTravelTime = VrpPaths.calcAndCreatePath(drtRequest.getFromLink(), drtRequest.getToLink(), now, router, travelTime).getTravelTime();
        double maxTravelTime = drtCfg.maxTravelTimeAlpha * directTravelTime + drtCfg.maxTravelTimeBeta;

        if (considerRequest(maxTravelTime, alternativeModeData.totalTravelTime())) {
            openRequests.put(drtRequest.getPassengerId(), drtRequest);
            Id<DvrpVehicle> selectedVehicleId = inserter.insert(drtRequest, fleetSchedules.vehicleToTimetableMap(), realTimeVehicleInfoMap);
            if (selectedVehicleId != null) {
                eventsManager.processEvent(
                        new PassengerRequestScheduledEvent(timer.getTimeOfDay(), drtRequest.getMode(), drtRequest.getId(),
                                drtRequest.getPassengerId(), selectedVehicleId, Double.NaN, Double.NaN));
                updateVehicleCurrentTask(realTimeVehicleInfoMap.get(selectedVehicleId), now);
            } else {
                eventsManager.processEvent(new PassengerRequestRejectedEvent(timer.getTimeOfDay(), mode, request.getId(),
                        drtRequest.getPassengerId(), "No feasible insertion. The spontaneous request is rejected"));
                openRequests.remove(drtRequest.getPassengerId());
            }
        } else {
            eventsManager.processEvent(new PassengerRequestRejectedEvent(timer.getTimeOfDay(), mode, request.getId(),
                    drtRequest.getPassengerId(), "Request is rejected because alternative mode is also attractive"));
        }
    }

    @Override
    public void nextTask(DvrpVehicle vehicle) {
        scheduleTimingUpdater.updateBeforeNextTask(vehicle);
        var schedule = vehicle.getSchedule();

        if (schedule.getStatus() == Schedule.ScheduleStatus.PLANNED) {
            schedule.nextTask();
            return;
        }

        var currentTask = schedule.getCurrentTask();
        var currentLink = Tasks.getEndLink(currentTask);
        double currentTime = timer.getTimeOfDay();

        var stopsToVisit = fleetSchedules.vehicleToTimetableMap().get(vehicle.getId());

        if (stopsToVisit.isEmpty()) {
            // no preplanned stops for the vehicle within current horizon
            if (currentTime < vehicle.getServiceEndTime()) {
                // fill the time gap with STAY
                schedule.addTask(taskFactory.createStayTask(vehicle, currentTime, vehicle.getServiceEndTime(), currentLink));
            } else if (!STAY.isBaseTypeOf(currentTask)) {
                // we need to end the schedule with STAY task even if it is delayed
                schedule.addTask(taskFactory.createStayTask(vehicle, currentTime, currentTime, currentLink));
            }
        } else {
            var nextStop = stopsToVisit.get(0);
            if (!nextStop.getLinkId().equals(currentLink.getId())) {
                // Next stop is at another location? --> Add a drive task
                var nextLink = network.getLinks().get(nextStop.getLinkId());
                VrpPathWithTravelData path = VrpPaths.calcAndCreatePath(currentLink, nextLink, currentTime, router,
                        travelTime);
                schedule.addTask(taskFactory.createDriveTask(vehicle, path, DrtDriveTask.TYPE));
            } else if (nextStop.getRequest().getEarliestDepartureTime() >= timer.getTimeOfDay()) {
                // We are at the stop location. But we are too early. --> Add a wait for stop task
                // Currently assuming the mobsim time step is 1 s
                schedule.addTask(new WaitForStopTask(currentTime,
                        nextStop.getRequest().getEarliestDepartureTime() + 1, currentLink));
            } else {
                // We are ready for the stop task! --> Add stop task to the schedule
                var stopTask = taskFactory.createStopTask(vehicle, currentTime, currentTime + stopDuration, currentLink);
                if (nextStop.getStopType() == TimetableEntry.StopType.PICKUP) {
                    var request = Preconditions.checkNotNull(openRequests.get(nextStop.getRequest().getPassengerId()),
                            "Request (%s) has not been yet submitted", nextStop.getRequest());
                    stopTask.addPickupRequest(AcceptedDrtRequest.createFromOriginalRequest(request));
                } else {
                    var request = Preconditions.checkNotNull(openRequests.remove(nextStop.getRequest().getPassengerId()),
                            "Request (%s) has not been yet submitted", nextStop.getRequest());
                    stopTask.addDropoffRequest(AcceptedDrtRequest.createFromOriginalRequest(request));
                    fleetSchedules.requestIdToVehicleMap().remove(request.getPassengerId());
                }
                schedule.addTask(stopTask);
                stopsToVisit.remove(0); //remove the first entry in the stops to visit list
            }
        }

        // switch to the next task and update currentTasks
        schedule.nextTask();
    }

    @Override
    public void notifyMobsimBeforeSimStep(MobsimBeforeSimStepEvent e) {
        // nothing to do here

    }

    private boolean considerRequest(double maxTravelTime, double alternativeModeTravelTime) {
        return maxTravelTime < alternativeModeTravelTime;
    }

    private void updateFleetStatus(double now) {
        // This function only needs to be performed once for each time step
        if (now != lastUpdateTimeOfFleetStatus) {
            for (DvrpVehicle v : fleet.getVehicles().values()) {
                scheduleTimingUpdater.updateTimings(v);
            }

            var vehicleEntries = forkJoinPool.submit(() -> fleet.getVehicles()
                    .values()
                    .parallelStream()
                    .map(v -> vehicleEntryFactory.create(v, now))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(e -> e.vehicle.getId(), e -> e))).join();

            for (VehicleEntry vehicleEntry : vehicleEntries.values()) {
                Schedule schedule = vehicleEntry.vehicle.getSchedule();
                Task currentTask = schedule.getCurrentTask();

                Link currentLink = null;
                double divertableTime = Double.NaN;

                if (currentTask instanceof DrtStayTask) {
                    currentLink = ((DrtStayTask) currentTask).getLink();
                    divertableTime = now;
                }

                if (currentTask instanceof WaitForStopTask) {
                    currentLink = ((WaitForStopTask) currentTask).getLink();
                    divertableTime = now;
                }

                if (currentTask instanceof DriveTask) {
                    LinkTimePair diversion = ((OnlineDriveTaskTracker) currentTask.getTaskTracker()).getDiversionPoint();
                    currentLink = diversion.link;
                    divertableTime = diversion.time;
                }

                if (currentTask instanceof DrtStopTask) {
                    currentLink = ((DrtStopTask) currentTask).getLink();
                    divertableTime = currentTask.getEndTime();
                }

                Preconditions.checkState(currentLink != null, "Current link should not be null! Vehicle ID = " + vehicleEntry.vehicle.getId().toString());
                Preconditions.checkState(!Double.isNaN(divertableTime), "Divertable time should not be NaN! Vehicle ID = " + vehicleEntry.vehicle.getId().toString());
                OnlineVehicleInfo onlineVehicleInfo = new OnlineVehicleInfo(vehicleEntry.vehicle, currentLink, divertableTime);
                realTimeVehicleInfoMap.put(vehicleEntry.vehicle.getId(), onlineVehicleInfo);
            }

            lastUpdateTimeOfFleetStatus = now;
        }
    }

    private void updateVehicleCurrentTask(OnlineVehicleInfo onlineVehicleInfo, double now) {
        DvrpVehicle vehicle = onlineVehicleInfo.vehicle();
        Schedule schedule = vehicle.getSchedule();
        Task currentTask = schedule.getCurrentTask();
        Link currentLink = onlineVehicleInfo.currentLink();
        double divertableTime = onlineVehicleInfo.divertableTime();
        List<TimetableEntry> timetable = fleetSchedules.vehicleToTimetableMap().get(vehicle.getId());

        // Stay task: end stay task now if timetable is non-empty
        if (currentTask instanceof DrtStayTask && !timetable.isEmpty()) {
            currentTask.setEndTime(now);
        }

        // Wait for stop task: end this task if first timetable entry has changed
        if (currentTask instanceof WaitForStopTask) {
            currentTask.setEndTime(now);
            //Note: currently, it's not easy to check if the first entry in timetable is changed.
            // We just end this task (a new wait for stop task will be generated at "nextTask" section if needed)
        }

        // Drive task: Divert the drive task when needed
        if (currentTask instanceof DrtDriveTask) {
            if (timetable.isEmpty()) {
                // stop the vehicle at divertable location and time (a stay task will be appended in the "nextTask" section)
                var dummyPath = VrpPaths.calcAndCreatePath(currentLink, currentLink, divertableTime, router, travelTime);
                ((OnlineDriveTaskTracker) currentTask.getTaskTracker()).divertPath(dummyPath);
            } else {
                // Divert the vehicle if destination has changed
                assert timetable.get(0) != null;
                Id<Link> newDestination = timetable.get(0).getLinkId();
                Id<Link> oldDestination = ((DrtDriveTask) currentTask).getPath().getToLink().getId();
                if (!oldDestination.toString().equals(newDestination.toString())) {
                    var newPath = VrpPaths.calcAndCreatePath(currentLink,
                            network.getLinks().get(newDestination), divertableTime, router, travelTime);
                    ((OnlineDriveTaskTracker) currentTask.getTaskTracker()).divertPath(newPath);
                }
            }
        }

        // Stop task: nothing need to be done here

    }
}
