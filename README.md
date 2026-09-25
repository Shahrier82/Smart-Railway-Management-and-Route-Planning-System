# Smart Railway Management and Route Planning System

A Java desktop application (JavaFX GUI) that simulates and manages a
railway network: stations, track segments, train schedules, and
Dijkstra-based route planning with distance/time/fare optimization.

Built to demonstrate:
- **JavaFX** — interactive desktop UI with a `Canvas`-drawn network map
- **Concurrency & multithreading** — `javafx.concurrent.Task`/`Service`
  plus raw `java.util.concurrent` (`ExecutorService`,
  `ScheduledExecutorService`) so the UI never freezes
- **SQLite** (via `sqlite-jdbc`) — persistent storage for the network
- **JSON** (via `org.json`) — import/export of the entire network

## Requirements

- JDK 17+
- Maven 3.8+
- Internet access on first build (Maven needs to download the JavaFX,
  sqlite-jdbc and org.json dependencies declared in `pom.xml`)

## Build & Run

```bash
# Run directly with the JavaFX Maven plugin
mvn clean javafx:run

# OR build a self-contained runnable jar and run it
mvn clean package
java -jar target/smart-railway-system-1.0.0.jar
```

On first launch the app creates `railway.db` (SQLite file) next to the
jar/working directory and seeds it automatically from the bundled sample
network (`src/main/resources/data/sample_network.json`) — a representative
Bangladesh railway network of 16 stations (Dhaka, Chittagong, Khulna,
Rajshahi, Sylhet, Rangpur, Mymensingh, Jessore, Ishwardi, Akhaura, Comilla,
Feni, Noakhali, Bogura, Dinajpur, Barisal) with 10 named express trains and
their stop schedules.

## Using the app

1. Wait for **"Loaded N stations."** in the status bar (network loads
   from SQLite on a background thread at startup).
2. Pick a **From** and **To** station (or click one in the left sidebar
   station list, which fills "From").
3. Choose what to optimize for: shortest **distance**, fastest **time**,
   or cheapest **fare**.
4. Click **Find Route** — Dijkstra's algorithm runs on a background
   thread pool; the map highlights the resulting path in red, and the
   right panel shows total distance/time/fare, the ordered list of
   stops, and any direct trains that run between the two stations.
5. Use **File → Import Network (JSON)** to replace the whole network
   from a JSON file in the same shape as `sample_network.json`, or
   **File → Export Network (JSON)** to dump the current database back
   out to JSON. Both run on background threads with progress shown in
   the status bar.

## Project structure

```
src/main/java/com/railway/
  Main.java                        JavaFX Application entry point
  model/                           Plain data classes
    Station.java, RouteEdge.java, Train.java, ScheduleEntry.java
  db/
    DatabaseManager.java           All SQLite access (schema + CRUD)
  graph/
    RailwayGraph.java              In-memory adjacency-list graph
    RouteWeight.java               DISTANCE / TIME / FARE enum
    RouteResult.java               Immutable pathfinding result
    DijkstraRoutePlanner.java      Callable<RouteResult>, runs off-thread
  json/
    JsonDataManager.java           Import/export network as JSON
  concurrency/
    RouteCalculationService.java   JavaFX Service running Dijkstra on a pool
    NetworkDataLoaderService.java  JavaFX Service loading DB -> graph
    LiveClockService.java          ScheduledExecutorService ticking clock
  ui/
    MainView.java                  Builds & wires the whole JavaFX UI
    NetworkCanvas.java             Canvas rendering of the network/route
  util/
    SampleDataLoader.java          Seeds an empty DB from bundled JSON
src/main/resources/
  data/sample_network.json         Bundled sample railway network
  styles/app.css                   UI styling
```

## Concurrency design notes

- **Route calculation** (`RouteCalculationService`) is a `javafx.concurrent.Service`
  backed by its own daemon-thread `ExecutorService`, so searches never
  block the JavaFX Application Thread and multiple searches can be
  in-flight/cancelled cleanly via `restart()`.
- **Network loading** (`NetworkDataLoaderService`) similarly loads all
  stations/edges from SQLite and builds the in-memory graph off the UI
  thread, since disk I/O can be slow.
- **Live clock** (`LiveClockService`) uses a raw `ScheduledExecutorService`
  ticking every second on its own daemon thread — independent of the
  JavaFX `Task`/`Service` machinery — and marshals updates back to the
  UI thread with `Platform.runLater`, showing a second, lower-level
  concurrency pattern alongside the higher-level JavaFX services.
- **JSON import/export** and **direct-train lookups** run as ad-hoc
  `Task`s submitted to a shared `ExecutorService`, again keeping
  database/file I/O off the UI thread.
- `DatabaseManager`'s public methods are `synchronized` because a single
  SQLite `Connection` is shared across the UI thread and multiple
  background workers.

## Extending the project

- Add more stations/trains by editing `sample_network.json` or using
  **File → Import Network**.
- Swap in a different shortest-path weighting by adding a new
  `RouteWeight` enum value and a case in `DijkstraRoutePlanner.weightOf`.
- The schema supports multiple trains per station pair — `findDirectTrainsBetween`
  in `DatabaseManager` can be extended to also compute connecting
  (multi-train) itineraries if desired.
