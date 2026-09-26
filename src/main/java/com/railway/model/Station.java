package com.railway.model;

import java.util.Objects;

/**
 * Represents a railway station node in the network.
 * x/y are normalized map coordinates (0-1000 range) used purely for
 * drawing the station on the network canvas.
 */
public class Station {

    private int id;
    private final String code;   // short unique code, e.g. "DHK"
    private final String name;   // full display name, e.g. "Dhaka"
    private final double x;
    private final double y;

    public Station(int id, String code, String name, double x, double y) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public Station(String code, String name, double x, double y) {
        this(-1, code, name, x, y);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public String toString() {
        return name + " (" + code + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Station)) return false;
        Station station = (Station) o;
        return id == station.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
