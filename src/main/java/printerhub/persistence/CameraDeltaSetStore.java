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

public final class CameraDeltaSetStore {

    public CameraDeltaSet save(CameraDeltaSet deltaSet) {
        if (deltaSet == null) {
            throw new IllegalArgumentException("camera delta set must not be null");
        }

        String sql = """
                INSERT INTO camera_delta_sets (
                    printer_id,
                    camera_job_id,
                    method_name,
                    delta_snapshot_step,
                    source_snapshot_count,
                    generated_delta_count,
                    created_at,
                    message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, deltaSet.printerId());
            statement.setLong(2, deltaSet.cameraJobId());
            statement.setString(3, deltaSet.methodName());
            statement.setInt(4, deltaSet.deltaSnapshotStep());
            statement.setInt(5, deltaSet.sourceSnapshotCount());
            statement.setInt(6, deltaSet.generatedDeltaCount());
            statement.setString(7, deltaSet.createdAt().toString());
            statement.setString(8, deltaSet.message());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return deltaSet.withId(keys.getLong(1));
                }
            }

            throw new IllegalStateException("Failed to read generated camera delta set id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera delta set", exception);
        }
    }

    public Optional<CameraDeltaSet> findById(long id) {
        String sql = selectColumns() + " FROM camera_delta_sets WHERE id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(id, "id"));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta set", exception);
        }
    }

    public List<CameraDeltaSet> findByCameraJobId(long cameraJobId) {
        String sql = selectColumns() + """
                FROM camera_delta_sets
                WHERE camera_job_id = ?
                ORDER BY created_at DESC, id DESC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(cameraJobId, "cameraJobId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraDeltaSet> deltaSets = new ArrayList<>();
                while (resultSet.next()) {
                    deltaSets.add(mapRow(resultSet));
                }
                return deltaSets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta sets", exception);
        }
    }

    public CameraDeltaSet updateGeneratedDeltaCount(long id, int generatedDeltaCount) {
        if (generatedDeltaCount < 0) {
            throw new IllegalArgumentException("generatedDeltaCount must not be negative");
        }

        String sql = "UPDATE camera_delta_sets SET generated_delta_count = ? WHERE id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, generatedDeltaCount);
            statement.setLong(2, requirePositive(id, "id"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera delta set", exception);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + id));
    }

    public CameraDeltaSet updateCounts(long id, int sourceSnapshotCount, int generatedDeltaCount) {
        if (sourceSnapshotCount < 0) {
            throw new IllegalArgumentException("sourceSnapshotCount must not be negative");
        }
        if (generatedDeltaCount < 0) {
            throw new IllegalArgumentException("generatedDeltaCount must not be negative");
        }

        String sql = """
                UPDATE camera_delta_sets
                SET source_snapshot_count = ?,
                    generated_delta_count = ?
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, sourceSnapshotCount);
            statement.setInt(2, generatedDeltaCount);
            statement.setLong(3, requirePositive(id, "id"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera delta set counts", exception);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + id));
    }

    private static String selectColumns() {
        return """
                SELECT
                    id,
                    printer_id,
                    camera_job_id,
                    method_name,
                    delta_snapshot_step,
                    source_snapshot_count,
                    generated_delta_count,
                    created_at,
                    message
                """;
    }

    private static CameraDeltaSet mapRow(ResultSet resultSet) throws SQLException {
        return new CameraDeltaSet(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                resultSet.getLong("camera_job_id"),
                resultSet.getString("method_name"),
                resultSet.getInt("delta_snapshot_step"),
                resultSet.getInt("source_snapshot_count"),
                resultSet.getInt("generated_delta_count"),
                Instant.parse(resultSet.getString("created_at")),
                resultSet.getString("message"));
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }
}
