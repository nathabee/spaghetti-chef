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

public final class CameraCalculationResultStore {

    public CameraCalculationResult save(CameraCalculationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("camera calculation result must not be null");
        }

        String sql = """
                INSERT INTO camera_calculation_results (
                    calculation_run_id,
                    delta_frame_id,
                    confidence,
                    suspected,
                    reason_codes,
                    message,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setLong(1, result.calculationRunId());
            statement.setLong(2, result.deltaFrameId());
            statement.setDouble(3, result.confidence());
            statement.setInt(4, result.suspected() ? 1 : 0);
            statement.setString(5, result.reasonCodes());
            statement.setString(6, result.message());
            statement.setString(7, result.createdAt().toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return result.withId(keys.getLong(1));
                }
            }

            throw new IllegalStateException("Failed to read generated camera calculation result id");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera calculation result", exception);
        }
    }

    public Optional<CameraCalculationResult> findById(long id) {
        String sql = selectColumns() + " FROM camera_calculation_results WHERE id = ?;";

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
            throw new IllegalStateException("Failed to load camera calculation result", exception);
        }
    }

    public List<CameraCalculationResult> findByCalculationRunId(long calculationRunId) {
        String sql = selectColumns() + """
                FROM camera_calculation_results
                WHERE calculation_run_id = ?
                ORDER BY id ASC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(calculationRunId, "calculationRunId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraCalculationResult> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera calculation results", exception);
        }
    }

    public int deleteByCalculationRunId(long calculationRunId) {
        String sql = "DELETE FROM camera_calculation_results WHERE calculation_run_id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(calculationRunId, "calculationRunId"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera calculation results", exception);
        }
    }

    private static String selectColumns() {
        return """
                SELECT
                    id,
                    calculation_run_id,
                    delta_frame_id,
                    confidence,
                    suspected,
                    reason_codes,
                    message,
                    created_at
                """;
    }

    private static CameraCalculationResult mapRow(ResultSet resultSet) throws SQLException {
        return new CameraCalculationResult(
                resultSet.getLong("id"),
                resultSet.getLong("calculation_run_id"),
                resultSet.getLong("delta_frame_id"),
                resultSet.getDouble("confidence"),
                resultSet.getInt("suspected") != 0,
                resultSet.getString("reason_codes"),
                resultSet.getString("message"),
                Instant.parse(resultSet.getString("created_at")));
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }
}
