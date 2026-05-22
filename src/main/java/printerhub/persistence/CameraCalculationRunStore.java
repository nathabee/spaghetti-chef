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

public final class CameraCalculationRunStore {

    public CameraCalculationRun save(CameraCalculationRun run) {
        if (run == null) {
            throw new IllegalArgumentException("camera calculation run must not be null");
        }

        String sql = """
                INSERT INTO camera_calculation_runs (
                    printer_id,
                    camera_job_id,
                    delta_set_id,
                    method_name,
                    parameter_json,
                    created_at,
                    result_count,
                    message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, run.printerId());
            statement.setLong(2, run.cameraJobId());
            statement.setLong(3, run.deltaSetId());
            statement.setString(4, run.methodName());
            statement.setString(5, run.parameterJson());
            statement.setString(6, run.createdAt().toString());
            statement.setInt(7, run.resultCount());
            statement.setString(8, run.message());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return run.withId(keys.getLong(1));
                }
            }

            throw new IllegalStateException("Failed to read generated camera calculation run id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera calculation run", exception);
        }
    }

    public Optional<CameraCalculationRun> findById(long id) {
        String sql = selectColumns() + " FROM camera_calculation_runs WHERE id = ?;";

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
            throw new IllegalStateException("Failed to load camera calculation run", exception);
        }
    }

    public List<CameraCalculationRun> findByDeltaSetId(long deltaSetId) {
        String sql = selectColumns() + """
                FROM camera_calculation_runs
                WHERE delta_set_id = ?
                ORDER BY created_at DESC, id DESC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(deltaSetId, "deltaSetId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraCalculationRun> runs = new ArrayList<>();
                while (resultSet.next()) {
                    runs.add(mapRow(resultSet));
                }
                return runs;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera calculation runs", exception);
        }
    }

    public CameraCalculationRun updateResultCount(long id, int resultCount) {
        if (resultCount < 0) {
            throw new IllegalArgumentException("resultCount must not be negative");
        }

        String sql = "UPDATE camera_calculation_runs SET result_count = ? WHERE id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, resultCount);
            statement.setLong(2, requirePositive(id, "id"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera calculation run result count", exception);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("camera calculation run not found: " + id));
    }

    private static String selectColumns() {
        return """
                SELECT
                    id,
                    printer_id,
                    camera_job_id,
                    delta_set_id,
                    method_name,
                    parameter_json,
                    created_at,
                    result_count,
                    message
                """;
    }

    private static CameraCalculationRun mapRow(ResultSet resultSet) throws SQLException {
        return new CameraCalculationRun(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                resultSet.getLong("camera_job_id"),
                resultSet.getLong("delta_set_id"),
                resultSet.getString("method_name"),
                resultSet.getString("parameter_json"),
                Instant.parse(resultSet.getString("created_at")),
                resultSet.getInt("result_count"),
                resultSet.getString("message"));
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }
}
