package com.railway.graph;

import com.railway.db.DatabaseManager;
import com.railway.model.ScheduleEntry;
import com.railway.model.Station;
import com.railway.model.Train;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Given a station-to-station route (as produced by {@link DijkstraRoutePlanner}),
 * works out which train(s) a passenger would actually ride, and where they
 * would need to change trains.
 * <p>
 * At each point along the path it greedily picks the train that carries the
 * passenger the furthest along the remaining route (fewest changes), the way
 * a human would plan a real multi-leg train journey. If no scheduled train
 * covers even the very next stop, that stretch is recorded as a leg with a
 * {@code null} train so the UI can tell the passenger no direct service
 * exists there.
 */
public class TrainItineraryPlanner {

    private final DatabaseManager db;

    public TrainItineraryPlanner(DatabaseManager db) {
        this.db = db;
    }

    /** Builds the itinerary. Performs database reads, so call this off the UI thread. */
    public List<TrainLeg> plan(List<Station> path) throws SQLException {
        List<TrainLeg> legs = new ArrayList<>();
        if (path == null || path.size() < 2) {
            return legs;
        }

        List<Train> allTrains = db.getAllTrains();
        // trainId -> (stationId -> stop_sequence), preloaded once for the whole plan.
        Map<Integer, Map<Integer, Integer>> stopsByTrain = new HashMap<>();
        for (Train t : allTrains) {
            Map<Integer, Integer> stopSeq = new HashMap<>();
            for (ScheduleEntry entry : db.getScheduleForTrain(t.getId())) {
                stopSeq.put(entry.getStationId(), entry.getStopSequence());
            }
            stopsByTrain.put(t.getId(), stopSeq);
        }

        int i = 0;
        while (i < path.size() - 1) {
            int bestReachIndex = -1;
            Train bestTrain = null;

            for (Train t : allTrains) {
                Map<Integer, Integer> stopSeq = stopsByTrain.get(t.getId());
                Integer seqAtI = stopSeq.get(path.get(i).getId());
                if (seqAtI == null) continue; // this train doesn't stop here at all

                // Walk backwards from the route's end to find the furthest
                // later path station this same train also stops at.
                for (int j = path.size() - 1; j > i; j--) {
                    Integer seqAtJ = stopSeq.get(path.get(j).getId());
                    if (seqAtJ != null && seqAtJ > seqAtI) {
                        if (j > bestReachIndex) {
                            bestReachIndex = j;
                            bestTrain = t;
                        }
                        break;
                    }
                }
            }

            if (bestReachIndex == -1) {
                // No scheduled train covers even the next stop - record the gap
                // and move on one station at a time so the itinerary still completes.
                legs.add(new TrainLeg(path.get(i), path.get(i + 1), null));
                i = i + 1;
            } else {
                legs.add(new TrainLeg(path.get(i), path.get(bestReachIndex), bestTrain));
                i = bestReachIndex;
            }
        }

        return legs;
    }
}
