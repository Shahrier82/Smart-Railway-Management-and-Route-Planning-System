package com.railway.graph;

import com.railway.model.Station;

import java.util.Collections;
import java.util.List;

/** Immutable result object produced by a route planner. */
public class RouteResult {

    private final boolean found;
    private final List<Station> path;
    private final double totalDistanceKm;
    private final int totalTimeMinutes;
    private final double totalFare;
    private final RouteWeight optimizedFor;

    private RouteResult(boolean found, List<Station> path, double totalDistanceKm,
                         int totalTimeMinutes, double totalFare, RouteWeight optimizedFor) {
        this.found = found;
        this.path = path;
        this.totalDistanceKm = totalDistanceKm;
        this.totalTimeMinutes = totalTimeMinutes;
        this.totalFare = totalFare;
        this.optimizedFor = optimizedFor;
    }

    public static RouteResult notFound() {
        return new RouteResult(false, Collections.emptyList(), 0, 0, 0, null);
    }

    public static RouteResult of(List<Station> path, double distanceKm, int timeMinutes,
                                  double fare, RouteWeight optimizedFor) {
        return new RouteResult(true, path, distanceKm, timeMinutes, fare, optimizedFor);
    }

    public boolean isFound() {
        return found;
    }

    public List<Station> getPath() {
        return path;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public int getTotalTimeMinutes() {
        return totalTimeMinutes;
    }

    public double getTotalFare() {
        return totalFare;
    }

    public RouteWeight getOptimizedFor() {
        return optimizedFor;
    }

    public String getFormattedTime() {
        int h = totalTimeMinutes / 60;
        int m = totalTimeMinutes % 60;
        return String.format("%dh %02dm", h, m);
    }
}
