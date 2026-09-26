package com.railway.db;

import com.railway.model.RouteEdge;
import com.railway.model.ScheduleEntry;
import com.railway.model.Station;
import com.railway.model.Train;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all SQLite persistence for the railway network: stations, track
 * segments (edges), trains and their stop schedules.
 * <p>
 * A single connection is kept open for the lifetime of the application
 * (SQLite is file-based and each connection is cheap to hold). All public
 * methods are synchronized because SQLite connections are not safe for
 * concurrent use from multiple threads at once, and this manager is called
 * both from the JavaFX Application Thread and from background worker
 * threads (route calculation, data import/export, live services).
 */
public class DatabaseManager implements AutoCloseable {

    private final Connection connection;
    private final String dbFilePath;

    public DatabaseManager(String dbFilePath) throws SQLException {
        this.dbFilePath = dbFilePath;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA busy_timeout = 3000;");
        }
        initSchema();
    }

    /** The on-disk path this manager's connection points at, so other DAOs (e.g. BookingDao) can open their own connection to the same file. */
    public String getDbFilePath() {
        return dbFilePath;
    }

    private void initSchema() throws SQLException {
        String stations = """
                CREATE TABLE IF NOT EXISTS stations (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    x    REAL NOT NULL,
                    y    REAL NOT NULL
                );
                """;
        String edges = """
                CREATE TABLE IF NOT EXISTS edges (
                    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_station_id      INTEGER NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
                    to_station_id        INTEGER NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
                    distance_km          REAL NOT NULL,
                    travel_time_minutes  INTEGER NOT NULL,
                    base_fare            REAL NOT NULL
                );
                """;
        String trains = """
                CREATE TABLE IF NOT EXISTS trains (
                    id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    number TEXT NOT NULL UNIQUE,
                    name   TEXT NOT NULL
                );
                """;
        String schedule = """
                CREATE TABLE IF NOT EXISTS schedule (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    train_id       INTEGER NOT NULL REFERENCES trains(id) ON DELETE CASCADE,
                    station_id     INTEGER NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
                    stop_sequence  INTEGER NOT NULL,
                    arrival_time   TEXT,
                    departure_time TEXT
                );
                """;
        try (Statement st = connection.createStatement()) {
            st.execute(stations);
            st.execute(edges);
            st.execute(trains);
            st.execute(schedule);
        }
    }

    public synchronized boolean isEmpty() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM stations")) {
            return rs.next() && rs.getInt("cnt") == 0;
        }
    }

    public synchronized void clearAll() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM schedule");
            st.execute("DELETE FROM edges");
            st.execute("DELETE FROM trains");
            st.execute("DELETE FROM stations");
        }
    }

    // ---------------------------------------------------------------
    // Stations
    // ---------------------------------------------------------------

    public synchronized int insertStation(String code, String name, double x, double y) throws SQLException {
        String sql = "INSERT INTO stations(code, name, x, y) VALUES (?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public synchronized List<Station> getAllStations() throws SQLException {
        List<Station> list = new ArrayList<>();
        String sql = "SELECT id, code, name, x, y FROM stations ORDER BY name";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Station(rs.getInt("id"), rs.getString("code"),
                        rs.getString("name"), rs.getDouble("x"), rs.getDouble("y")));
            }
        }
        return list;
    }

    public synchronized Integer findStationIdByCode(String code) throws SQLException {
        String sql = "SELECT id FROM stations WHERE code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    // ---------------------------------------------------------------
    // Edges (track segments)
    // ---------------------------------------------------------------

    public synchronized void insertEdge(int fromId, int toId, double distanceKm,
                                         int travelTimeMinutes, double baseFare) throws SQLException {
        String sql = "INSERT INTO edges(from_station_id, to_station_id, distance_km, travel_time_minutes, base_fare) " +
                "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, fromId);
            ps.setInt(2, toId);
            ps.setDouble(3, distanceKm);
            ps.setInt(4, travelTimeMinutes);
            ps.setDouble(5, baseFare);
            ps.executeUpdate();
        }
    }

    /** Inserts the edge in both directions so the line can be travelled either way. */
    public synchronized void insertBidirectionalEdge(int stationAId, int stationBId, double distanceKm,
                                                       int travelTimeMinutes, double baseFare) throws SQLException {
        insertEdge(stationAId, stationBId, distanceKm, travelTimeMinutes, baseFare);
        insertEdge(stationBId, stationAId, distanceKm, travelTimeMinutes, baseFare);
    }

    public synchronized List<RouteEdge> getAllEdges() throws SQLException {
        List<RouteEdge> list = new ArrayList<>();
        String sql = "SELECT id, from_station_id, to_station_id, distance_km, travel_time_minutes, base_fare FROM edges";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new RouteEdge(rs.getInt("id"), rs.getInt("from_station_id"),
                        rs.getInt("to_station_id"), rs.getDouble("distance_km"),
                        rs.getInt("travel_time_minutes"), rs.getDouble("base_fare")));
            }
        }
        return list;
    }

    // ---------------------------------------------------------------
    // Trains & schedules
    // ---------------------------------------------------------------

    public synchronized int insertTrain(String number, String name) throws SQLException {
        String sql = "INSERT INTO trains(number, name) VALUES (?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, number);
            ps.setString(2, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public synchronized List<Train> getAllTrains() throws SQLException {
        List<Train> list = new ArrayList<>();
        String sql = "SELECT id, number, name FROM trains ORDER BY number";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Train(rs.getInt("id"), rs.getString("number"), rs.getString("name")));
            }
        }
        return list;
    }

    public synchronized void insertScheduleEntry(int trainId, int stationId, int stopSequence,
                                                  String arrivalTime, String departureTime) throws SQLException {
        String sql = "INSERT INTO schedule(train_id, station_id, stop_sequence, arrival_time, departure_time) " +
                "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, trainId);
            ps.setInt(2, stationId);
            ps.setInt(3, stopSequence);
            ps.setString(4, arrivalTime);
            ps.setString(5, departureTime);
            ps.executeUpdate();
        }
    }

    public synchronized List<ScheduleEntry> getScheduleForTrain(int trainId) throws SQLException {
        List<ScheduleEntry> list = new ArrayList<>();
        String sql = "SELECT id, train_id, station_id, stop_sequence, arrival_time, departure_time " +
                "FROM schedule WHERE train_id = ? ORDER BY stop_sequence";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, trainId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ScheduleEntry(rs.getInt("id"), rs.getInt("train_id"),
                            rs.getInt("station_id"), rs.getInt("stop_sequence"),
                            rs.getString("arrival_time"), rs.getString("departure_time")));
                }
            }
        }
        return list;
    }

    /**
     * Finds trains that stop at both {@code fromStationId} and {@code toStationId}
     * with the "from" stop sequence occurring before the "to" stop sequence
     * (i.e. the train actually travels in that direction).
     */
    public synchronized List<Train> findDirectTrainsBetween(int fromStationId, int toStationId) throws SQLException {
        List<Train> list = new ArrayList<>();
        String sql = """
                SELECT DISTINCT t.id, t.number, t.name
                FROM trains t
                JOIN schedule s1 ON s1.train_id = t.id AND s1.station_id = ?
                JOIN schedule s2 ON s2.train_id = t.id AND s2.station_id = ?
                WHERE s1.stop_sequence < s2.stop_sequence
                ORDER BY t.number
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, fromStationId);
            ps.setInt(2, toStationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Train(rs.getInt("id"), rs.getString("number"), rs.getString("name")));
                }
            }
        }
        return list;
    }

    /** Finds every train that makes a scheduled stop at the given station, in train-number order. */
    public synchronized List<Train> getTrainsAtStation(int stationId) throws SQLException {
        List<Train> list = new ArrayList<>();
        String sql = """
                SELECT DISTINCT t.id, t.number, t.name
                FROM trains t
                JOIN schedule s ON s.train_id = t.id
                WHERE s.station_id = ?
                ORDER BY t.number
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, stationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Train(rs.getInt("id"), rs.getString("number"), rs.getString("name")));
                }
            }
        }
        return list;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
