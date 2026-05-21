package printerhub.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CameraAnalysisSampleStore {

    public CameraAnalysisSample save(CameraAnalysisSample sample) {
        if (sample == null) {
            throw new IllegalArgumentException("camera analysis sample must not be null");
        }

        String sql = """
                INSERT INTO camera_analysis_samples (
                    session_id, printer_id, captured_at, analyzed_at, latest_snapshot_path,
                    previous_snapshot_path, delta_snapshot_path, delta_score, changed_pixel_ratio,
                    average_pixel_delta, confidence, suspected, reason_codes, message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, sample.sessionId());
            statement.setString(2, sample.printerId());
            statement.setString(3, sample.capturedAt().toString());
            statement.setString(4, sample.analyzedAt().toString());
            statement.setString(5, sample.latestSnapshotPath().orElse(null));
            statement.setString(6, sample.previousSnapshotPath().orElse(null));
            statement.setString(7, sample.deltaSnapshotPath().orElse(null));
            statement.setDouble(8, sample.deltaScore());
            statement.setDouble(9, sample.changedPixelRatio());
            statement.setDouble(10, sample.averagePixelDelta());
            statement.setDouble(11, sample.confidence());
            statement.setInt(12, sample.suspected() ? 1 : 0);
            statement.setString(13, sample.reasonCodes().orElse(null));
            statement.setString(14, sample.message().orElse(null));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new CameraAnalysisSample(
                            keys.getLong(1),
                            sample.sessionId(),
                            sample.printerId(),
                            sample.capturedAt(),
                            sample.analyzedAt(),
                            sample.latestSnapshotPath().orElse(null),
                            sample.previousSnapshotPath().orElse(null),
                            sample.deltaSnapshotPath().orElse(null),
                            sample.deltaScore(),
                            sample.changedPixelRatio(),
                            sample.averagePixelDelta(),
                            sample.confidence(),
                            sample.suspected(),
                            sample.reasonCodes().orElse(null),
                            sample.message().orElse(null));
                }
            }

            return sample;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera analysis sample", exception);
        }
    }

    public List<CameraAnalysisSample> findBySession(String printerId, String sessionId) {
        String sql = """
                SELECT id, session_id, printer_id, captured_at, analyzed_at, latest_snapshot_path,
                    previous_snapshot_path, delta_snapshot_path, delta_score, changed_pixel_ratio,
                    average_pixel_delta, confidence, suspected, reason_codes, message
                FROM camera_analysis_samples
                WHERE printer_id = ? AND session_id = ?
                ORDER BY captured_at ASC, id ASC;
                """;
        List<CameraAnalysisSample> samples = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setString(2, requireText(sessionId, "sessionId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    samples.add(mapSample(resultSet));
                }
            }
            return samples;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list camera analysis samples", exception);
        }
    }

    public List<CameraAnalysisSample> findRecentBySession(String printerId, String sessionId, int limit) {
        int safeLimit = Math.max(1, limit);
        String sql = """
                SELECT id, session_id, printer_id, captured_at, analyzed_at, latest_snapshot_path,
                    previous_snapshot_path, delta_snapshot_path, delta_score, changed_pixel_ratio,
                    average_pixel_delta, confidence, suspected, reason_codes, message
                FROM camera_analysis_samples
                WHERE printer_id = ? AND session_id = ?
                ORDER BY captured_at DESC, id DESC
                LIMIT ?;
                """;
        List<CameraAnalysisSample> samples = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setString(2, requireText(sessionId, "sessionId"));
            statement.setInt(3, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    samples.add(mapSample(resultSet));
                }
            }
            return samples;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list recent camera analysis samples", exception);
        }
    }

    private static CameraAnalysisSample mapSample(ResultSet resultSet) throws SQLException {
        return new CameraAnalysisSample(
                resultSet.getLong("id"),
                resultSet.getString("session_id"),
                resultSet.getString("printer_id"),
                Instant.parse(resultSet.getString("captured_at")),
                Instant.parse(resultSet.getString("analyzed_at")),
                resultSet.getString("latest_snapshot_path"),
                resultSet.getString("previous_snapshot_path"),
                resultSet.getString("delta_snapshot_path"),
                resultSet.getDouble("delta_score"),
                resultSet.getDouble("changed_pixel_ratio"),
                resultSet.getDouble("average_pixel_delta"),
                resultSet.getDouble("confidence"),
                resultSet.getInt("suspected") == 1,
                resultSet.getString("reason_codes"),
                resultSet.getString("message"));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
