package rental.billing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Optional;

/** Persistence gateway of subsystem B (SQLite file billing.db). */
public class BillingRepository {
    private final String url;

    public BillingRepository(Path dir) {
        try { Files.createDirectories(dir); } catch (IOException e) { throw new IllegalStateException(e); }
        this.url = "jdbc:sqlite:" + dir.resolve("billing.db");
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS deposits(id INTEGER PRIMARY KEY AUTOINCREMENT, order_ref INTEGER, amount REAL, status TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS policies(id INTEGER PRIMARY KEY AUTOINCREMENT, order_ref INTEGER, coverage TEXT, premium REAL, status TEXT)");
        } catch (SQLException e) { throw new IllegalStateException("Billing DB init failed", e); }
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(url); }

    public Deposit insertDeposit(long orderRef, double amount) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO deposits(order_ref,amount,status) VALUES(?,?,?)")) {
            ps.setLong(1, orderRef); ps.setDouble(2, amount); ps.setString(3, Deposit.Status.HELD.name());
            ps.executeUpdate();
            try (Statement ks = c.createStatement(); ResultSet k = ks.executeQuery("SELECT last_insert_rowid()")) { k.next(); return new Deposit(k.getLong(1), orderRef, amount, Deposit.Status.HELD); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public InsurancePolicy insertPolicy(long orderRef, InsurancePolicy.Coverage cov, double premium) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO policies(order_ref,coverage,premium,status) VALUES(?,?,?,?)")) {
            ps.setLong(1, orderRef); ps.setString(2, cov.name()); ps.setDouble(3, premium);
            ps.setString(4, InsurancePolicy.Status.QUOTED.name());
            ps.executeUpdate();
            try (Statement ks = c.createStatement(); ResultSet k = ks.executeQuery("SELECT last_insert_rowid()")) {
                k.next();
                return new InsurancePolicy(k.getLong(1), orderRef, cov, premium, InsurancePolicy.Status.QUOTED);
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Optional<Deposit> findDeposit(long orderRef) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM deposits WHERE order_ref = ?")) {
            ps.setLong(1, orderRef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Deposit(rs.getLong("id"), rs.getLong("order_ref"),
                        rs.getDouble("amount"), Deposit.Status.valueOf(rs.getString("status")))) : Optional.empty();
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Optional<InsurancePolicy> findPolicy(long orderRef) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM policies WHERE order_ref = ?")) {
            ps.setLong(1, orderRef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new InsurancePolicy(rs.getLong("id"), rs.getLong("order_ref"),
                        InsurancePolicy.Coverage.valueOf(rs.getString("coverage")), rs.getDouble("premium"),
                        InsurancePolicy.Status.valueOf(rs.getString("status")))) : Optional.empty();
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public void update(Deposit d) { exec("UPDATE deposits SET status = ? WHERE id = ?", d.getStatus().name(), d.getId()); }
    public void update(InsurancePolicy p) { exec("UPDATE policies SET status = ? WHERE id = ?", p.getStatus().name(), p.getId()); }

    private void exec(String sql, String status, long id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status); ps.setLong(2, id); ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
}
