package com.railway.ui;

import com.railway.graph.RailwayGraph;
import com.railway.model.RouteEdge;
import com.railway.model.Station;
import com.railway.model.Train;
import javafx.geometry.Insets;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A read-only popup shown when the user double-clicks a station in the
 * sidebar: its direct track connections (with distance/time/fare) and
 * which trains are scheduled to stop there. All data is gathered by the
 * caller beforehand (off the UI thread where it needs DB access), so this
 * class does no I/O itself - purely presentational.
 */
public final class StationDetailsDialog {

    private StationDetailsDialog() {
    }

    public static void show(Station station, RailwayGraph graph, List<Train> trainsAtStation) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(station.getName());
        dialog.getDialogPane().getStyleClass().add("station-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiTheme.apply(dialog.getDialogPane());

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        Label header = new Label(station.getName() + "  (" + station.getCode() + ")");
        header.getStyleClass().add("dialog-header");

        Label connTitle = new Label("Directly connected stations");
        connTitle.getStyleClass().add("section-label");
        VBox connList = new VBox(4);
        List<RouteEdge> neighbors = graph.getNeighbors(station.getId());
        if (neighbors.isEmpty()) {
            connList.getChildren().add(new Label("No direct connections in the current network."));
        } else {
            for (RouteEdge edge : neighbors) {
                Station other = graph.getStation(edge.getToStationId());
                if (other == null) continue;
                connList.getChildren().add(new Label(String.format("%s  \u2014  %.0f km \u00b7 %d min \u00b7 %.0f BDT",
                        other.getName(), edge.getDistanceKm(), edge.getTravelTimeMinutes(), edge.getBaseFare())));
            }
        }

        Label trainsTitle = new Label("Trains stopping here");
        trainsTitle.getStyleClass().add("section-label");
        VBox trainList = new VBox(4);
        if (trainsAtStation.isEmpty()) {
            trainList.getChildren().add(new Label("No trains currently scheduled to stop here."));
        } else {
            for (Train t : trainsAtStation) {
                trainList.getChildren().add(new Label(t.getNumber() + " - " + t.getName()));
            }
        }

        content.getChildren().addAll(header, connTitle, connList, trainsTitle, trainList);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(380, 420);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }
}
