package com.railway;

import com.railway.db.BookingDao;
import com.railway.db.DatabaseManager;
import com.railway.ui.MainView;
import com.railway.util.SampleDataLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Application entry point. Opens/creates the SQLite database file next to
 * the application, seeds it with sample data on first run, then hands off
 * to {@link MainView} to build the JavaFX UI.
 */
public class Main extends Application {

    private static final String DB_FILE_NAME = "railway.db";

    private DatabaseManager db;
    private BookingDao bookingDao;
    private MainView mainView;

    @Override
    public void start(Stage primaryStage) {
        try {
            String dbPath = Path.of(DB_FILE_NAME).toAbsolutePath().toString();
            db = new DatabaseManager(dbPath);
            bookingDao = new BookingDao(dbPath);
            SampleDataLoader.seedIfEmpty(db);
        } catch (Exception e) {
            showFatalError("Failed to initialize the database:\n" + e.getMessage());
            Platform.exit();
            return;
        }

        mainView = new MainView(primaryStage, db, bookingDao);
        mainView.show();

        primaryStage.setOnCloseRequest(e -> shutdown());
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (mainView != null) {
            mainView.shutdown();
        }
        if (bookingDao != null) {
            try {
                bookingDao.close();
            } catch (Exception ignored) {
                // best-effort close on shutdown
            }
        }
        if (db != null) {
            try {
                db.close();
            } catch (Exception ignored) {
                // best-effort close on shutdown
            }
        }
    }

    private void showFatalError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Startup Error");
        alert.setHeaderText("Smart Railway System could not start");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
