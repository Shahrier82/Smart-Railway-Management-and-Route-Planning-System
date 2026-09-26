package com.railway.graph;

import com.railway.model.RouteEdge;
import com.railway.model.Station;

import java.util.*;

/**
 * In-memory adjacency-list representation of the railway network.
 * Built from the list of stations and edges loaded from the database,
 * and handed to route planners (e.g. Dijkstra) for pathfinding.
 * This class is read-only after construction, so it is safe to share
 * across background worker threads.
 */
public class RailwayGraph {

    private final Map<Integer, Station> stations = new HashMap<>();
    private final Map<Integer, List<RouteEdge>> adjacency = new HashMap<>();

    public RailwayGraph(Collection<Station> stationList, Collection<RouteEdge> edgeList) {
        for (Station s : stationList) {
            stations.put(s.getId(), s);
            adjacency.put(s.getId(), new ArrayList<>());
        }
        for (RouteEdge e : edgeList) {
            adjacency.computeIfAbsent(e.getFromStationId(), k -> new ArrayList<>()).add(e);
        }
    }

    public Station getStation(int id) {
        return stations.get(id);
    }

    public Collection<Station> getAllStations() {
        return Collections.unmodifiableCollection(stations.values());
    }

    public List<RouteEdge> getNeighbors(int stationId) {
        return adjacency.getOrDefault(stationId, Collections.emptyList());
    }

    public boolean containsStation(int id) {
        return stations.containsKey(id);
    }

    public int stationCount() {
        return stations.size();
    }
}
