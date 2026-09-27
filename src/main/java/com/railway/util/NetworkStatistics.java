package com.railway.util;

import com.railway.graph.RailwayGraph;
import com.railway.model.NetworkStats;
import com.railway.model.RouteEdge;
import com.railway.model.Station;

/**
 * Computes summary statistics over the currently loaded {@link RailwayGraph}:
 * station/segment counts, total track length, the busiest junction, and the
 * average fare charged per kilometre across the whole network. Pure,
 * synchronous computation - the graph is already in memory and small, so
 * this runs fast enough to call directly from the UI thread right after a
 * network load completes.
 */
public final class NetworkStatistics {

    private NetworkStatistics() {
    }

    public static NetworkStats compute(RailwayGraph graph) {
        int stationCount = graph.stationCount();

        double totalDistance = 0;
        double totalFare = 0;
        int edgeCount = 0;

        Station busiest = null;
        int busiestDegree = -1;

        for (Station station : graph.getAllStations()) {
            var neighbors = graph.getNeighbors(station.getId());
            int degree = neighbors.size();
            if (degree > busiestDegree) {
                busiestDegree = degree;
                busiest = station;
            }
            for (RouteEdge edge : neighbors) {
                // Edges are stored in both directions; count each physical
                // segment once by only counting the direction where the
                // "from" id is numerically smaller than the "to" id.
                if (edge.getFromStationId() < edge.getToStationId()) {
                    totalDistance += edge.getDistanceKm();
                    totalFare += edge.getBaseFare();
                    edgeCount++;
                }
            }
        }

        double avgFarePerKm = totalDistance > 0 ? totalFare / totalDistance : 0;

        return new NetworkStats(stationCount, edgeCount, totalDistance, busiest,
                Math.max(busiestDegree, 0), avgFarePerKm);
    }
}
