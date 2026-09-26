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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws the railway network: stations as circles positioned by their
 * stored (x, y) coordinates, and track segments as connecting lines.
 * When a route is computed, the stations/edges on that path are
 * re-drawn highlighted in a distinct color on top of the base network.
 */
public class NetworkCanvas extends Canvas {

    private static final double STATION_RADIUS = 6.5;
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

    public NetworkCanvas(double width, double height) {
        super(width, height);
    }

    public void setGraph(RailwayGraph graph) {
        this.graph = graph;
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

    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, getWidth(), getHeight());

        if (graph == null) {
            return;
        }

        drawBaseNetwork(gc);
        if (highlightedRoute != null && highlightedRoute.isFound()) {
            drawHighlightedRoute(gc, highlightedRoute);
        }
        drawStations(gc);
    }

    private void drawBaseNetwork(GraphicsContext gc) {
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
                gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
            }
        }
    }

    private void drawHighlightedRoute(GraphicsContext gc, RouteResult route) {
        List<Station> path = route.getPath();
        gc.setStroke(ROUTE_COLOR);
        gc.setLineWidth(4);
        for (int i = 0; i < path.size() - 1; i++) {
            Station a = path.get(i);
            Station b = path.get(i + 1);
            gc.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
        }
    }

    private void drawStations(GraphicsContext gc) {
        Set<Integer> onRoute = new HashSet<>();
        if (highlightedRoute != null && highlightedRoute.isFound()) {
            for (Station s : highlightedRoute.getPath()) {
                onRoute.add(s.getId());
            }
        }

        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 11.5));
        for (Station station : graph.getAllStations()) {
            boolean highlighted = onRoute.contains(station.getId());
            double r = highlighted ? STATION_RADIUS + 2 : STATION_RADIUS;

            // Ring first (ticket-punch look), then a solid inner disc.
            gc.setFill(STATION_RING);
            gc.fillOval(station.getX() - r - 2, station.getY() - r - 2, (r + 2) * 2, (r + 2) * 2);
            gc.setFill(highlighted ? ROUTE_STATION_FILL : STATION_FILL);
            gc.fillOval(station.getX() - r, station.getY() - r, r * 2, r * 2);
            gc.setStroke(highlighted ? ROUTE_STATION_FILL : STATION_FILL);
            gc.setLineWidth(1);
            gc.strokeOval(station.getX() - r - 2, station.getY() - r - 2, (r + 2) * 2, (r + 2) * 2);

            gc.setFill(STATION_LABEL_COLOR);
            gc.setFont(Font.font("Georgia", highlighted ? FontWeight.BOLD : FontWeight.NORMAL, 11.5));
            gc.fillText(station.getName(), station.getX() + r + 6, station.getY() + 4);
        }
    }

    private String pairKey(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }
}
