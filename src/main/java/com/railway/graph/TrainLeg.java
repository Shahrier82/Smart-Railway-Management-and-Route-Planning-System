package com.railway.graph;

import com.railway.model.Station;
import com.railway.model.Train;

/**
 * One leg of a journey along a computed route: ride a particular train
 * from one station to another. {@link #getTrain()} is {@code null} when
 * no scheduled train covers this stretch at all (a service gap), so the
 * UI can flag it distinctly from an ordinary change-of-train.
 */
public class TrainLeg {

    private final Station from;
    private final Station to;
    private final Train train; // null => no scheduled train covers this leg

    public TrainLeg(Station from, Station to, Train train) {
        this.from = from;
        this.to = to;
        this.train = train;
    }

    public Station getFrom() {
        return from;
    }

    public Station getTo() {
        return to;
    }

    public Train getTrain() {
        return train;
    }

    public boolean hasTrain() {
        return train != null;
    }
}
