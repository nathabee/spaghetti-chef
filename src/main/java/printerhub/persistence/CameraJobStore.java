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

public final class CameraJobStore {

    public CameraJob save(CameraJob job) {
        if (job == null) {
            throw new IllegalArgumentException("camera job must not be null");
        }

        if (job.id().isPresent()) {
            update(job);
            return job;
        }

        return insert(job);
    }

    private CameraJob insert(CameraJob job) {
        String sql = """
                INSERT INTO camera_jobs (
                    printer_id,
                    linked_print_job_id,
                    analysis_session_id,
                    state,
                    started_at,
                    stopped_at,
                    capture_interval_seconds,
                    retained_snapshots,
                    source_type,
                    source_description,
                    snapshot_directory,
                    message,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            bind(statement, job);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return job.withId(generatedKeys.getLong(1));
                }
            }

            throw new IllegalStateException("Failed to read generated camera job id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera job", exception);
        }
    }

    private void update(CameraJob job) {
        String sql = """
                UPDATE camera_jobs
                SET
                    printer_id = ?,
                    linked_print_job_id = ?,
                    analysis_session_id = ?,
                    state = ?,
                    started_at = ?,
                    stopped_at = ?,
                    capture_interval_seconds = ?,
                    retained_snapshots = ?,
                    source_type = ?,
                    source_description = ?,
                    snapshot_directory = ?,
                    message = ?,
                    created_at = ?,
                    updated_at = ?
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            bind(statement, job);
            statement.setLong(15, job.requireId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera job", exception);
        }
    }

    public Optional<CameraJob> findById(long id) {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be greater than zero");
        }

        String sql = selectColumns() + """
                FROM camera_jobs
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
            throw new IllegalStateException("Failed to load camera job", exception);
        }
    }

    public Optional<CameraJob> findActiveByPrinterId(String printerId) {
        String normalizedPrinterId = requireText(printerId, "printerId");

        String sql = selectColumns() + """
                FROM camera_jobs
                WHERE printer_id = ?
                    AND state = ?
                ORDER BY id DESC
                LIMIT 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setString(2, CameraJobState.RUNNING.name());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active camera job", exception);
        }
    }

    public List<CameraJob> findByPrinterId(String printerId) {
        String normalizedPrinterId = requireText(printerId, "printerId");

        String sql = selectColumns() + """
                FROM camera_jobs
                WHERE printer_id = ?
                ORDER BY id DESC;
                """;

        List<CameraJob> jobs = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jobs.add(mapRow(resultSet));
                }
            }

            return jobs;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera jobs", exception);
        }
    }

    public List<CameraJob> findAll() {
        String sql = selectColumns() + """
                FROM camera_jobs
                ORDER BY id DESC;
                """;

        List<CameraJob> jobs = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                jobs.add(mapRow(resultSet));
            }

            return jobs;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera jobs", exception);
        }
    }

    public CameraJob updateSnapshotDirectory(long id, String snapshotDirectory, Instant updatedAt) {
        CameraJob current = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("camera job not found: " + id));
        CameraJob updated = current.withSnapshotDirectory(snapshotDirectory, updatedAt);
        save(updated);
        return updated;
    }

    public CameraJob markStopped(long id, CameraJobState state, Instant stoppedAt, String message) {
        CameraJob current = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("camera job not found: " + id));
        CameraJob updated = current.stopped(state, stoppedAt, message);
        save(updated);
        return updated;
    }

    private static String selectColumns() {
        return """
                SELECT
                    id,
                    printer_id,
                    linked_print_job_id,
                    analysis_session_id,
                    state,
                    started_at,
                    stopped_at,
                    capture_interval_seconds,
                    retained_snapshots,
                    source_type,
                    source_description,
                    snapshot_directory,
                    message,
                    created_at,
                    updated_at
                """;
    }

    private static void bind(PreparedStatement statement, CameraJob job) throws SQLException {
        statement.setString(1, job.printerId());
        statement.setString(2, job.linkedPrintJobId().orElse(null));
        statement.setString(3, job.analysisSessionId().orElse(null));
        statement.setString(4, job.state().name());
        statement.setString(5, job.startedAt().toString());
        statement.setString(6, job.stoppedAt().map(Instant::toString).orElse(null));
        statement.setInt(7, job.captureIntervalSeconds());
        statement.setInt(8, job.retainedSnapshots());
        statement.setString(9, job.sourceType());
        statement.setString(10, job.sourceDescription().orElse(null));
        statement.setString(11, job.snapshotDirectory());
        statement.setString(12, job.message().orElse(null));
        statement.setString(13, job.createdAt().toString());
        statement.setString(14, job.updatedAt().toString());
    }

    private static CameraJob mapRow(ResultSet resultSet) throws SQLException {
        return new CameraJob(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                resultSet.getString("linked_print_job_id"),
                resultSet.getString("analysis_session_id"),
                CameraJobState.valueOf(resultSet.getString("state")),
                Instant.parse(resultSet.getString("started_at")),
                nullableInstant(resultSet, "stopped_at"),
                resultSet.getInt("capture_interval_seconds"),
                resultSet.getInt("retained_snapshots"),
                resultSet.getString("source_type"),
                resultSet.getString("source_description"),
                resultSet.getString("snapshot_directory"),
                resultSet.getString("message"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private static Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);

        if (value == null || value.isBlank()) {
            return null;
        }

        return Instant.parse(value);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }
}
