package com.railway.ui;

import com.railway.concurrency.SeatAvailabilityService;
import com.railway.db.BookingDao;
import com.railway.exception.RailwayDataException;
import com.railway.graph.RouteResult;
import com.railway.graph.TrainLeg;
import com.railway.model.Booking;
import com.railway.model.SeatClass;
import com.railway.model.Train;
import com.railway.util.FareCalculator;
import com.railway.util.TicketGenerator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.IntConsumer;

/**
 * Modal booking flow for the currently computed route: pick a seat class
 * and seat count, watch the total fare and (when a single direct train
 * covers the whole route) the live simulated seat count update as you
 * choose, then confirm to persist the booking via {@link BookingDao} and
 * view/save a text e-ticket.
 */
public final class BookingDialog {

    private BookingDialog() {
    }

    public static void show(Stage owner, RouteResult route, List<TrainLeg> legs,
                             BookingDao bookingDao, SeatAvailabilityService seatService,
                             ExecutorService executor) {

        Train soleTrain = soleCoveringTrain(legs);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Book Ticket");
        dialog.getDialogPane().getStyleClass().add("booking-dialog");
        ButtonType bookButtonType = new ButtonType("Book Ticket", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(bookButtonType, ButtonType.CANCEL);
        UiTheme.apply(dialog.getDialogPane());

        TextField nameField = new TextField();
        nameField.setPromptText("Passenger name");

        ComboBox<SeatClass> classCombo = new ComboBox<>(FXCollections.observableArrayList(SeatClass.values()));
        classCombo.getSelectionModel().select(SeatClass.SHOVON_CHAIR);

        Spinner<Integer> seatSpinner = new Spinner<>(1, 6, 1);
        seatSpinner.setEditable(true);

        Label fareLabel = new Label();
        fareLabel.getStyleClass().add("stat-value");
        Label seatsAvailableLabel = new Label(soleTrain != null
                ? seatService.getAvailableSeats(soleTrain.getId()) + " seats available (live)"
                : "Live seat count shown only when one direct train covers the whole route.");
        seatsAvailableLabel.setWrapText(true);

        Runnable refreshFare = () -> {
            double total = FareCalculator.calculateTotalFare(route.getTotalFare(),
                    classCombo.getValue(), seatSpinner.getValue());
            fareLabel.setText(String.format("%.2f BDT", total));
        };
        refreshFare.run();
        classCombo.valueProperty().addListener((obs, oldVal, newVal) -> refreshFare.run());
        seatSpinner.valueProperty().addListener((obs, oldVal, newVal) -> refreshFare.run());

        IntConsumer seatListener = updated -> seatsAvailableLabel.setText(updated + " seats available (live)");
        if (soleTrain != null) {
            seatService.addListener(soleTrain.getId(), seatListener);
        }
        dialog.setOnHidden(e -> {
            if (soleTrain != null) seatService.removeListener(soleTrain.getId(), seatListener);
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Passenger name:"), nameField);
        grid.addRow(1, new Label("Seat class:"), classCombo);
        grid.addRow(2, new Label("Seats:"), seatSpinner);
        grid.addRow(3, new Label("Total fare:"), fareLabel);
        grid.addRow(4, new Label("Availability:"), seatsAvailableLabel);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(seatsAvailableLabel, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);

        Button bookButton = (Button) dialog.getDialogPane().lookupButton(bookButtonType);
        bookButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                nameField.setStyle("-fx-border-color: #C13B23; -fx-border-width: 1.5;");
                event.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != bookButtonType) return;

            double totalFare = FareCalculator.calculateTotalFare(route.getTotalFare(),
                    classCombo.getValue(), seatSpinner.getValue());
            String bookedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            int seatCount = seatSpinner.getValue();

            Booking booking = new Booking(
                    nameField.getText().trim(),
                    route.getPath().get(0).getId(),
                    route.getPath().get(route.getPath().size() - 1).getId(),
                    route.getPath().get(0).toString(),
                    route.getPath().get(route.getPath().size() - 1).toString(),
                    soleTrain != null ? soleTrain.getNumber() : null,
                    soleTrain != null ? soleTrain.getName() : null,
                    classCombo.getValue(), seatCount, totalFare, bookedAt);

            executor.submit(() -> {
                try {
                    Booking saved = bookingDao.save(booking);
                    if (soleTrain != null) {
                        seatService.reserveSeats(soleTrain.getId(), seatCount);
                    }
                    Platform.runLater(() -> showConfirmation(owner, saved));
                } catch (RailwayDataException ex) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Booking failed: " + ex.getMessage());
                        UiTheme.apply(alert.getDialogPane());
                        alert.showAndWait();
                    });
                }
            });
        });
    }

    private static Train soleCoveringTrain(List<TrainLeg> legs) {
        if (legs == null || legs.size() != 1 || !legs.get(0).hasTrain()) return null;
        return legs.get(0).getTrain();
    }

    private static void showConfirmation(Stage owner, Booking booking) {
        String ticketText = TicketGenerator.generate(booking);

        TextArea area = new TextArea(ticketText);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(14);
        area.getStyleClass().add("ticket-area");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("Booking Confirmed");
        alert.setHeaderText("Booking #" + booking.getId() + " confirmed for " + booking.getPassengerName());
        ButtonType saveButtonType = new ButtonType("Save as .txt", ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().add(saveButtonType);
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().getStyleClass().add("ticket-dialog");
        UiTheme.apply(alert.getDialogPane());

        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType != saveButtonType) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Ticket");
            chooser.setInitialFileName("ticket_" + booking.getId() + ".txt");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
            File file = chooser.showSaveDialog(owner);
            if (file == null) return;
            try {
                Files.writeString(file.toPath(), ticketText);
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Could not save ticket: " + ex.getMessage());
                UiTheme.apply(err.getDialogPane());
                err.showAndWait();
            }
        });
    }
}
