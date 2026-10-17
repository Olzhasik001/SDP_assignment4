package rental.fleet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Persistence gateway of subsystem A (SQLite file fleet.db). */
public class ReservationRepository {
    private final String url;

    public ReservationRepository(Path dir) {
        try { Files.createDirectories(dir); } catch (IOException e) { throw new IllegalStateException(e); }
        this.url = "jdbc:sqlite:" + dir.resolve("fleet.db");
        init();
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(url); }

    private void init() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS cars(id INTEGER PRIMARY KEY, type TEXT, plate TEXT, daily_rate REAL)");
            s.execute("CREATE TABLE IF NOT EXISTS reservations(id INTEGER PRIMARY KEY AUTOINCREMENT, order_ref INTEGER, "
                    + "car_id INTEGER, start_date TEXT, end_date TEXT, price REAL, status TEXT)");
            int count;
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM cars")) { rs.next(); count = rs.getInt(1); }
            if (count == 0) {
                s.execute("INSERT INTO cars VALUES (1,'Economy','KZ-101-AA',30),(2,'Economy','KZ-102-AA',30),"
                        + "(3,'SUV','KZ-201-BB',60),(4,'Luxury','KZ-301-CC',120)");
            }
        } catch (SQLException e) { throw new IllegalStateException("Fleet DB init failed", e); }
    }

    public Optional<Car> findFreeCar(String type, LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM cars c WHERE c.type = ? AND NOT EXISTS (SELECT 1 FROM reservations r "
                + "WHERE r.car_id = c.id AND r.status IN ('HELD','CONFIRMED') AND r.start_date < ? AND r.end_date > ?) LIMIT 1";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type); ps.setString(2, to.toString()); ps.setString(3, from.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Car(rs.getLong("id"), rs.getString("type"), rs.getString("plate"), rs.getDouble("daily_rate")));
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Reservation insert(long orderRef, long carId, LocalDate from, LocalDate to, double price) {
        String sql = "INSERT INTO reservations(order_ref,car_id,start_date,end_date,price,status) VALUES(?,?,?,?,?,?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderRef); ps.setLong(2, carId); ps.setString(3, from.toString());
            ps.setString(4, to.toString()); ps.setDouble(5, price); ps.setString(6, Reservation.Status.HELD.name());
            ps.executeUpdate();
            try (Statement ks = c.createStatement(); ResultSet k = ks.executeQuery("SELECT last_insert_rowid()")) {
                k.next();
                return new Reservation(k.getLong(1), orderRef, carId, from, to, price, Reservation.Status.HELD);
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Optional<Reservation> findByOrder(long orderRef) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM reservations WHERE order_ref = ?")) {
            ps.setLong(1, orderRef);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public void updateStatus(Reservation r) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("UPDATE reservations SET status = ? WHERE id = ?")) {
            ps.setString(1, r.getStatus().name()); ps.setLong(2, r.getId());
            ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public List<Car> allCars() {
        List<Car> out = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM cars ORDER BY id")) {
            while (rs.next()) out.add(new Car(rs.getLong("id"), rs.getString("type"), rs.getString("plate"), rs.getDouble("daily_rate")));
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return out;
    }

    private Reservation map(ResultSet rs) throws SQLException {
        return new Reservation(rs.getLong("id"), rs.getLong("order_ref"), rs.getLong("car_id"),
                LocalDate.parse(rs.getString("start_date")), LocalDate.parse(rs.getString("end_date")),
                rs.getDouble("price"), Reservation.Status.valueOf(rs.getString("status")));
    }
}
