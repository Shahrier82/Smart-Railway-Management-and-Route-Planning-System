package com.railway.model;

/**
 * Represents a single stop made by a train at a station:
 * its position in the train's stop sequence and arrival/departure times.
 * Times are stored as "HH:mm" strings (24-hour). Either time may be null
 * for origin (no arrival) or terminus (no departure) stops.
 */
public class ScheduleEntry {

    private int id;
    private final int trainId;
    private final int stationId;
    private final int stopSequence;
    private final String arrivalTime;
    private final String departureTime;

    public ScheduleEntry(int id, int trainId, int stationId, int stopSequence,
                          String arrivalTime, String departureTime) {
        this.id = id;
        this.trainId = trainId;
        this.stationId = stationId;
        this.stopSequence = stopSequence;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
    }

    public ScheduleEntry(int trainId, int stationId, int stopSequence,
                          String arrivalTime, String departureTime) {
        this(-1, trainId, stationId, stopSequence, arrivalTime, departureTime);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTrainId() {
        return trainId;
    }

    public int getStationId() {
        return stationId;
    }

    public int getStopSequence() {
        return stopSequence;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public String getDepartureTime() {
        return departureTime;
    }
}
