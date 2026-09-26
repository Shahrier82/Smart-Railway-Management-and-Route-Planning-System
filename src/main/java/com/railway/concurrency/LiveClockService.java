package com.railway.concurrency;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drives a simulated "system clock" using a raw {@link ScheduledExecutorService}
 * ticking once a second on its own background thread — independent of the
 * JavaFX {@code Task}/{@code Service} classes used for route calculation.
 * This demonstrates plain java.util.concurrent multithreading combined with
 * JavaFX: the worker thread never touches the UI directly, it always hands
 * updates to the JavaFX Application Thread via {@link Platform#runLater}.
 * <p>
 * The simulated clock is used to highlight the next upcoming departure for
 * a given station in the UI.
 */
public class LiveClockService {

    private final StringProperty currentTime = new SimpleStringProperty("--:--:--");
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tickHandle;

    public StringProperty currentTimeProperty() {
        return currentTime;
    }

    public String getCurrentTime() {
        return currentTime.get();
    }

    /** Starts the background ticking thread. Safe to call only once per instance. */
    public synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // already running
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "live-clock-ticker");
            t.setDaemon(true);
            return t;
        });
        tickHandle = scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        String formatted = LocalTime.now().format(formatter);
        // Never touch JavaFX properties from a non-UI thread directly.
        Platform.runLater(() -> currentTime.set(formatted));
    }

    /** Stops the background ticker and releases its thread. Call on application shutdown. */
    public synchronized void stop() {
        if (tickHandle != null) {
            tickHandle.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
