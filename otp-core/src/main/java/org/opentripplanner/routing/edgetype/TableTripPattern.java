/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.edgetype;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.ServiceDay;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a class of trips distinguished by service id and list of stops. For each stop, there
 * is a list of departure times, running times, arrival times, dwell times, and wheelchair
 * accessibility information (one of each of these per trip per stop). An exemplar trip is also
 * included so that information such as route name can be found. Trips are assumed to be
 * non-overtaking, so that an earlier trip never arrives after a later trip.
 */
public class TableTripPattern implements TripPattern, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(TableTripPattern.class);

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();
    
    public static final int FLAG_WHEELCHAIR_ACCESSIBLE = 1;
    public static final int MASK_PICKUP = 2|4;
    public static final int SHIFT_PICKUP = 1;
    public static final int MASK_DROPOFF = 8|16;
    public static final int SHIFT_DROPOFF = 3;
    public static final int NO_PICKUP = 1;
    public static final int FLAG_BIKES_ALLOWED = 32;

    /** 
     * An integer index uniquely identifying this pattern among all in the graph.
     * This additional level of indirection allows versioning of trip patterns, which is 
     * necessary for real-time stop time updates. (Currently using a hashmap until that proves to
     * be too inefficient.) 
     */
//    public final int patternIndex;
    
    /** An arbitrary trip that uses this pattern. Maybe we should just store route, etc. directly. */
    public final Trip exemplar;

    /** 
     * This timetable holds the 'official' stop times from GTFS. If realtime stoptime updates are 
     * applied, trips searches will be conducted using another timetable and this one will serve to 
     * find early/late offsets, or as a fallback if the other timetable becomes corrupted or
     * expires.
     */
    protected final Timetable scheduledTimetable = new Timetable(this);

    // redundant since tripTimes have a trip
    // however it's nice to have for order reference, since all timetables must have tripTimes
    // in this order, e.g. for interlining. 
    // potential optimization: trip fields can be removed from TripTimes?
    /**
     * This pattern may have multiple Timetable objects, but they should all contain TripTimes
     * for the same trips, in the same order (that of the scheduled Timetable). An exception to 
     * this rule may arise if unscheduled trips are added to a Timetable. For that case we need 
     * to search for trips/TripIds in the Timetable rather than the enclosing TripPattern.  
     */
    final ArrayList<Trip> trips = new ArrayList<Trip>();

    /**
     * An ordered list of related PatternHop. All trips in a pattern have the same stops and a
     * PatternHop apply to all those trips, so this array apply to every trip in every timetable in
     * this pattern. Please note that the array size is the number of stops minus 1. This also allow
     * to access the ordered list of stops.
     */
    private PatternHop[] patternHops;

    /** Holds stop-specific information such as wheelchair accessibility and pickup/dropoff roles. */
    @XmlElement int[] perStopFlags;
    
    /** Optimized serviceId code. All trips in a pattern are by definition on the same service. */
    int serviceId; 
    
    public TableTripPattern(Trip exemplar, ScheduledStopPattern stopPattern, int serviceId) {
        this.exemplar = exemplar;
        this.serviceId = serviceId;
        setStopsFromStopPattern(stopPattern);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // The serialized graph contains cyclic references TableTripPattern <--> Timetable.
        // The Timetable must be indexed from here (rather than in its own readObject method) 
        // to ensure that the stops field it uses in TableTripPattern is already deserialized.
        finish();
    }
            
    private void setStopsFromStopPattern(ScheduledStopPattern stopPattern) {
        patternHops = new PatternHop[stopPattern.stops.size() - 1];
        perStopFlags = new int[stopPattern.stops.size()];
        int i = 0;
        for (Stop stop : stopPattern.stops) {
            // Assume that stops can be boarded with wheelchairs by default (defer to per-trip data)
            if (stop.getWheelchairBoarding() != 2) {
                perStopFlags[i] |= FLAG_WHEELCHAIR_ACCESSIBLE;
            }
            perStopFlags[i] |= stopPattern.pickups.get(i) << SHIFT_PICKUP;
            perStopFlags[i] |= stopPattern.dropoffs.get(i) << SHIFT_DROPOFF;
            ++i;
        }
    }
    
    public Stop getStop(int stopIndex) {
        if (stopIndex == patternHops.length) {
            return patternHops[stopIndex - 1].getEndStop();
        } else {
            return patternHops[stopIndex].getBeginStop();
        }
    }

    public List<Stop> getStops() {
        /*
         * Dynamically build the list from the PatternHop list. Not super efficient but this method
         * is not called very often.
         */
        List<Stop> retval = new ArrayList<Stop>(patternHops.length + 1);
        for (int i = 0; i <= patternHops.length; i++)
            retval.add(getStop(i));
        return retval;
    }
    
    public List<PatternHop> getPatternHops() {
        return Arrays.asList(patternHops);
    }

    /* package private */
    void setPatternHop(int stopIndex, PatternHop patternHop) {
        patternHops[stopIndex] = patternHop;
    }

    @Override
    public int getHopCount() {
        return patternHops.length;
    }

    public Trip getTrip(int tripIndex) {
        return trips.get(tripIndex);
    }
    
    @XmlTransient
    public List<Trip> getTrips() {
        return trips;
    }

    public int getTripIndex(Trip trip) {
        return trips.indexOf(trip);
    }

    /** Returns whether passengers can alight at a given stop */
    public boolean canAlight(int stopIndex) {
        return getAlightType(stopIndex) != NO_PICKUP;
    }

    /** Returns whether passengers can board at a given stop */
    public boolean canBoard(int stopIndex) {
        return getBoardType(stopIndex) != NO_PICKUP;
    }

    /** Returns the zone of a given stop */
    public String getZone(int stopIndex) {
        return getStop(stopIndex).getZoneId();
    }

    /** Returns an arbitrary trip that uses this pattern */
    public Trip getExemplar() {
        return exemplar;
    }

    @Override
    public int getAlightType(int stopIndex) {
        return (perStopFlags[stopIndex] & MASK_DROPOFF) >> SHIFT_DROPOFF;
    }

    @Override
    public int getBoardType(int stopIndex) {
        return (perStopFlags[stopIndex] & MASK_PICKUP) >> SHIFT_PICKUP;
    }

    /** 
     * Gets the number of scheduled trips on this pattern. Note that when stop time updates are
     * being applied, there may be other Timetables for this pattern which contain a larger number
     * of trips. However, all trips with indexes from 0 through getNumTrips()-1 will always 
     * correspond to the scheduled trips.
     */
    public int getNumScheduledTrips () {
        return trips.size();
    }
    
    // TODO: Lombokize all boilerplate... but lombok does not generate javadoc :/ 
    public int getServiceId() { 
        return serviceId;
    }
    
    /** 
     * Find the next (or previous) departure on this pattern at or after (respectively before) the 
     * specified time. This method will make use of any TimetableResolver present in the 
     * RoutingContext to redirect departure lookups to the appropriate updated Timetable, and will 
     * fall back on the scheduled timetable when no updates are available.
     * @param boarding true means find next departure, false means find previous arrival 
     * @return a TripTimes object providing all the arrival and departure times on the best trip.
     */
    public TripTimes getNextTrip(int stopIndex, int time, State state0, ServiceDay sd,
            boolean haveBicycle, boolean boarding) {
        RoutingRequest options = state0.getOptions();
        Timetable timetable = scheduledTimetable;
        TimetableResolver snapshot = options.rctx.timetableSnapshot;
        if (snapshot != null)
            timetable = snapshot.resolve(this, sd.getServiceDate());
        // check that we can even board/alight the given stop on this pattern with these options
        int mask = boarding ? MASK_PICKUP : MASK_DROPOFF;
        int shift = boarding ? SHIFT_PICKUP : SHIFT_DROPOFF;
        if ((perStopFlags[stopIndex] & mask) >> shift == NO_PICKUP) {
            return null;
        }
        if (options.wheelchairAccessible && 
           (perStopFlags[stopIndex] & FLAG_WHEELCHAIR_ACCESSIBLE) == 0) {
            return null;
        }
        // so far so good, delegate to the timetable
        return timetable.getNextTrip(stopIndex, time, state0, sd, haveBicycle, boarding);
    }

    public TripTimes getResolvedTripTimes(int tripIndex, State state0) {
        ServiceDate serviceDate = state0.getServiceDay().getServiceDate();
        RoutingRequest options = state0.getOptions();
        Timetable timetable = scheduledTimetable;
        TimetableResolver snapshot = options.rctx.timetableSnapshot;
        if (snapshot != null) {
            timetable = snapshot.resolve(this, serviceDate);
        }
        return timetable.getTripTimes(tripIndex);
    }

    /** Gets the running time after a given stop (i.e. for the given hop) on a given trip */
    public int getRunningTime(int stopIndex, int trip) {
        return scheduledTimetable.getRunningTime(stopIndex, trip);
    }

    /** @return the index of TripTimes for this Trip(Id) in this particular TableTripPattern */
    public int getTripIndex(AgencyAndId tripId) {
        return scheduledTimetable.getTripIndex(tripId);
    }

    public TripTimes getTripTimes(int tripIndex) {
        return scheduledTimetable.getTripTimes(tripIndex);
    }

    /** Gets the departure time for a given hop on a given trip */
    public int getDepartureTime(int hop, int trip) {
        return scheduledTimetable.getDepartureTime(hop, trip);
    }

    /** Gets the arrival time for a given hop on a given trip */
    public int getArrivalTime(int hop, int trip) {
        return scheduledTimetable.getArrivalTime(hop, trip);
    }

    /** Gets all the departure times at a given stop (not used in routing) */
    public Iterator<Integer> getDepartureTimes(int stopIndex) {
        return scheduledTimetable.getDepartureTimes(stopIndex);
    }

    /** Gets all the arrival times at a given stop (not used in routing) */
    public Iterator<Integer> getArrivalTimes(int stopIndex) {
        return scheduledTimetable.getArrivalTimes(stopIndex);
    }

    /** Returns the shortest possible running time for this stop */
    public int getBestRunningTime(int stopIndex) {
        return scheduledTimetable.getBestRunningTime(stopIndex);
    }

    /** Returns the shortest possible dwell time at this stop */
    public int getBestDwellTime(int stopIndex) {
        return scheduledTimetable.getBestDwellTime(stopIndex);
    }

    /**
     * Add a trip to this TableTripPattern.
     */
    public void addTrip(Trip trip, List<StopTime> stopTimes) {
        scheduledTimetable.addTrip(trip, stopTimes);
    }

    /**
     * Finish off a TableTripPattern once all TripTimes have been added to it.
     */
    public void finish() {
        scheduledTimetable.finish();
    }
}
