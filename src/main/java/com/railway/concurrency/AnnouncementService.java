package com.railway.concurrency;

import com.railway.model.Train;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives a rotating station-announcement banner ("now boarding...", "is
 * running behind schedule...") on its own background thread, in the style
 * of a real railway platform PA system / departure board. Runs
 * independently of {@link LiveClockService} to demonstrate more than one
 * concurrently-running background service coexisting cleanly.
 */
public class AnnouncementService {

    private static final String[] TEMPLATES = {
            "Attention passengers: Train %s %s is now boarding.",
            "Train %s %s is running approximately %d minutes behind schedule.",
            "Please keep the platform clear as Train %s %s approaches.",
            "Final call for Train %s %s. Doors closing shortly.",
            "Train %s %s has arrived and is ready for boarding."
    };

    private final StringProperty announcement =
            new SimpleStringProperty("Welcome to Smart Railway. Please check the departure board for updates.");
    private final Random random = new Random();

    private ScheduledExecutorService scheduler;
    private List<Train> trains = List.of();

    public StringProperty announcementProperty() {
        return announcement;
    }

    /** Starts cycling announcements referencing the given trains, roughly every 6 seconds. */
    public synchronized void start(List<Train> trains) {
        this.trains = trains;
        if (trains.isEmpty()) return;
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // already running
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "announcement-ticker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::announceRandom, 3, 6, TimeUnit.SECONDS);
    }

    private void announceRandom() {
        if (trains.isEmpty()) return;
        Train train = trains.get(random.nextInt(trains.size()));
        String template = TEMPLATES[random.nextInt(TEMPLATES.length)];
        String message = template.contains("%d")
                ? String.format(template, train.getNumber(), train.getName(), 5 + random.nextInt(20))
                : String.format(template, train.getNumber(), train.getName());
        Platform.runLater(() -> announcement.set(message));
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
