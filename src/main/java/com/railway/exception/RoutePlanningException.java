package com.railway.exception;

/**
 * Thrown when a route cannot even be attempted - as opposed to a normal
 * "no path exists" result (which {@link com.railway.graph.RouteResult}
 * represents without an exception). Examples: the network graph hasn't
 * been loaded yet, or a station id doesn't belong to the current graph.
 */
public class RoutePlanningException extends Exception {

    public RoutePlanningException(String message) {
        super(message);
    }

    public RoutePlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
