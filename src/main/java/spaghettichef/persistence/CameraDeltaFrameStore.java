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

public final class CameraDeltaFrameStore {

    public CameraDeltaFrame save(CameraDeltaFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("camera delta frame must not be null");
        }

        String sql = """
                INSERT INTO camera_delta_frames (
                    delta_set_id,
                    printer_id,
                    camera_job_id,
                    from_snapshot_id,
                    to_snapshot_id,
                    from_captured_at,
                    to_captured_at,
                    delta_path,
                    delta_score,
                    changed_pixel_ratio,
                    average_pixel_delta,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            bind(statement, frame);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return frame.withId(keys.getLong(1));
                }
            }

            throw new IllegalStateException("Failed to read generated camera delta frame id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera delta frame", exception);
        }
    }

    public Optional<CameraDeltaFrame> findById(long id) {
        String sql = selectColumns() + " FROM camera_delta_frames WHERE id = ?;";

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
            throw new IllegalStateException("Failed to load camera delta frame", exception);
        }
    }

    public List<CameraDeltaFrame> findByDeltaSetId(long deltaSetId) {
        String sql = selectColumns() + """
                FROM camera_delta_frames
                WHERE delta_set_id = ?
                ORDER BY from_captured_at ASC, id ASC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(deltaSetId, "deltaSetId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraDeltaFrame> frames = new ArrayList<>();
                while (resultSet.next()) {
                    frames.add(mapRow(resultSet));
                }
                return frames;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta frames", exception);
        }
    }

    public List<CameraDeltaFrame> findByCameraJobId(long cameraJobId) {
        String sql = selectColumns() + """
                FROM camera_delta_frames
                WHERE camera_job_id = ?
                ORDER BY from_captured_at ASC, id ASC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(cameraJobId, "cameraJobId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraDeltaFrame> frames = new ArrayList<>();
                while (resultSet.next()) {
                    frames.add(mapRow(resultSet));
                }
                return frames;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta frames", exception);
        }
    }

    public Optional<CameraDeltaFrame> findBySnapshotPair(long cameraJobId, long fromSnapshotId, long toSnapshotId) {
        String sql = selectColumns() + """
                FROM camera_delta_frames
                WHERE camera_job_id = ?
                    AND from_snapshot_id = ?
                    AND to_snapshot_id = ?
                ORDER BY id DESC
                LIMIT 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(cameraJobId, "cameraJobId"));
            statement.setLong(2, requirePositive(fromSnapshotId, "fromSnapshotId"));
            statement.setLong(3, requirePositive(toSnapshotId, "toSnapshotId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta frame", exception);
        }
    }

    public Optional<CameraDeltaFrame> findByDeltaPath(long deltaSetId, String deltaPath) {
        if (deltaPath == null || deltaPath.isBlank()) {
            throw new IllegalArgumentException("deltaPath must not be blank");
        }

        String sql = selectColumns() + """
                FROM camera_delta_frames
                WHERE delta_set_id = ?
                    AND delta_path = ?
                ORDER BY id DESC
                LIMIT 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(deltaSetId, "deltaSetId"));
            statement.setString(2, deltaPath.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta frame", exception);
        }
    }

    public int deleteById(long id) {
        String sql = "DELETE FROM camera_delta_frames WHERE id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(id, "id"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera delta frame", exception);
        }
    }

    public int deleteByCameraJobId(long cameraJobId) {
        String sql = "DELETE FROM camera_delta_frames WHERE camera_job_id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(cameraJobId, "cameraJobId"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera delta frames", exception);
        }
    }

    public int deleteByDeltaSetId(long deltaSetId) {
        String sql = "DELETE FROM camera_delta_frames WHERE delta_set_id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(deltaSetId, "deltaSetId"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera delta frames", exception);
        }
    }

    private static void bind(PreparedStatement statement, CameraDeltaFrame frame) throws SQLException {
        statement.setLong(1, frame.deltaSetId());
        statement.setString(2, frame.printerId());
        statement.setLong(3, frame.cameraJobId());
        statement.setLong(4, frame.fromSnapshotId());
        statement.setLong(5, frame.toSnapshotId());
        statement.setString(6, frame.fromCapturedAt().toString());
        statement.setString(7, frame.toCapturedAt().toString());
        statement.setString(8, frame.deltaPath());
        statement.setDouble(9, frame.deltaScore());
        statement.setDouble(10, frame.changedPixelRatio());
        statement.setDouble(11, frame.averagePixelDelta());
        statement.setString(12, frame.createdAt().toString());
    }

    private static String selectColumns() {
        return """
                SELECT
                    id,
                    delta_set_id,
                    printer_id,
                    camera_job_id,
                    from_snapshot_id,
                    to_snapshot_id,
                    from_captured_at,
                    to_captured_at,
                    delta_path,
                    delta_score,
                    changed_pixel_ratio,
                    average_pixel_delta,
                    created_at
                """;
    }

    private static CameraDeltaFrame mapRow(ResultSet resultSet) throws SQLException {
        return new CameraDeltaFrame(
                resultSet.getLong("id"),
                resultSet.getLong("delta_set_id"),
                resultSet.getString("printer_id"),
                resultSet.getLong("camera_job_id"),
                resultSet.getLong("from_snapshot_id"),
                resultSet.getLong("to_snapshot_id"),
                Instant.parse(resultSet.getString("from_captured_at")),
                Instant.parse(resultSet.getString("to_captured_at")),
                resultSet.getString("delta_path"),
                resultSet.getDouble("delta_score"),
                resultSet.getDouble("changed_pixel_ratio"),
                resultSet.getDouble("average_pixel_delta"),
                Instant.parse(resultSet.getString("created_at")));
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }
}
