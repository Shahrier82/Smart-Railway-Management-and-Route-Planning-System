package com.railway.ui;

import com.railway.model.Booking;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Function;

/**
 * Read-only table listing every ticket booking saved so far, most recent
 * first. Data is fetched by the caller beforehand (BookingDao access is
 * I/O, so it happens off the UI thread) - this class only renders it.
 */
public final class BookingsHistoryDialog {

    private BookingsHistoryDialog() {
    }

    public static void show(Stage owner, List<Booking> bookings) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("My Bookings");
        dialog.getDialogPane().getStyleClass().add("bookings-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiTheme.apply(dialog.getDialogPane());

        TableView<Booking> table = new TableView<>(FXCollections.observableArrayList(bookings));
        table.setPrefSize(640, 360);
        table.setPlaceholder(new Label("No bookings yet \u2014 find a route and click \"Book Ticket\"."));

        table.getColumns().add(column("Passenger", Booking::getPassengerName));
        table.getColumns().add(column("From", Booking::getFromStationLabel));
        table.getColumns().add(column("To", Booking::getToStationLabel));
        table.getColumns().add(column("Train",
                b -> b.getTrainNumber() != null ? b.getTrainNumber() + " - " + b.getTrainName() : "Multiple"));
        table.getColumns().add(column("Class", b -> b.getSeatClass().getDisplayName()));
        table.getColumns().add(column("Seats", b -> String.valueOf(b.getSeatCount())));
        table.getColumns().add(column("Fare", b -> String.format("%.2f BDT", b.getTotalFare())));
        table.getColumns().add(column("Booked At", Booking::getBookedAt));

        dialog.getDialogPane().setContent(table);
        dialog.showAndWait();
    }

    private static TableColumn<Booking, String> column(String title, Function<Booking, String> extractor) {
        TableColumn<Booking, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(extractor.apply(data.getValue())));
        col.setPrefWidth(90);
        return col;
    }
}
