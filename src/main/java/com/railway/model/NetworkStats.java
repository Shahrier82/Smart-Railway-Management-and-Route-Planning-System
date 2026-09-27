package com.railway.model;

/**
 * Immutable snapshot of network-wide statistics, computed by
 * {@link com.railway.util.NetworkStatistics} once the graph has loaded.
 */
public class NetworkStats {

    private final int stationCount;
    private final int trackSegmentCount;
    private final double totalTrackKm;
    private final Station busiestStation;
    private final int busiestStationConnections;
    private final double averageFarePerKm;

    public NetworkStats(int stationCount, int trackSegmentCount, double totalTrackKm,
                         Station busiestStation, int busiestStationConnections, double averageFarePerKm) {
        this.stationCount = stationCount;
        this.trackSegmentCount = trackSegmentCount;
        this.totalTrackKm = totalTrackKm;
        this.busiestStation = busiestStation;
        this.busiestStationConnections = busiestStationConnections;
        this.averageFarePerKm = averageFarePerKm;
    }

    public int getStationCount() {
        return stationCount;
    }

    public int getTrackSegmentCount() {
        return trackSegmentCount;
    }

    public double getTotalTrackKm() {
        return totalTrackKm;
    }

    public Station getBusiestStation() {
        return busiestStation;
    }

    public int getBusiestStationConnections() {
        return busiestStationConnections;
    }

    public double getAverageFarePerKm() {
        return averageFarePerKm;
    }
}
