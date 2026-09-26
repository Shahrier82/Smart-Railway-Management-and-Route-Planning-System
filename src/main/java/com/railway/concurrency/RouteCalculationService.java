package com.railway.concurrency;

import com.railway.graph.DijkstraRoutePlanner;
import com.railway.graph.RailwayGraph;
import com.railway.graph.RouteResult;
import com.railway.graph.RouteWeight;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JavaFX {@link Service} that runs Dijkstra route computation on a
 * dedicated background thread pool instead of the JavaFX Application
 * Thread. The UI stays responsive even while computing routes over a
 * large network, and progress/state can be observed via the Service's
 * own properties (running, message, value, etc.) which are automatically
 * marshalled back to the UI thread by the JavaFX concurrency framework.
 * <p>
 * Call {@link #configure(RailwayGraph, int, int, RouteWeight)} then
 * {@link #restart()} (or {@link #start()} the first time) to run a
 * computation; the service can be reused for subsequent searches.
 */
public class RouteCalculationService extends Service<RouteResult> {

    private static final AtomicInteger THREAD_COUNT = new AtomicInteger(1);

    private RailwayGraph graph;
    private int sourceId;
    private int destinationId;
    private RouteWeight weight = RouteWeight.DISTANCE;

    public RouteCalculationService() {
        // Use a small daemon-thread pool so route calculations run
        // concurrently with other background work (data loading, live
        // clock updates) without blocking each other or preventing JVM exit.
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "route-calc-" + THREAD_COUNT.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        Executor executor = Executors.newFixedThreadPool(2, daemonFactory);
        setExecutor(executor);
    }

    public void configure(RailwayGraph graph, int sourceId, int destinationId, RouteWeight weight) {
        this.graph = graph;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.weight = weight;
    }

    @Override
    protected Task<RouteResult> createTask() {
        final RailwayGraph capturedGraph = graph;
        final int capturedSource = sourceId;
        final int capturedDest = destinationId;
        final RouteWeight capturedWeight = weight;

        return new Task<>() {
            @Override
            protected RouteResult call() throws Exception {
                updateMessage("Calculating route...");
                DijkstraRoutePlanner planner =
                        new DijkstraRoutePlanner(capturedGraph, capturedSource, capturedDest, capturedWeight);
                RouteResult result = planner.call();
                updateMessage(result.isFound() ? "Route found." : "No route found.");
                return result;
            }
        };
    }
}
