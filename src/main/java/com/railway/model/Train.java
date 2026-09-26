package com.railway.model;

/** Represents a train service, identified by a number and display name. */
public class Train {

    private int id;
    private final String number;
    private final String name;

    public Train(int id, String number, String name) {
        this.id = id;
        this.number = number;
        this.name = name;
    }

    public Train(String number, String name) {
        this(-1, number, name);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return number + " - " + name;
    }
}
