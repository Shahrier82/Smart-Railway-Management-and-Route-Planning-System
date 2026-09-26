package com.railway.db;

import com.railway.exception.RailwayDataException;
import com.railway.model.Booking;
import com.railway.model.SeatClass;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists ticket {@link Booking}s in their own SQLite table. Kept as a
 * separate DAO from {@link DatabaseManager} because bookings are a
 * distinct domain (transactional passenger data) from the railway network
 * reference data that manager owns - each opens its own JDBC connection
 * to the same database file, which SQLite supports safely, with a busy
 * timeout set so brief cross-connection write contention is retried
 * automatically rather than failing outright.
 */
public class BookingDao implements AutoCloseable {

    private final Connection connection;

    public BookingDao(String dbFilePath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA busy_timeout = 3000;");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS bookings (
                        id                INTEGER PRIMARY KEY AUTOINCREMENT,
                        passenger_name    TEXT NOT NULL,
                        from_station_id   INTEGER NOT NULL,
                        to_station_id     INTEGER NOT NULL,
                        from_station_label TEXT NOT NULL,
                        to_station_label   TEXT NOT NULL,
                        train_number      TEXT,
                        train_name        TEXT,
                        seat_class        TEXT NOT NULL,
                        seat_count        INTEGER NOT NULL,
                        total_fare        REAL NOT NULL,
                        booked_at         TEXT NOT NULL
                    );
                    """);
        }
    }

    public synchronized Booking save(Booking booking) throws RailwayDataException {
        String sql = """
                INSERT INTO bookings(passenger_name, from_station_id, to_station_id,
                    from_station_label, to_station_label, train_number, train_name,
                    seat_class, seat_count, total_fare, booked_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, booking.getPassengerName());
            ps.setInt(2, booking.getFromStationId());
            ps.setInt(3, booking.getToStationId());
            ps.setString(4, booking.getFromStationLabel());
            ps.setString(5, booking.getToStationLabel());
            ps.setString(6, booking.getTrainNumber());
            ps.setString(7, booking.getTrainName());
            ps.setString(8, booking.getSeatClass().name());
            ps.setInt(9, booking.getSeatCount());
            ps.setDouble(10, booking.getTotalFare());
            ps.setString(11, booking.getBookedAt());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                booking.setId(keys.getInt(1));
            }
            return booking;
        } catch (SQLException e) {
            throw new RailwayDataException("Could not save the booking to the database.", e);
        }
    }

    public synchronized List<Booking> getAllBookings() throws RailwayDataException {
        List<Booking> list = new ArrayList<>();
        String sql = "SELECT * FROM bookings ORDER BY id DESC";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Booking(
                        rs.getInt("id"),
                        rs.getString("passenger_name"),
                        rs.getInt("from_station_id"),
                        rs.getInt("to_station_id"),
                        rs.getString("from_station_label"),
                        rs.getString("to_station_label"),
                        rs.getString("train_number"),
                        rs.getString("train_name"),
                        SeatClass.valueOf(rs.getString("seat_class")),
                        rs.getInt("seat_count"),
                        rs.getDouble("total_fare"),
                        rs.getString("booked_at")));
            }
        } catch (SQLException e) {
            throw new RailwayDataException("Could not load saved bookings.", e);
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
