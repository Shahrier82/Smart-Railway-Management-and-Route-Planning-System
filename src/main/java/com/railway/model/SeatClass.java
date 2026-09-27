package com.railway.model;

/**
 * Seating classes offered on Bangladesh Railway intercity services, each
 * with a fare multiplier applied to a route's base fare by
 * {@link com.railway.util.FareCalculator}.
 */
public enum SeatClass {
    SHOVON_CHAIR("Shovon Chair", 1.0),
    SNIGDHA("Snigdha (AC Chair)", 1.6),
    AC_SEAT("AC Seat", 2.1),
    AC_BERTH("AC Berth", 2.8);

    private final String displayName;
    private final double fareMultiplier;

    SeatClass(String displayName, double fareMultiplier) {
        this.displayName = displayName;
        this.fareMultiplier = fareMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getFareMultiplier() {
        return fareMultiplier;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
