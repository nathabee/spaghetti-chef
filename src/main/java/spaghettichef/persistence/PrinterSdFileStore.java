package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.job.PrinterSdFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PrinterSdFileStore {

    public void save(PrinterSdFile file) {
        if (file == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printerSdFile"));
        }

        String sql = """
                INSERT INTO printer_sd_files (
                    id,
                    printer_id,
                    firmware_path,
                    display_name,
                    size_bytes,
                    raw_line,
                    print_file_id,
                    enabled,
                    deleted,
                    deleted_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(printer_id, firmware_path) DO UPDATE SET
                    display_name = excluded.display_name,
                    size_bytes = excluded.size_bytes,
                    raw_line = excluded.raw_line,
                    print_file_id = COALESCE(excluded.print_file_id, printer_sd_files.print_file_id),
                    enabled = excluded.enabled,
                    deleted = excluded.deleted,
                    deleted_at = excluded.deleted_at,
                    updated_at = excluded.updated_at
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, file.id());
            statement.setString(2, file.printerId());
            statement.setString(3, file.firmwarePath());
            statement.setString(4, file.displayName());

            if (file.sizeBytes() == null) {
                statement.setNull(5, java.sql.Types.BIGINT);
            } else {
                statement.setLong(5, file.sizeBytes());
            }

            statement.setString(6, file.rawLine());
            statement.setString(7, file.printFileId());
            statement.setInt(8, file.enabled() ? 1 : 0);
            statement.setInt(9, file.deleted() ? 1 : 0);
            statement.setString(10, file.deletedAt() == null ? null : file.deletedAt().toString());
            statement.setString(11, file.createdAt().toString());
            statement.setString(12, file.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_PRINTER_SD_FILE, exception);
        }
    }

    public Optional<PrinterSdFile> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_ID_MUST_NOT_BE_BLANK);
        }

        String sql = selectSql() + " WHERE id = ?";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, id.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_SD_FILE, exception);
        }
    }

    public List<PrinterSdFile> findByPrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        String sql = selectSql() + " WHERE printer_id = ? ORDER BY updated_at DESC";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerId.trim());
            return queryList(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_SD_FILES, exception);
        }
    }

    public Optional<PrinterSdFile> findByPrinterIdAndFirmwarePath(String printerId, String firmwarePath) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_PATH_MUST_NOT_BE_BLANK);
        }

        String sql = selectSql() + " WHERE printer_id = ? AND firmware_path = ?";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerId.trim());
            statement.setString(2, firmwarePath.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_SD_FILE, exception);
        }
    }

    public List<PrinterSdFile> findAll() {
        String sql = selectSql() + " ORDER BY updated_at DESC";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            return queryList(statement);
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_SD_FILES, exception);
        }
    }

    private List<PrinterSdFile> queryList(PreparedStatement statement) throws SQLException {
        List<PrinterSdFile> files = new ArrayList<>();

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                files.add(mapRow(resultSet));
            }
        }

        return files;
    }

    private String selectSql() {
        return """
                SELECT
                    id,
                    printer_id,
                    firmware_path,
                    display_name,
                    size_bytes,
                    raw_line,
                    print_file_id,
                    enabled,
                    deleted,
                    deleted_at,
                    created_at,
                    updated_at
                FROM printer_sd_files
                """;
    }

    private PrinterSdFile mapRow(ResultSet resultSet) throws SQLException {
        return new PrinterSdFile(
                resultSet.getString("id"),
                resultSet.getString("printer_id"),
                resultSet.getString("firmware_path"),
                resultSet.getString("display_name"),
                nullableLong(resultSet, "size_bytes"),
                resultSet.getString("raw_line"),
                resultSet.getString("print_file_id"),
                resultSet.getInt("enabled") == 1,
                resultSet.getInt("deleted") == 1,
                nullableInstant(resultSet, "deleted_at"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    private Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
