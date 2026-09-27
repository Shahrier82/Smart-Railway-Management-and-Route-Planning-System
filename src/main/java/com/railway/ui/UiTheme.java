package com.railway.ui;

import javafx.scene.control.DialogPane;

/**
 * JavaFX {@code Dialog}/{@code Alert} windows own a separate {@code Scene}
 * from the main application window, so they don't automatically pick up
 * the stylesheet applied to it. Every dialog in this app calls
 * {@link #apply(DialogPane)} so popups stay visually consistent with the
 * rest of the interface instead of falling back to default JavaFX styling.
 */
public final class UiTheme {

    private static String cachedStylesheetUrl;

    private UiTheme() {
    }

    public static String stylesheet() {
        if (cachedStylesheetUrl == null) {
            cachedStylesheetUrl = UiTheme.class.getResource("/styles/app.css").toExternalForm();
        }
        return cachedStylesheetUrl;
    }

    public static void apply(DialogPane pane) {
        if (pane != null && !pane.getStylesheets().contains(stylesheet())) {
            pane.getStylesheets().add(stylesheet());
        }
    }
}
