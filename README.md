# Smart Railway Management and Route Planning System

A Java desktop application (JavaFX GUI) that simulates and manages a
railway network: stations, track segments, train schedules, Dijkstra-based
route planning with distance/time/fare optimization, multi-train itinerary
planning, live seat availability, station announcements, and ticket booking.

Built to demonstrate:
- **JavaFX** — interactive desktop UI with a `Canvas`-drawn network map,
  a scrolling announcement ticker, live stats, and modal dialogs
- **Concurrency & multithreading** — `javafx.concurrent.Task`/`Service`
  plus raw `java.util.concurrent` (`ExecutorService`,
  `ScheduledExecutorService`) so the UI never freezes, with four
  independent background services running side by side
- **SQLite** (via `sqlite-jdbc`) — persistent storage for the network and
  for saved ticket bookings (a separate DAO/table)
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
their stop schedules. Saved bookings live in their own `bookings` table in
the same database file.

## Using the app

1. Wait for **"Loaded N stations."** in the status bar (network loads
   from SQLite on a background thread at startup). Once loaded, the
   **network stats strip** under the map shows station/track counts, the
   busiest junction, and average fare per km, and the header's
   **announcement ticker** starts cycling simulated platform PA messages.
2. Pick a **From** and **To** station (or click one in the left sidebar
   station list, which fills "From"). **Double-click** a station in the
   sidebar to see its direct connections and which trains stop there.
3. Choose what to optimize for: shortest **distance**, fastest **time**,
   or cheapest **fare** — two route pairs in the sample data (Dhaka to
   Chittagong, Rajshahi to Rangpur) genuinely change route depending on
   this choice.
4. Click **Find Route** — Dijkstra's algorithm runs on a background
   thread pool; the map highlights the resulting path in red, and the
   right panel shows total distance/time/fare and the ordered stop list.
5. **Trains for this route** shows a leg-by-leg itinerary: if one train
   covers the whole route it says so; if you'd need to change trains
   partway, each leg lists which train to take and where the change
   happens; and any stretch with no scheduled service is flagged as a gap.
6. Click **Book Ticket** to open the booking dialog: pick a seat class
   (Shovon Chair, Snigdha, AC Seat, AC Berth - each with its own fare
   multiplier) and seat count. When exactly one train covers the whole
   route, a **live seat-availability count** is shown, fed by a background
   simulation that fluctuates every few seconds. Confirming saves the
   booking to SQLite and shows a text e-ticket you can save to a `.txt` file.
7. **Bookings -> View My Bookings** lists every saved booking in a table.
8. Use **File -> Import Network (JSON)** to replace the whole network
   from a JSON file in the same shape as `sample_network.json`, or
   **File -> Export Network (JSON)** to dump the current database back
   out to JSON. Both run on background threads with progress shown in
   the status bar.

## Project structure

```
src/main/java/com/railway/
  Main.java                        JavaFX Application entry point
  model/                           Plain data classes
    Station.java, RouteEdge.java, Train.java, ScheduleEntry.java
    SeatClass.java                 Seat classes (Shovon/Snigdha/AC...) with fare multipliers
    Booking.java                   A saved ticket reservation
    NetworkStats.java              Computed network-wide statistics snapshot
  db/
    DatabaseManager.java           SQLite access for network data (schema + CRUD)
    BookingDao.java                Separate SQLite DAO for ticket bookings
  graph/
    RailwayGraph.java              In-memory adjacency-list graph
    RouteWeight.java                DISTANCE / TIME / FARE enum
    RouteResult.java                Immutable pathfinding result
    DijkstraRoutePlanner.java       Callable<RouteResult>, runs off-thread
    TrainLeg.java                   One leg of a multi-train itinerary
    TrainItineraryPlanner.java      Works out which train(s) cover a route
  json/
    JsonDataManager.java            Import/export network as JSON
  exception/
    RailwayDataException.java       Domain exception for data load/save failures
    RoutePlanningException.java     Domain exception for invalid routing preconditions
  concurrency/
    RouteCalculationService.java    JavaFX Service running Dijkstra on a pool
    NetworkDataLoaderService.java   JavaFX Service loading DB -> graph
    LiveClockService.java           ScheduledExecutorService ticking a clock
    SeatAvailabilityService.java    ScheduledExecutorService simulating live seat counts
    AnnouncementService.java        ScheduledExecutorService cycling PA-style announcements
  util/
    SampleDataLoader.java           Seeds an empty DB from bundled JSON
    FareCalculator.java              Seat-class fare math
    NetworkStatistics.java           Computes NetworkStats from a RailwayGraph
    TicketGenerator.java             Formats a Booking into a text e-ticket
  ui/
    MainView.java                   Builds & wires the whole JavaFX UI
    NetworkCanvas.java              Canvas rendering of the network/route
    AnnouncementTicker.java         Scrolling marquee banner control
    NetworkStatsBar.java            Headline network-statistics strip
    BookingDialog.java              Modal ticket-booking flow
    BookingsHistoryDialog.java      Table of all saved bookings
    StationDetailsDialog.java       Popup: a station's connections & trains
    UiTheme.java                    Attaches app.css to dialogs/alerts
src/main/resources/
  data/sample_network.json          Bundled sample railway network
  styles/app.css                    UI styling
```

## Concurrency design notes

Four background services run independently and concurrently:

- **Route calculation** (`RouteCalculationService`) is a `javafx.concurrent.Service`
  backed by its own daemon-thread `ExecutorService`, so searches never
  block the JavaFX Application Thread and multiple searches can be
  in-flight/cancelled cleanly via `restart()`.
- **Network loading** (`NetworkDataLoaderService`) similarly loads all
  stations/edges from SQLite and builds the in-memory graph off the UI
  thread, since disk I/O can be slow.
- **Live clock** (`LiveClockService`) uses a raw `ScheduledExecutorService`
  ticking every second on its own daemon thread.
- **Seat availability** (`SeatAvailabilityService`) uses a `ScheduledExecutorService`
  to randomly fluctuate each train's simulated seat count every few
  seconds, paired with a small observer pattern (`addListener`/`removeListener`)
  so the booking dialog reacts live without polling.
- **Announcements** (`AnnouncementService`) uses another independent
  `ScheduledExecutorService` to rotate simulated PA messages referencing
  real trains from the loaded network.

All four marshal their updates back to the JavaFX Application Thread via
`Platform.runLater`, and none of them block each other.

Additionally:
- **JSON import/export**, **direct-train/itinerary lookups**, **station
  detail lookups**, and **booking save/list** all run as ad-hoc `Task`s or
  `Runnable`s submitted to a shared `ExecutorService`, keeping database/file
  I/O off the UI thread.
- `DatabaseManager` and `BookingDao` each hold their own SQLite `Connection`
  (both pointed at the same file, with `PRAGMA busy_timeout` set so brief
  cross-connection write contention retries instead of failing) and their
  public methods are `synchronized`, since SQLite connections aren't safe
  for concurrent use from multiple threads at once.

## Extending the project

- Add more stations/trains by editing `sample_network.json` or using
  **File -> Import Network**.
- Swap in a different shortest-path weighting by adding a new
  `RouteWeight` enum value and a case in `DijkstraRoutePlanner.weightOf`.
- Add a new seat class or adjust fare multipliers in `SeatClass`.
- The schema supports multiple trains per station pair - `findDirectTrainsBetween`
  in `DatabaseManager` remains available for simple direct-only lookups,
  while `TrainItineraryPlanner` handles the general multi-leg case used
  by the UI.
