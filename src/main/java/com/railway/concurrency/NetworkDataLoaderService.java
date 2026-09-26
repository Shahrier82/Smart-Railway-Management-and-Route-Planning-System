package com.railway.concurrency;

import com.railway.db.DatabaseManager;
import com.railway.graph.RailwayGraph;
import com.railway.model.RouteEdge;
import com.railway.model.Station;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

/**
 * Loads all stations and edges from SQLite and builds a fresh
 * {@link RailwayGraph} on a background thread. Used both at application
 * startup and whenever the network changes (e.g. after a JSON import),
 * so the potentially slow disk I/O never blocks the JavaFX Application
 * Thread / freezes the interface.
 */
public class NetworkDataLoaderService extends Service<RailwayGraph> {

    private final DatabaseManager db;

    public NetworkDataLoaderService(DatabaseManager db) {
        this.db = db;
    }

    @Override
    protected Task<RailwayGraph> createTask() {
        return new Task<>() {
            @Override
            protected RailwayGraph call() throws Exception {
                updateMessage("Loading stations...");
                var stations = db.getAllStations();

                updateMessage("Loading track segments...");
                var edges = db.getAllEdges();

                updateMessage("Building network graph...");
                return new RailwayGraph(stations, edges);
            }
        };
    }
}
