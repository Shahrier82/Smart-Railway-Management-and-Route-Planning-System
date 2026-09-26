package com.railway.graph;

/** The metric Dijkstra should minimize when computing the "best" route. */
public enum RouteWeight {
    DISTANCE("Shortest Distance"),
    TIME("Fastest (Travel Time)"),
    FARE("Cheapest (Fare)");

    private final String label;

    RouteWeight(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
