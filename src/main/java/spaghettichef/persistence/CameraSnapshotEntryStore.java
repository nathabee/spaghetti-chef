package spaghettichef.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CameraSnapshotEntryStore {

    public CameraSnapshotEntry save(CameraSnapshotEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("camera snapshot entry must not be null");
        }

        String sql = """
                INSERT INTO camera_snapshot_entries (
                    printer_id,
                    camera_job_id,
                    linked_print_job_id,
                    snapshot_path,
                    content_type,
                    size_bytes,
                    captured_at,
                    retained_at,
                    source_type,
                    message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, entry.printerId());
            if (entry.cameraJobId() == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setLong(2, entry.cameraJobId());
            }
            statement.setString(3, entry.linkedPrintJobId());
            statement.setString(4, entry.snapshotPath());
            statement.setString(5, entry.contentType());
            statement.setLong(6, entry.sizeBytes());
            statement.setString(7, entry.capturedAt().toString());
            statement.setString(8, entry.retainedAt().toString());
            statement.setString(9, entry.sourceType());
            statement.setString(10, entry.message());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new CameraSnapshotEntry(
                            keys.getLong(1),
                            entry.printerId(),
                            entry.cameraJobId(),
                            entry.linkedPrintJobId(),
                            entry.snapshotPath(),
                            entry.contentType(),
                            entry.sizeBytes(),
                            entry.capturedAt(),
                            entry.retainedAt(),
                            entry.sourceType(),
                            entry.message());
                }
            }

            return entry;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera snapshot entry", exception);
        }
    }

    public List<CameraSnapshotJobSummary> findJobSummaries() {
        String sql = """
                SELECT
                    COALESCE(CAST(camera_job_id AS TEXT), 'unassigned') AS camera_job_key,
                    COUNT(*) AS file_count,
                    COALESCE(SUM(size_bytes), 0) AS total_bytes,
                    MIN(captured_at) AS first_captured_at,
                    MAX(captured_at) AS last_captured_at
                FROM camera_snapshot_entries
                GROUP BY COALESCE(CAST(camera_job_id AS TEXT), 'unassigned')
                ORDER BY MAX(captured_at) DESC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            List<CameraSnapshotJobSummary> summaries = new ArrayList<>();
            while (resultSet.next()) {
                summaries.add(new CameraSnapshotJobSummary(
                        resultSet.getString("camera_job_key"),
                        resultSet.getInt("file_count"),
                        resultSet.getLong("total_bytes"),
                        Instant.parse(resultSet.getString("first_captured_at")),
                        Instant.parse(resultSet.getString("last_captured_at"))));
            }
            return summaries;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera snapshot job summaries", exception);
        }
    }

    public List<CameraSnapshotJobSummary> findJobSummariesByPrinterId(String printerId) {
        String normalizedPrinterId = normalizePrinterId(printerId);
        String sql = """
                SELECT
                    COALESCE(CAST(camera_job_id AS TEXT), 'unassigned') AS camera_job_key,
                    COUNT(*) AS file_count,
                    COALESCE(SUM(size_bytes), 0) AS total_bytes,
                    MIN(captured_at) AS first_captured_at,
                    MAX(captured_at) AS last_captured_at
                FROM camera_snapshot_entries
                WHERE printer_id = ?
                GROUP BY COALESCE(CAST(camera_job_id AS TEXT), 'unassigned')
                ORDER BY MAX(captured_at) DESC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraSnapshotJobSummary> summaries = new ArrayList<>();
                while (resultSet.next()) {
                    summaries.add(new CameraSnapshotJobSummary(
                            resultSet.getString("camera_job_key"),
                            resultSet.getInt("file_count"),
                            resultSet.getLong("total_bytes"),
                            Instant.parse(resultSet.getString("first_captured_at")),
                            Instant.parse(resultSet.getString("last_captured_at"))));
                }
                return summaries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera snapshot job summaries", exception);
        }
    }

    public Optional<CameraSnapshotEntry> findById(long id) {
        String sql = selectColumns() + """
                FROM camera_snapshot_entries
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera snapshot entry", exception);
        }
    }

    public CameraSnapshotEntry updateSnapshotPath(long id, String snapshotPath) {
        if (id <= 0L) {
            throw new IllegalArgumentException("camera snapshot entry id must be greater than zero");
        }
        if (snapshotPath == null || snapshotPath.isBlank()) {
            throw new IllegalArgumentException("snapshotPath must not be blank");
        }

        String sql = "UPDATE camera_snapshot_entries SET snapshot_path = ? WHERE id = ?";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, snapshotPath.trim());
            statement.setLong(2, id);
            int updated = statement.executeUpdate();

            if (updated != 1) {
                throw new IllegalStateException("Camera snapshot entry not found: " + id);
            }

            return findById(id)
                    .orElseThrow(() -> new IllegalStateException("Camera snapshot entry not found: " + id));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera snapshot entry path", exception);
        }
    }

    public List<CameraSnapshotEntry> findByJobId(String jobId) {
        Long cameraJobId = parseCameraJobId(jobId);

        String sql = selectColumns() + """
                FROM camera_snapshot_entries
                WHERE camera_job_id = ?
                ORDER BY captured_at ASC, id ASC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, cameraJobId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraSnapshotEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapRow(resultSet));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera snapshot entries", exception);
        }
    }

    public List<CameraSnapshotEntry> findByPrinterIdAndJobId(String printerId, String jobId) {
        String normalizedPrinterId = normalizePrinterId(printerId);
        Long cameraJobId = parseCameraJobId(jobId);

        String sql = selectColumns() + """
                FROM camera_snapshot_entries
                WHERE printer_id = ?
                    AND camera_job_id = ?
                ORDER BY captured_at ASC, id ASC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setLong(2, cameraJobId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraSnapshotEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapRow(resultSet));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera snapshot entries", exception);
        }
    }

    public int deleteByJobId(String jobId) {
        Long cameraJobId = parseCameraJobId(jobId);

        String sql = "DELETE FROM camera_snapshot_entries WHERE camera_job_id = ?";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, cameraJobId);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera snapshot entries", exception);
        }
    }

    public int deleteByPrinterIdAndJobId(String printerId, String jobId) {
        String normalizedPrinterId = normalizePrinterId(printerId);
        Long cameraJobId = parseCameraJobId(jobId);

        String sql = "DELETE FROM camera_snapshot_entries WHERE printer_id = ? AND camera_job_id = ?";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setLong(2, cameraJobId);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera snapshot entries", exception);
        }
    }

    private static String selectColumns() {
        return """
                SELECT
                    id,
                    printer_id,
                    camera_job_id,
                    linked_print_job_id,
                    snapshot_path,
                    content_type,
                    size_bytes,
                    captured_at,
                    retained_at,
                    source_type,
                    message
                """;
    }

    private static CameraSnapshotEntry mapRow(ResultSet resultSet) throws SQLException {
        Long cameraJobId = nullableLong(resultSet, "camera_job_id");

        return new CameraSnapshotEntry(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                cameraJobId,
                resultSet.getString("linked_print_job_id"),
                resultSet.getString("snapshot_path"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                Instant.parse(resultSet.getString("captured_at")),
                Instant.parse(resultSet.getString("retained_at")),
                resultSet.getString("source_type"),
                resultSet.getString("message"));
    }

    private static Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private static Long parseCameraJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("camera job id must not be blank");
        }

        try {
            long parsed = Long.parseLong(jobId.trim());

            if (parsed <= 0L) {
                throw new IllegalArgumentException("camera job id must be greater than zero");
            }

            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("camera job id must be numeric: " + jobId, exception);
        }
    }

    private static String normalizePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }
}
