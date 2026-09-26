package com.railway.ui;

import com.railway.model.NetworkStats;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A compact horizontal strip of headline network statistics (station
 * count, total track length, busiest junction, average fare per km),
 * shown beneath the network map. Purely presentational - the numbers are
 * computed elsewhere by {@link com.railway.util.NetworkStatistics}.
 */
public class NetworkStatsBar extends HBox {

    public NetworkStatsBar() {
        getStyleClass().add("stats-bar");
        setSpacing(24);
        setAlignment(Pos.CENTER_LEFT);
    }

    public void update(NetworkStats stats) {
        getChildren().clear();
        if (stats == null) return;

        getChildren().add(statBlock("STATIONS", String.valueOf(stats.getStationCount())));
        getChildren().add(divider());
        getChildren().add(statBlock("TRACK SEGMENTS", String.valueOf(stats.getTrackSegmentCount())));
        getChildren().add(divider());
        getChildren().add(statBlock("TOTAL NETWORK", String.format("%.0f km", stats.getTotalTrackKm())));
        getChildren().add(divider());

        String busiestName = stats.getBusiestStation() != null ? stats.getBusiestStation().getName() : "-";
        getChildren().add(statBlock("BUSIEST JUNCTION",
                busiestName + " (" + stats.getBusiestStationConnections() + " lines)"));
        getChildren().add(divider());
        getChildren().add(statBlock("AVG. FARE", String.format("%.2f BDT/km", stats.getAverageFarePerKm())));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
    }

    private VBox statBlock(String caption, String value) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stats-caption");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stats-value");
        VBox box = new VBox(1, valueLabel, captionLabel);
        return box;
    }

    private Region divider() {
        Region r = new Region();
        r.getStyleClass().add("stats-divider");
        r.setPrefWidth(1);
        r.setMaxHeight(28);
        r.setMinHeight(28);
        return r;
    }
}
