package com.railway.ui;

import com.railway.concurrency.AnnouncementService;
import com.railway.concurrency.LiveClockService;
import com.railway.concurrency.NetworkDataLoaderService;
import com.railway.concurrency.RouteCalculationService;
import com.railway.concurrency.SeatAvailabilityService;
import com.railway.db.BookingDao;
import com.railway.db.DatabaseManager;
import com.railway.exception.RailwayDataException;
import com.railway.graph.RailwayGraph;
import com.railway.graph.RouteResult;
import com.railway.graph.RouteWeight;
import com.railway.graph.TrainItineraryPlanner;
import com.railway.graph.TrainLeg;
import com.railway.json.JsonDataManager;
import com.railway.model.Booking;
import com.railway.model.Station;
import com.railway.model.Train;
import com.railway.util.NetworkStatistics;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds and controls the main application window. Wires the SQLite
 * database, JSON import/export, the graph route-planning engine and the
 * background concurrency services together with the JavaFX UI.
 */
public class MainView {

    private final Stage stage;
    private final DatabaseManager db;
    private final BookingDao bookingDao;
    private final JsonDataManager jsonDataManager = new JsonDataManager();
    private final TrainItineraryPlanner itineraryPlanner;

    // Background services
    private final NetworkDataLoaderService dataLoaderService;
    private final RouteCalculationService routeCalculationService = new RouteCalculationService();
    private final LiveClockService liveClockService = new LiveClockService();
    private final SeatAvailabilityService seatAvailabilityService = new SeatAvailabilityService();
    private final AnnouncementService announcementService = new AnnouncementService();
    // General-purpose pool for one-off background jobs (JSON import/export, itinerary lookups, bookings).
    private final ExecutorService generalExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "railway-io-worker");
        t.setDaemon(true);
        return t;
    });

    private RailwayGraph graph;
    private RouteResult lastRouteResult;
    private List<TrainLeg> lastItineraryLegs = List.of();

    // UI components
    private final ComboBox<Station> fromCombo = new ComboBox<>();
    private final ComboBox<Station> toCombo = new ComboBox<>();
    private final ComboBox<RouteWeight> weightCombo = new ComboBox<>();
    private final Button findRouteButton = new Button("Find Route");
    private final Label statusLabel = new Label("Ready.");
    private final Label clockLabel = new Label("--:--:--");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final ListView<Station> stationListView = new ListView<>();
    private final TextField stationSearchField = new TextField();
    private final NetworkCanvas networkCanvas = new NetworkCanvas(760, 520);
    private final NetworkStatsBar networkStatsBar = new NetworkStatsBar();
    private final AnnouncementTicker announcementTicker = new AnnouncementTicker();

    private final Label resultDistanceLabel = new Label("-");
    private final Label resultTimeLabel = new Label("-");
    private final Label resultFareLabel = new Label("-");
    private final ListView<String> routeStopsListView = new ListView<>();
    private final Label itinerarySummaryLabel = new Label("Select stations and find a route to see the trains you'd need.");
    private final TableView<TrainLeg> trainTable = new TableView<>();
    private final Button bookTicketButton = new Button("Book Ticket");

    public MainView(Stage stage, DatabaseManager db, BookingDao bookingDao) {
        this.stage = stage;
        this.db = db;
        this.bookingDao = bookingDao;
        this.dataLoaderService = new NetworkDataLoaderService(db);
        this.itineraryPlanner = new TrainItineraryPlanner(db);
    }

    public void show() {
        BorderPane root = new BorderPane();
        VBox top = new VBox(buildAppHeader(), buildTopBar());
        root.setTop(top);
        root.setLeft(buildStationSidebar());
        root.setCenter(buildCenterMapArea());
        root.setRight(buildResultsPanel());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1200, 760);
        String css = getClass().getResource("/styles/app.css") != null
                ? getClass().getResource("/styles/app.css").toExternalForm() : null;
        if (css != null) scene.getStylesheets().add(css);

        stage.setTitle("Smart Railway Management and Route Planning System");
        stage.setScene(scene);
        stage.show();

        wireBehavior();
        liveClockService.start();
        loadNetworkFromDatabase();
    }

    // ---------------------------------------------------------------
    // UI construction
    // ---------------------------------------------------------------

    private HBox buildAppHeader() {
        Label title = new Label("SMART RAILWAY");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Management & Route Planning System");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titleBlock = new VBox(2, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        announcementTicker.setPrefWidth(420);
        announcementTicker.getStyleClass().add("header-ticker");

        HBox header = new HBox(16, titleBlock, spacer, announcementTicker);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private MenuBar buildTopBar() {
        Menu fileMenu = new Menu("File");
        MenuItem importItem = new MenuItem("Import Network (JSON)...");
        MenuItem exportItem = new MenuItem("Export Network (JSON)...");
        MenuItem exitItem = new MenuItem("Exit");
        importItem.setOnAction(e -> onImportJson());
        exportItem.setOnAction(e -> onExportJson());
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(importItem, exportItem, new SeparatorMenuItem(), exitItem);

        Menu bookingsMenu = new Menu("Bookings");
        MenuItem viewBookingsItem = new MenuItem("View My Bookings...");
        viewBookingsItem.setOnAction(e -> onViewBookings());
        bookingsMenu.getItems().add(viewBookingsItem);

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAbout());
        helpMenu.getItems().add(aboutItem);

        MenuBar menuBar = new MenuBar(fileMenu, bookingsMenu, helpMenu);
        return menuBar;
    }

    private VBox buildStationSidebar() {
        Label title = new Label("Stations");
        title.getStyleClass().add("panel-title");

        stationSearchField.setPromptText("Search stations...");
        stationListView.setPrefWidth(220);
        VBox.setVgrow(stationListView, Priority.ALWAYS);

        VBox box = new VBox(10, title, stationSearchField, stationListView);
        box.getStyleClass().addAll("panel", "sidebar");
        box.setPadding(new Insets(14));
        box.setPrefWidth(240);
        return box;
    }

    private VBox buildCenterMapArea() {
        Label mapTitle = new Label("Railway Network Map");
        mapTitle.getStyleClass().add("panel-title");

        Label fromLabel = new Label("From:");
        Label toLabel = new Label("To:");
        Label optimizeLabel = new Label("Optimize for:");
        fromLabel.getStyleClass().add("field-label");
        toLabel.getStyleClass().add("field-label");
        optimizeLabel.getStyleClass().add("field-label");

        HBox controls = new HBox(10, fromLabel, fromCombo, toLabel, toCombo,
                optimizeLabel, weightCombo, findRouteButton, progressIndicator);
        controls.getStyleClass().add("controls-bar");
        controls.setAlignment(Pos.CENTER_LEFT);

        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        weightCombo.setItems(FXCollections.observableArrayList(RouteWeight.values()));
        weightCombo.getSelectionModel().select(RouteWeight.DISTANCE);

        StackPane canvasWrapper = new StackPane(networkCanvas);
        canvasWrapper.getStyleClass().add("map-frame");

        networkStatsBar.getStyleClass().add("stats-bar-frame");

        VBox box = new VBox(10, mapTitle, controls, canvasWrapper, networkStatsBar);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(16));
        VBox.setVgrow(canvasWrapper, Priority.ALWAYS);
        return box;
    }

    private VBox buildResultsPanel() {
        Label title = new Label("Route Details");
        title.getStyleClass().add("panel-title");

        resultDistanceLabel.getStyleClass().add("stat-value");
        resultTimeLabel.getStyleClass().add("stat-value");
        resultFareLabel.getStyleClass().add("stat-value");
        Label distanceCaption = new Label("Distance");
        Label timeCaption = new Label("Travel time");
        Label fareCaption = new Label("Estimated fare");
        distanceCaption.getStyleClass().add("field-label");
        timeCaption.getStyleClass().add("field-label");
        fareCaption.getStyleClass().add("field-label");

        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(10);
        summaryGrid.setVgap(6);
        summaryGrid.addRow(0, distanceCaption, resultDistanceLabel);
        summaryGrid.addRow(1, timeCaption, resultTimeLabel);
        summaryGrid.addRow(2, fareCaption, resultFareLabel);

        Label stopsTitle = new Label("Stops along route");
        stopsTitle.getStyleClass().add("section-label");
        routeStopsListView.setPrefHeight(160);

        Label trainsTitle = new Label("Trains for this route");
        trainsTitle.getStyleClass().add("section-label");
        itinerarySummaryLabel.setWrapText(true);
        itinerarySummaryLabel.getStyleClass().add("field-label");
        buildTrainTable();
        VBox.setVgrow(trainTable, Priority.ALWAYS);

        bookTicketButton.getStyleClass().add("book-button");
        bookTicketButton.setDisable(true);
        bookTicketButton.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(12, title, summaryGrid, stopsTitle, routeStopsListView,
                trainsTitle, itinerarySummaryLabel, trainTable, bookTicketButton);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(16));
        box.setPrefWidth(320);
        return box;
    }

    private void buildTrainTable() {
        TableColumn<TrainLeg, String> legCol = new TableColumn<>("Leg");
        legCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getFrom().getCode() + " \u2192 " + data.getValue().getTo().getCode()));
        legCol.setPrefWidth(90);

        TableColumn<TrainLeg, String> trainCol = new TableColumn<>("Train");
        trainCol.setCellValueFactory(data -> {
            TrainLeg leg = data.getValue();
            String text = leg.hasTrain()
                    ? leg.getTrain().getNumber() + " - " + leg.getTrain().getName()
                    : "No scheduled train (local transfer needed)";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        trainCol.setPrefWidth(230);
        trainCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) {
                    setText(null);
                    getStyleClass().remove("leg-gap");
                } else {
                    setText(text);
                    TrainLeg leg = getTableRow() != null ? (TrainLeg) getTableRow().getItem() : null;
                    if (leg != null && !leg.hasTrain()) {
                        if (!getStyleClass().contains("leg-gap")) getStyleClass().add("leg-gap");
                    } else {
                        getStyleClass().remove("leg-gap");
                    }
                }
            }
        });

        trainTable.getColumns().addAll(legCol, trainCol);
        Label placeholder = new Label("Select stations and find a route to see direct trains.");
        placeholder.getStyleClass().add("empty-placeholder");
        trainTable.setPlaceholder(placeholder);
    }

    private HBox buildStatusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label clockCaption = new Label("SYSTEM TIME");
        clockCaption.getStyleClass().add("clock-caption");
        statusLabel.getStyleClass().add("status-text");
        clockLabel.getStyleClass().add("clock-value");
        clockLabel.textProperty().bind(liveClockService.currentTimeProperty());

        HBox bar = new HBox(10, statusLabel, spacer, clockCaption, clockLabel);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------
    // Behavior wiring
    // ---------------------------------------------------------------

    private void wireBehavior() {
        findRouteButton.setOnAction(e -> onFindRoute());
        bookTicketButton.setOnAction(e -> onBookTicket());

        stationSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterStationList(newVal));

        stationListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Station station, boolean empty) {
                super.updateItem(station, empty);
                setText(empty || station == null ? null : station.toString());
            }
        });

        // Clicking a station in the sidebar quick-fills the "From" field.
        stationListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                fromCombo.getSelectionModel().select(newVal);
            }
        });

        // Double-clicking a station shows its connections and scheduled trains.
        stationListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Station selected = stationListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onShowStationDetails(selected);
                }
            }
        });

        routeCalculationService.setOnRunning(e -> {
            progressIndicator.setVisible(true);
            statusLabel.setText("Calculating route in background thread...");
            itinerarySummaryLabel.setText("Looking up trains for this route...");
            findRouteButton.setDisable(true);
            bookTicketButton.setDisable(true);
        });
        routeCalculationService.setOnSucceeded(e -> {
            progressIndicator.setVisible(false);
            findRouteButton.setDisable(false);
            RouteResult result = routeCalculationService.getValue();
            lastRouteResult = result;
            displayRouteResult(result);
            if (result != null && result.isFound()) {
                lookupTrainItinerary(result.getPath());
            } else {
                trainTable.setItems(FXCollections.observableArrayList());
                itinerarySummaryLabel.setText("Select stations and find a route to see the trains you'd need.");
                bookTicketButton.setDisable(true);
            }
        });
        routeCalculationService.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            findRouteButton.setDisable(false);
            statusLabel.setText("Route calculation failed: " +
                    (routeCalculationService.getException() != null
                            ? routeCalculationService.getException().getMessage() : "unknown error"));
        });
    }

    private void loadNetworkFromDatabase() {
        statusLabel.setText("Loading railway network from database...");
        progressIndicator.setVisible(true);

        dataLoaderService.setOnSucceeded(e -> {
            graph = dataLoaderService.getValue();
            progressIndicator.setVisible(false);
            statusLabel.setText("Loaded " + graph.stationCount() + " stations.");
            populateStationControls();
            networkCanvas.setGraph(graph);
            networkStatsBar.update(NetworkStatistics.compute(graph));
            startLiveServices();
        });
        dataLoaderService.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            statusLabel.setText("Failed to load network: " +
                    (dataLoaderService.getException() != null
                            ? dataLoaderService.getException().getMessage() : "unknown error"));
        });
        dataLoaderService.restart();
    }

    /** Fetches the train list (off the UI thread) and starts the seat-availability and announcement services. */
    private void startLiveServices() {
        generalExecutor.submit(() -> {
            try {
                List<Train> trains = db.getAllTrains();
                List<Integer> trainIds = trains.stream().map(Train::getId).toList();
                Platform.runLater(() -> {
                    seatAvailabilityService.start(trainIds);
                    announcementService.start(trains);
                    announcementTicker.bindTo(announcementService.announcementProperty());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Could not start live services: " + ex.getMessage()));
            }
        });
    }

    private void populateStationControls() {
        ObservableList<Station> stations = FXCollections.observableArrayList(graph.getAllStations());
        stations.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        stationListView.setItems(stations);
        fromCombo.setItems(FXCollections.observableArrayList(stations));
        toCombo.setItems(FXCollections.observableArrayList(stations));
    }

    private void filterStationList(String query) {
        if (graph == null) return;
        ObservableList<Station> filtered = FXCollections.observableArrayList();
        String lower = query == null ? "" : query.toLowerCase();
        for (Station s : graph.getAllStations()) {
            if (s.getName().toLowerCase().contains(lower) || s.getCode().toLowerCase().contains(lower)) {
                filtered.add(s);
            }
        }
        filtered.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        stationListView.setItems(filtered);
    }

    private void onFindRoute() {
        Station from = fromCombo.getSelectionModel().getSelectedItem();
        Station to = toCombo.getSelectionModel().getSelectedItem();
        RouteWeight weight = weightCombo.getSelectionModel().getSelectedItem();

        if (from == null || to == null) {
            statusLabel.setText("Please select both a departure and destination station.");
            return;
        }
        if (graph == null) {
            statusLabel.setText("Network is still loading, please wait.");
            return;
        }

        routeCalculationService.configure(graph, from.getId(), to.getId(), weight);
        routeCalculationService.restart(); // runs on the service's background thread pool;
        // the itinerary lookup follows once the route (and its path) is known - see wireBehavior().
    }

    /**
     * Works out which train(s) cover the just-computed route and where a
     * passenger would need to change, off the UI thread (it queries the DB
     * for every train's schedule), then updates the results table.
     */
    private void lookupTrainItinerary(List<Station> path) {
        generalExecutor.submit(() -> {
            try {
                List<TrainLeg> legs = itineraryPlanner.plan(path);
                Platform.runLater(() -> displayItinerary(legs));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    itinerarySummaryLabel.setText("Could not look up trains: " + ex.getMessage());
                    trainTable.setItems(FXCollections.observableArrayList());
                });
            }
        });
    }

    private void displayItinerary(List<TrainLeg> legs) {
        lastItineraryLegs = legs;
        trainTable.setItems(FXCollections.observableArrayList(legs));
        bookTicketButton.setDisable(legs.isEmpty());

        long changesNeeded = legs.stream().filter(TrainLeg::hasTrain).count();
        boolean hasGap = legs.stream().anyMatch(l -> !l.hasTrain());

        if (legs.isEmpty()) {
            itinerarySummaryLabel.setText("You're already there \u2014 no train needed.");
        } else if (hasGap) {
            itinerarySummaryLabel.setText("No single train covers this whole route, and part of it has no "
                    + "scheduled service in the sample data \u2014 see the highlighted leg below.");
        } else if (changesNeeded == 1) {
            itinerarySummaryLabel.setText("One direct train covers this entire route \u2014 no change needed.");
        } else {
            itinerarySummaryLabel.setText("No single direct train covers this route. You'll need to change "
                    + "trains " + (changesNeeded - 1) + " time(s), across " + changesNeeded + " trains.");
        }
    }

    /** Looks up a station's connections/trains off the UI thread, then shows the details popup. */
    private void onShowStationDetails(Station station) {
        generalExecutor.submit(() -> {
            try {
                List<Train> trainsAtStation = db.getTrainsAtStation(station.getId());
                Platform.runLater(() -> StationDetailsDialog.show(station, graph, trainsAtStation));
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Could not load station details: " + ex.getMessage()));
            }
        });
    }

    private void onBookTicket() {
        if (lastRouteResult == null || !lastRouteResult.isFound()) {
            statusLabel.setText("Find a route before booking a ticket.");
            return;
        }
        BookingDialog.show(stage, lastRouteResult, lastItineraryLegs, bookingDao, seatAvailabilityService, generalExecutor);
    }

    private void onViewBookings() {
        generalExecutor.submit(() -> {
            try {
                List<Booking> bookings = bookingDao.getAllBookings();
                Platform.runLater(() -> BookingsHistoryDialog.show(stage, bookings));
            } catch (RailwayDataException ex) {
                Platform.runLater(() -> statusLabel.setText("Could not load bookings: " + ex.getMessage()));
            }
        });
    }

    private void displayRouteResult(RouteResult result) {
        if (result == null || !result.isFound()) {
            statusLabel.setText("No route found between the selected stations.");
            resultDistanceLabel.setText("-");
            resultTimeLabel.setText("-");
            resultFareLabel.setText("-");
            routeStopsListView.getItems().clear();
            networkCanvas.clearHighlight();
            return;
        }

        statusLabel.setText("Route found (" + result.getOptimizedFor().getLabel() + ").");
        resultDistanceLabel.setText(String.format("%.1f km", result.getTotalDistanceKm()));
        resultTimeLabel.setText(result.getFormattedTime());
        resultFareLabel.setText(String.format("%.2f BDT", result.getTotalFare()));

        ObservableList<String> stops = FXCollections.observableArrayList();
        for (Station s : result.getPath()) {
            stops.add(s.getName() + " (" + s.getCode() + ")");
        }
        routeStopsListView.setItems(stops);

        networkCanvas.setHighlightedRoute(result);
    }

    // ---------------------------------------------------------------
    // JSON import / export (background tasks)
    // ---------------------------------------------------------------

    private void onImportJson() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Railway Network (JSON)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        Task<Void> importTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Importing network from " + file.getName() + "...");
                db.clearAll();
                jsonDataManager.importNetwork(file.toPath(), db);
                return null;
            }
        };
        bindBackgroundTask(importTask, "Import");
        importTask.setOnSucceeded(e -> loadNetworkFromDatabase());
        generalExecutor.submit(importTask);
    }

    private void onExportJson() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Railway Network (JSON)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        chooser.setInitialFileName("railway_network_export.json");
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Exporting network to " + file.getName() + "...");
                jsonDataManager.exportNetwork(file.toPath(), db);
                return null;
            }
        };
        bindBackgroundTask(exportTask, "Export");
        generalExecutor.submit(exportTask);
    }

    private void bindBackgroundTask(Task<Void> task, String label) {
        progressIndicator.setVisible(true);
        task.setOnRunning(e -> statusLabel.textProperty().bind(task.messageProperty()));
        task.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText(label + " completed successfully.");
            progressIndicator.setVisible(false);
        });
        task.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText(label + " failed: " +
                    (task.getException() != null ? task.getException().getMessage() : "unknown error"));
            progressIndicator.setVisible(false);
        });
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Smart Railway Management and Route Planning System");
        alert.setContentText("A JavaFX desktop application for exploring railway networks, "
                + "planning routes with Dijkstra's algorithm, browsing train schedules, and booking tickets.\n\n"
                + "Built with JavaFX, SQLite (JDBC) and JSON, using background threads "
                + "(java.util.concurrent + JavaFX Task/Service) to keep the UI responsive.");
        UiTheme.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    /** Called by the application on shutdown to release background threads cleanly. */
    public void shutdown() {
        liveClockService.stop();
        seatAvailabilityService.stop();
        announcementService.stop();
        generalExecutor.shutdownNow();
    }
}
