package com.railway.util;

import com.railway.model.SeatClass;

/**
 * Applies a {@link SeatClass} fare multiplier and seat count to a route's
 * base (Shovon-equivalent) fare to get the total ticket price.
 */
public final class FareCalculator {

    private FareCalculator() {
    }

    /** Rounds to 2 decimal places, since fares are currency amounts. */
    public static double calculateTotalFare(double baseRouteFare, SeatClass seatClass, int seatCount) {
        if (seatCount < 1) {
            throw new IllegalArgumentException("Seat count must be at least 1.");
        }
        double raw = baseRouteFare * seatClass.getFareMultiplier() * seatCount;
        return Math.round(raw * 100.0) / 100.0;
    }

    /** The per-seat fare for a given class, without multiplying by seat count. */
    public static double calculatePerSeatFare(double baseRouteFare, SeatClass seatClass) {
        double raw = baseRouteFare * seatClass.getFareMultiplier();
        return Math.round(raw * 100.0) / 100.0;
    }
}
