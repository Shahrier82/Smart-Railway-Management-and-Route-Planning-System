package com.railway.concurrency;

import javafx.application.Platform;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Simulates live seat availability per train using a background
 * {@link ScheduledExecutorService}, independent of the JavaFX Task/Service
 * classes used elsewhere - another instance of raw java.util.concurrent
 * usage, this time paired with a small observer pattern so UI components
 * (the booking dialog) can react to changes without polling.
 * <p>
 * Every few seconds a handful of trains have their available-seat count
 * nudged up or down at random, mimicking other passengers booking or
 * cancelling. Booking a ticket through the app also decrements the count
 * for that train directly via {@link #reserveSeats}.
 */
public class SeatAvailabilityService {

    private static final int INITIAL_MIN_SEATS = 25;
    private static final int INITIAL_MAX_SEATS = 90;
    private static final int MAX_SEATS_CAP = 120;

    private final Map<Integer, Integer> seatsByTrainId = new ConcurrentHashMap<>();
    private final Map<Integer, List<IntConsumer>> listeners = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private ScheduledExecutorService scheduler;

    /** Seeds every train with a random starting seat count and starts the background fluctuation thread. */
    public synchronized void start(List<Integer> trainIds) {
        for (Integer id : trainIds) {
            seatsByTrainId.putIfAbsent(id, INITIAL_MIN_SEATS + random.nextInt(INITIAL_MAX_SEATS - INITIAL_MIN_SEATS));
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // already running
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "seat-availability-sim");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::fluctuate, 4, 7, TimeUnit.SECONDS);
    }

    private void fluctuate() {
        if (seatsByTrainId.isEmpty()) return;
        Integer[] ids = seatsByTrainId.keySet().toArray(new Integer[0]);
        int trainId = ids[random.nextInt(ids.length)];
        int delta = random.nextInt(5) - 2; // -2..+2
        seatsByTrainId.compute(trainId, (id, current) -> {
            int updated = Math.max(0, Math.min(MAX_SEATS_CAP, (current == null ? 0 : current) + delta));
            notifyListeners(id, updated);
            return updated;
        });
    }

    /** Current known seat count for a train (0 if never seeded). Safe to call from any thread. */
    public int getAvailableSeats(int trainId) {
        return seatsByTrainId.getOrDefault(trainId, 0);
    }

    /** Deducts seats after a successful booking, notifying listeners on the JavaFX Application Thread. */
    public void reserveSeats(int trainId, int seatCount) {
        seatsByTrainId.compute(trainId, (id, current) -> {
            int updated = Math.max(0, (current == null ? 0 : current) - seatCount);
            notifyListeners(id, updated);
            return updated;
        });
    }

    /** Registers a callback invoked (on the JavaFX Application Thread) whenever this train's seat count changes. */
    public void addListener(int trainId, IntConsumer callback) {
        listeners.computeIfAbsent(trainId, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    public void removeListener(int trainId, IntConsumer callback) {
        List<IntConsumer> list = listeners.get(trainId);
        if (list != null) list.remove(callback);
    }

    private void notifyListeners(int trainId, int newValue) {
        List<IntConsumer> list = listeners.get(trainId);
        if (list == null || list.isEmpty()) return;
        Platform.runLater(() -> {
            for (IntConsumer c : list) c.accept(newValue);
        });
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
