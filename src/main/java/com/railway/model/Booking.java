package com.railway.model;

/**
 * A saved ticket reservation for a planned journey: who it's for, which
 * train/class/seat count, and the total fare charged. Persisted via
 * {@link com.railway.db.BookingDao}.
 */
public class Booking {

    private int id;
    private final String passengerName;
    private final int fromStationId;
    private final int toStationId;
    private final String fromStationLabel;
    private final String toStationLabel;
    private final String trainNumber;   // nullable - route may need multiple trains
    private final String trainName;     // nullable
    private final SeatClass seatClass;
    private final int seatCount;
    private final double totalFare;
    private final String bookedAt;      // ISO-8601 timestamp string

    public Booking(int id, String passengerName, int fromStationId, int toStationId,
                   String fromStationLabel, String toStationLabel, String trainNumber, String trainName,
                   SeatClass seatClass, int seatCount, double totalFare, String bookedAt) {
        this.id = id;
        this.passengerName = passengerName;
        this.fromStationId = fromStationId;
        this.toStationId = toStationId;
        this.fromStationLabel = fromStationLabel;
        this.toStationLabel = toStationLabel;
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.seatClass = seatClass;
        this.seatCount = seatCount;
        this.totalFare = totalFare;
        this.bookedAt = bookedAt;
    }

    public Booking(String passengerName, int fromStationId, int toStationId,
                   String fromStationLabel, String toStationLabel, String trainNumber, String trainName,
                   SeatClass seatClass, int seatCount, double totalFare, String bookedAt) {
        this(-1, passengerName, fromStationId, toStationId, fromStationLabel, toStationLabel,
                trainNumber, trainName, seatClass, seatCount, totalFare, bookedAt);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public int getFromStationId() {
        return fromStationId;
    }

    public int getToStationId() {
        return toStationId;
    }

    public String getFromStationLabel() {
        return fromStationLabel;
    }

    public String getToStationLabel() {
        return toStationLabel;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getTrainName() {
        return trainName;
    }

    public SeatClass getSeatClass() {
        return seatClass;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public double getTotalFare() {
        return totalFare;
    }

    public String getBookedAt() {
        return bookedAt;
    }
}
