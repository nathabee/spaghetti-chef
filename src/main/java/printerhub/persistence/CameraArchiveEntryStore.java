package printerhub.persistence;

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
                    job_id,
                    snapshot_path,
                    content_type,
                    size_bytes,
                    captured_at,
                    snapshotd_at,
                    source_type,
                    message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, entry.printerId());
            statement.setString(2, entry.jobId());
            statement.setString(3, entry.snapshotPath());
            statement.setString(4, entry.contentType());
            statement.setLong(5, entry.sizeBytes());
            statement.setString(6, entry.capturedAt().toString());
            statement.setString(7, entry.retainedAt().toString());
            statement.setString(8, entry.sourceType());
            statement.setString(9, entry.message());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new CameraSnapshotEntry(
                            keys.getLong(1),
                            entry.printerId(),
                            entry.jobId(),
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
                    COALESCE(job_id, 'unassigned') AS job_key,
                    COUNT(*) AS file_count,
                    COALESCE(SUM(size_bytes), 0) AS total_bytes,
                    MIN(captured_at) AS first_captured_at,
                    MAX(captured_at) AS last_captured_at
                FROM camera_snapshot_entries
                GROUP BY COALESCE(job_id, 'unassigned')
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
                        resultSet.getString("job_key"),
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

    public Optional<CameraSnapshotEntry> findById(long id) {
        String sql = """
                SELECT
                    id,
                    printer_id,
                    job_id,
                    snapshot_path,
                    content_type,
                    size_bytes,
                    captured_at,
                    snapshotd_at,
                    source_type,
                    message
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


    public List<CameraSnapshotJobSummary> findJobSummariesByPrinterId(String printerId) {
        String normalizedPrinterId = normalizePrinterId(printerId);
        String sql = """
                SELECT
                    COALESCE(job_id, 'unassigned') AS job_key,
                    COUNT(*) AS file_count,
                    COALESCE(SUM(size_bytes), 0) AS total_bytes,
                    MIN(captured_at) AS first_captured_at,
                    MAX(captured_at) AS last_captured_at
                FROM camera_snapshot_entries
                WHERE printer_id = ?
                GROUP BY COALESCE(job_id, 'unassigned')
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
                            resultSet.getString("job_key"),
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

    public List<CameraSnapshotEntry> findByJobId(String jobId) {
        String normalizedJobId = normalizeJobId(jobId);
        boolean unassigned = "unassigned".equals(normalizedJobId);
        String sql = """
                SELECT
                    id,
                    printer_id,
                    job_id,
                    snapshot_path,
                    content_type,
                    size_bytes,
                    captured_at,
                    snapshotd_at,
                    source_type,
                    message
                FROM camera_snapshot_entries
                WHERE %s
                ORDER BY captured_at ASC, id ASC;
                """.formatted(unassigned ? "job_id IS NULL" : "job_id = ?");

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            if (!unassigned) {
                statement.setString(1, normalizedJobId);
            }

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
        String normalizedJobId = normalizeJobId(jobId);
        boolean unassigned = "unassigned".equals(normalizedJobId);
        String sql = """
                SELECT
                    id,
                    printer_id,
                    job_id,
                    snapshot_path,
                    content_type,
                    size_bytes,
                    captured_at,
                    snapshotd_at,
                    source_type,
                    message
                FROM camera_snapshot_entries
                WHERE printer_id = ?
                    AND %s
                ORDER BY captured_at ASC, id ASC;
                """.formatted(unassigned ? "job_id IS NULL" : "job_id = ?");

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            if (!unassigned) {
                statement.setString(2, normalizedJobId);
            }

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
        String normalizedJobId = normalizeJobId(jobId);
        boolean unassigned = "unassigned".equals(normalizedJobId);
        String sql = "DELETE FROM camera_snapshot_entries WHERE " + (unassigned ? "job_id IS NULL" : "job_id = ?");

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            if (!unassigned) {
                statement.setString(1, normalizedJobId);
            }
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera snapshot entries", exception);
        }
    }

    public int deleteByPrinterIdAndJobId(String printerId, String jobId) {
        String normalizedPrinterId = normalizePrinterId(printerId);
        String normalizedJobId = normalizeJobId(jobId);
        boolean unassigned = "unassigned".equals(normalizedJobId);
        String sql = "DELETE FROM camera_snapshot_entries WHERE printer_id = ? AND "
                + (unassigned ? "job_id IS NULL" : "job_id = ?");

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            if (!unassigned) {
                statement.setString(2, normalizedJobId);
            }
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera snapshot entries", exception);
        }
    }

    private static CameraSnapshotEntry mapRow(ResultSet resultSet) throws SQLException {
        return new CameraSnapshotEntry(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                resultSet.getString("job_id"),
                resultSet.getString("snapshot_path"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                Instant.parse(resultSet.getString("captured_at")),
                Instant.parse(resultSet.getString("snapshotd_at")),
                resultSet.getString("source_type"),
                resultSet.getString("message"));
    }

    private static String normalizeJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }
        return jobId.trim();
    }

    private static String normalizePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        return printerId.trim();
    }
}
