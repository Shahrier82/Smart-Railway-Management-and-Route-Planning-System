package com.railway.model;

/**
 * Represents a direct rail track segment ("edge") between two stations.
 * Edges are stored directed in the database; the graph builder inserts
 * both directions for bidirectional track segments.
 */
public class RouteEdge {

    private int id;
    private final int fromStationId;
    private final int toStationId;
    private final double distanceKm;
    private final int travelTimeMinutes;
    private final double baseFare;

    public RouteEdge(int id, int fromStationId, int toStationId,
                      double distanceKm, int travelTimeMinutes, double baseFare) {
        this.id = id;
        this.fromStationId = fromStationId;
        this.toStationId = toStationId;
        this.distanceKm = distanceKm;
        this.travelTimeMinutes = travelTimeMinutes;
        this.baseFare = baseFare;
    }

    public RouteEdge(int fromStationId, int toStationId,
                      double distanceKm, int travelTimeMinutes, double baseFare) {
        this(-1, fromStationId, toStationId, distanceKm, travelTimeMinutes, baseFare);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFromStationId() {
        return fromStationId;
    }

    public int getToStationId() {
        return toStationId;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public double getBaseFare() {
        return baseFare;
    }
}
