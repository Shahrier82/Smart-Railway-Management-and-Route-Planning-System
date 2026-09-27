package com.railway.ui;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * A small self-contained "now scrolling" banner, styled after a real
 * railway platform announcement/departure display: text glides in from
 * the right and scrolls off to the left, then the next announcement
 * begins. Content is driven by binding {@link #textProperty()}-like usage
 * via {@link #bindTo(ObservableValue)}.
 */
public class AnnouncementTicker extends StackPane {

    private final Label label = new Label();
    private TranslateTransition transition;

    public AnnouncementTicker() {
        getStyleClass().add("announcement-ticker");
        label.getStyleClass().add("announcement-text");
        setAlignment(Pos.CENTER_LEFT);
        getChildren().add(label);

        // Clip to the ticker's own bounds so scrolling text doesn't spill
        // into neighboring components.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        setMinHeight(Region.USE_PREF_SIZE);
        setPrefHeight(30);
    }

    /** Binds this ticker's displayed text to an external observable (e.g. AnnouncementService's property). */
    public void bindTo(ObservableValue<String> source) {
        source.addListener((obs, oldVal, newVal) -> setAnnouncement(newVal));
        setAnnouncement(source.getValue());
    }

    public void setAnnouncement(String text) {
        label.setText(text == null ? "" : text);
        restartScroll();
    }

    private void restartScroll() {
        if (transition != null) {
            transition.stop();
        }
        // Start just past the right edge of the visible area, end just past the left edge.
        double startX = Math.max(getWidth(), 300);
        double endX = -(label.prefWidth(-1) + 40);

        label.setTranslateX(startX);
        transition = new TranslateTransition(Duration.seconds(Math.max(6, label.getText().length() * 0.12)), label);
        transition.setFromX(startX);
        transition.setToX(endX);
        transition.setInterpolator(Interpolator.LINEAR);
        transition.play();
    }
}
