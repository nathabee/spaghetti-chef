package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.runtime.PrinterRuntimeNodeFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PrinterConfigurationStore {

    public void save(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        String sql = """
                INSERT INTO configured_printers (
                    id,
                    name,
                    port_name,
                    mode,
                    enabled,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    port_name = excluded.port_name,
                    mode = excluded.mode,
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at;
                """;

        String now = Instant.now().toString();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, node.id());
            statement.setString(2, node.displayName());
            statement.setString(3, node.portName());
            statement.setString(4, node.mode());
            statement.setInt(5, node.enabled() ? 1 : 0);
            statement.setString(6, now);
            statement.setString(7, now);

            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_PRINTER_CONFIGURATION, exception);
        }
    }

    public List<PrinterRuntimeNode> findAll() {
        String sql = """
                SELECT
                    id,
                    name,
                    port_name,
                    mode,
                    enabled
                FROM configured_printers
                ORDER BY id;
                """;

        List<PrinterRuntimeNode> printers = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                printers.add(PrinterRuntimeNodeFactory.create(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("port_name"),
                        resultSet.getString("mode"),
                        resultSet.getInt("enabled") == 1
                ));
            }

            return printers;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_CONFIGURATION, exception);
        }
    }

    public boolean hasAnyPrinter() {
        String sql = """
                SELECT COUNT(*) AS printer_count
                FROM configured_printers;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            return resultSet.next() && resultSet.getInt("printer_count") > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_CHECK_PRINTER_CONFIGURATION, exception);
        }
    }

    public void delete(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        String sql = """
                DELETE FROM configured_printers
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerId.trim());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_DELETE_PRINTER_CONFIGURATION, exception);
        }
    }

    public void enable(String printerId) {
        updateEnabled(printerId, true);
    }

    public void disable(String printerId) {
        updateEnabled(printerId, false);
    }

    private void updateEnabled(String printerId, boolean enabled) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        String sql = """
                UPDATE configured_printers
                SET
                    enabled = ?,
                    updated_at = ?
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setString(2, Instant.now().toString());
            statement.setString(3, printerId.trim());

            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_UPDATE_PRINTER_ENABLED_FLAG, exception);
        }
    }
}