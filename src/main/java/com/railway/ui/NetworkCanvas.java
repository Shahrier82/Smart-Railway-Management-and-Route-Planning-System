package com.railway.ui;

import com.railway.graph.RailwayGraph;
import com.railway.graph.RouteResult;
import com.railway.model.RouteEdge;
import com.railway.model.Station;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws the railway network: stations as circles, track segments as
 * connecting lines, and the computed route highlighted on top.
 * <p>
 * Two things make this behave well at any window size, which a plain
 * {@code Canvas} does not do out of the box:
 * <ol>
 *   <li>It overrides {@link #isResizable()}/{@link #resize(double, double)}
 *       so its containing pane can actually shrink or grow it - a bare
 *       {@code Canvas} ignores layout and stays whatever size it was
 *       constructed with, which is what let it spill outside the window.</li>
 *   <li>Station coordinates from the data are never drawn at their raw
 *       values. They're remapped every redraw from the data's own
 *       bounding box onto whatever the canvas's current size actually is
 *       (preserving aspect ratio and centering the result), so every
 *       station stays fully visible and correctly framed regardless of
 *       window size.</li>
 * </ol>
 */
public class NetworkCanvas extends Canvas {

    private static final double STATION_RADIUS = 6.5;
    private static final double MARGIN = 46;
    // Palette mirrors app.css: warm ticket-paper surface, taupe idle track,
    // deep navy station markers, and a single signal-red accent for the
    // computed route so it reads unmistakably as "the path taken".
    private static final Color TRACK_COLOR = Color.web("#B7AC96");
    private static final Color STATION_FILL = Color.web("#101A30");
    private static final Color STATION_RING = Color.web("#F6F1E6");
    private static final Color STATION_LABEL_COLOR = Color.web("#241F1B");
    private static final Color ROUTE_COLOR = Color.web("#C13B23");
    private static final Color ROUTE_STATION_FILL = Color.web("#C13B23");
    private static final Color BACKGROUND = Color.web("#F6F1E6");

    private RailwayGraph graph;
    private RouteResult highlightedRoute;

    // Data-space bounding box of all station coordinates, recomputed whenever the graph changes.
    private double dataMinX, dataMaxX, dataMinY, dataMaxY;

    public NetworkCanvas(double width, double height) {
        super(width, height);
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    @Override
    public double minWidth(double height) {
        return 240;
    }

    @Override
    public double minHeight(double width) {
        return 200;
    }

    @Override
    public double maxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    public double maxHeight(double width) {
        return Double.MAX_VALUE;
    }

    @Override
    public void resize(double width, double height) {
        super.setWidth(width);
        super.setHeight(height);
        redraw();
    }

    public void setGraph(RailwayGraph graph) {
        this.graph = graph;
        computeDataBounds();
        redraw();
    }

    public void setHighlightedRoute(RouteResult route) {
        this.highlightedRoute = route;
        redraw();
    }

    public void clearHighlight() {
        this.highlightedRoute = null;
        redraw();
    }

    private void computeDataBounds() {
        dataMinX = dataMinY = Double.MAX_VALUE;
        dataMaxX = dataMaxY = -Double.MAX_VALUE;
        if (graph == null) return;
        for (Station s : graph.getAllStations()) {
            dataMinX = Math.min(dataMinX, s.getX());
            dataMaxX = Math.max(dataMaxX, s.getX());
            dataMinY = Math.min(dataMinY, s.getY());
            dataMaxY = Math.max(dataMaxY, s.getY());
        }
        if (dataMinX > dataMaxX) { // no stations at all
            dataMinX = 0; dataMaxX = 1; dataMinY = 0; dataMaxY = 1;
        }
    }

    /** Uniform scale mapping the data's bounding box into the canvas's current drawable area, centered. */
    private double currentScale() {
        double drawableW = Math.max(1, getWidth() - 2 * MARGIN);
        double drawableH = Math.max(1, getHeight() - 2 * MARGIN);
        double spanX = Math.max(dataMaxX - dataMinX, 1);
        double spanY = Math.max(dataMaxY - dataMinY, 1);
        return Math.min(drawableW / spanX, drawableH / spanY);
    }

    private double mapX(double dataX, double scale) {
        double spanX = Math.max(dataMaxX - dataMinX, 1);
        double drawableW = Math.max(1, getWidth() - 2 * MARGIN);
        double offsetX = MARGIN + (drawableW - spanX * scale) / 2;
        return offsetX + (dataX - dataMinX) * scale;
    }

    private double mapY(double dataY, double scale) {
        double spanY = Math.max(dataMaxY - dataMinY, 1);
        double drawableH = Math.max(1, getHeight() - 2 * MARGIN);
        double offsetY = MARGIN + (drawableH - spanY * scale) / 2;
        return offsetY + (dataY - dataMinY) * scale;
    }

    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, getWidth(), getHeight());

        if (graph == null) {
            return;
        }

        double scale = currentScale();
        drawBaseNetwork(gc, scale);
        if (highlightedRoute != null && highlightedRoute.isFound()) {
            drawHighlightedRoute(gc, highlightedRoute, scale);
        }
        drawStations(gc, scale);
    }

    private void drawBaseNetwork(GraphicsContext gc, double scale) {
        gc.setStroke(TRACK_COLOR);
        gc.setLineWidth(2);
        Set<String> drawnPairs = new HashSet<>();

        for (Station station : graph.getAllStations()) {
            for (RouteEdge edge : graph.getNeighbors(station.getId())) {
                String key = pairKey(edge.getFromStationId(), edge.getToStationId());
                if (!drawnPairs.add(key)) continue; // avoid drawing both directions twice

                Station from = graph.getStation(edge.getFromStationId());
                Station to = graph.getStation(edge.getToStationId());
                if (from == null || to == null) continue;
                gc.strokeLine(mapX(from.getX(), scale), mapY(from.getY(), scale),
                        mapX(to.getX(), scale), mapY(to.getY(), scale));
            }
        }
    }

    private void drawHighlightedRoute(GraphicsContext gc, RouteResult route, double scale) {
        List<Station> path = route.getPath();
        gc.setStroke(ROUTE_COLOR);
        gc.setLineWidth(4);
        for (int i = 0; i < path.size() - 1; i++) {
            Station a = path.get(i);
            Station b = path.get(i + 1);
            gc.strokeLine(mapX(a.getX(), scale), mapY(a.getY(), scale),
                    mapX(b.getX(), scale), mapY(b.getY(), scale));
        }
    }

    private void drawStations(GraphicsContext gc, double scale) {
        Set<Integer> onRoute = new HashSet<>();
        if (highlightedRoute != null && highlightedRoute.isFound()) {
            for (Station s : highlightedRoute.getPath()) {
                onRoute.add(s.getId());
            }
        }

        double width = getWidth();
        for (Station station : graph.getAllStations()) {
            boolean highlighted = onRoute.contains(station.getId());
            double r = highlighted ? STATION_RADIUS + 2 : STATION_RADIUS;
            double x = mapX(station.getX(), scale);
            double y = mapY(station.getY(), scale);

            // Ring first (ticket-punch look), then a solid inner disc.
            gc.setFill(STATION_RING);
            gc.fillOval(x - r - 2, y - r - 2, (r + 2) * 2, (r + 2) * 2);
            gc.setFill(highlighted ? ROUTE_STATION_FILL : STATION_FILL);
            gc.fillOval(x - r, y - r, r * 2, r * 2);
            gc.setStroke(highlighted ? ROUTE_STATION_FILL : STATION_FILL);
            gc.setLineWidth(1);
            gc.strokeOval(x - r - 2, y - r - 2, (r + 2) * 2, (r + 2) * 2);

            // Flip the label to the left of the dot near the right edge so it
            // never runs off the canvas, instead of always drawing rightward.
            gc.setFill(STATION_LABEL_COLOR);
            gc.setFont(Font.font("Georgia", highlighted ? FontWeight.BOLD : FontWeight.NORMAL, 11.5));
            boolean nearRightEdge = x > width * 0.62;
            if (nearRightEdge) {
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(station.getName(), x - r - 6, y + 4);
            } else {
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText(station.getName(), x + r + 6, y + 4);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT); // restore default for any other drawing
    }

    private String pairKey(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }
}
