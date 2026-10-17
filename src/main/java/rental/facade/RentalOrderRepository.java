package rental.facade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Persists the chain progress (orders.db). This is what survives a restart. */
public class RentalOrderRepository {
    private final String url;

    public RentalOrderRepository(Path dir) {
        try { Files.createDirectories(dir); } catch (IOException e) { throw new IllegalStateException(e); }
        this.url = "jdbc:sqlite:" + dir.resolve("orders.db");
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS rental_orders(id INTEGER PRIMARY KEY AUTOINCREMENT, customer TEXT, "
                    + "car_type TEXT, start_date TEXT, end_date TEXT, status TEXT, note TEXT)");
        } catch (SQLException e) { throw new IllegalStateException("Orders DB init failed", e); }
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(url); }

    public RentalOrder create(RentalRequest r) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO rental_orders(customer,car_type,start_date,end_date,status,note) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, r.customer()); ps.setString(2, r.carType()); ps.setString(3, r.from().toString());
            ps.setString(4, r.to().toString()); ps.setString(5, RentalOrder.ChainStatus.STARTED.name()); ps.setString(6, "created");
            ps.executeUpdate();
            try (Statement ks = c.createStatement(); ResultSet k = ks.executeQuery("SELECT last_insert_rowid()")) {
                k.next();
                return new RentalOrder(k.getLong(1), r.customer(), r.carType(), r.from(), r.to(), RentalOrder.ChainStatus.STARTED, "created");
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public void update(long id, RentalOrder.ChainStatus status, String note) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("UPDATE rental_orders SET status = ?, note = ? WHERE id = ?")) {
            ps.setString(1, status.name()); ps.setString(2, note); ps.setLong(3, id); ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Optional<RentalOrder> findById(long id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM rental_orders WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public List<RentalOrder> findByStatus(RentalOrder.ChainStatus status) {
        return query("SELECT * FROM rental_orders WHERE status = ? ORDER BY id", status.name());
    }

    public List<RentalOrder> all() { return query("SELECT * FROM rental_orders ORDER BY id", null); }

    private List<RentalOrder> query(String sql, String param) {
        List<RentalOrder> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return out;
    }

    private RentalOrder map(ResultSet rs) throws SQLException {
        return new RentalOrder(rs.getLong("id"), rs.getString("customer"), rs.getString("car_type"),
                LocalDate.parse(rs.getString("start_date")), LocalDate.parse(rs.getString("end_date")),
                RentalOrder.ChainStatus.valueOf(rs.getString("status")), rs.getString("note"));
    }
}
