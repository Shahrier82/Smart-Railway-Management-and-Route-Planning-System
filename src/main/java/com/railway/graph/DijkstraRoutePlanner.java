package com.railway.graph;

import com.railway.exception.RoutePlanningException;
import com.railway.model.RouteEdge;
import com.railway.model.Station;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Computes the optimal route between two stations using Dijkstra's algorithm.
 * Implements {@link Callable} so it can be submitted to an ExecutorService /
 * wrapped in a JavaFX {@code Task} and executed off the UI thread — route
 * computation on a large network should never freeze the interface.
 */
public class DijkstraRoutePlanner implements Callable<RouteResult> {

    private final RailwayGraph graph;
    private final int sourceId;
    private final int destinationId;
    private final RouteWeight weight;

    public DijkstraRoutePlanner(RailwayGraph graph, int sourceId, int destinationId, RouteWeight weight) {
        this.graph = graph;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.weight = weight;
    }

    @Override
    public RouteResult call() throws RoutePlanningException {
        if (graph == null || graph.stationCount() == 0) {
            throw new RoutePlanningException("The railway network has not been loaded yet - nothing to route through.");
        }
        if (!graph.containsStation(sourceId) || !graph.containsStation(destinationId)) {
            return RouteResult.notFound();
        }
        if (sourceId == destinationId) {
            Station only = graph.getStation(sourceId);
            return RouteResult.of(List.of(only), 0, 0, 0, weight);
        }

        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> prevStation = new HashMap<>();
        Map<Integer, RouteEdge> prevEdge = new HashMap<>();
        Set<Integer> visited = new HashSet<>();

        for (Station s : graph.getAllStations()) {
            dist.put(s.getId(), Double.POSITIVE_INFINITY);
        }
        dist.put(sourceId, 0.0);

        PriorityQueue<int[]> queue = new PriorityQueue<>(
                Comparator.comparingDouble(a -> dist.getOrDefault(a[0], Double.POSITIVE_INFINITY)));
        // We store station ids as single-element int[] so the comparator can
        // always read the *current* best-known distance (avoids stale keys).
        queue.add(new int[]{sourceId});

        while (!queue.isEmpty()) {
            int current = queue.poll()[0];
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current == destinationId) break;

            for (RouteEdge edge : graph.getNeighbors(current)) {
                int neighbor = edge.getToStationId();
                if (visited.contains(neighbor)) continue;

                double edgeWeight = weightOf(edge);
                double candidate = dist.getOrDefault(current, Double.POSITIVE_INFINITY) + edgeWeight;

                if (candidate < dist.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    dist.put(neighbor, candidate);
                    prevStation.put(neighbor, current);
                    prevEdge.put(neighbor, edge);
                    queue.add(new int[]{neighbor});
                }
            }
        }

        if (!dist.containsKey(destinationId) || Double.isInfinite(dist.get(destinationId))) {
            return RouteResult.notFound();
        }

        // Reconstruct path and accumulate real totals (distance/time/fare),
        // independent of which metric we optimized for.
        LinkedList<Station> path = new LinkedList<>();
        double totalDistance = 0;
        int totalTime = 0;
        double totalFare = 0;

        int walker = destinationId;
        path.addFirst(graph.getStation(walker));
        while (prevStation.containsKey(walker)) {
            RouteEdge e = prevEdge.get(walker);
            totalDistance += e.getDistanceKm();
            totalTime += e.getTravelTimeMinutes();
            totalFare += e.getBaseFare();

            walker = prevStation.get(walker);
            path.addFirst(graph.getStation(walker));
        }

        return RouteResult.of(path, totalDistance, totalTime, totalFare, weight);
    }

    private double weightOf(RouteEdge edge) {
        switch (weight) {
            case TIME:
                return edge.getTravelTimeMinutes();
            case FARE:
                return edge.getBaseFare();
            case DISTANCE:
            default:
                return edge.getDistanceKm();
        }
    }
}
