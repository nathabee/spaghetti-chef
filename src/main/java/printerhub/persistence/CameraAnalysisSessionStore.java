package printerhub.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CameraAnalysisSessionStore {

    public CameraAnalysisSession save(CameraAnalysisSession session) {
        if (session == null) {
            throw new IllegalArgumentException("camera analysis session must not be null");
        }

        String sql = """
                INSERT INTO camera_analysis_sessions (
                    id, printer_id, state, started_at, stopped_at, created_at, updated_at, message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            bindSession(statement, session);
            statement.executeUpdate();
            return session;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera analysis session", exception);
        }
    }

    public CameraAnalysisSession update(CameraAnalysisSession session) {
        if (session == null) {
            throw new IllegalArgumentException("camera analysis session must not be null");
        }

        String sql = """
                UPDATE camera_analysis_sessions
                SET printer_id = ?, state = ?, started_at = ?, stopped_at = ?, created_at = ?, updated_at = ?, message = ?
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, session.printerId());
            statement.setString(2, session.state().name());
            statement.setString(3, session.startedAt().map(Instant::toString).orElse(null));
            statement.setString(4, session.stoppedAt().map(Instant::toString).orElse(null));
            statement.setString(5, session.createdAt().toString());
            statement.setString(6, session.updatedAt().toString());
            statement.setString(7, session.message().orElse(null));
            statement.setString(8, session.id());
            statement.executeUpdate();
            return session;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera analysis session", exception);
        }
    }

    public Optional<CameraAnalysisSession> findById(String printerId, String sessionId) {
        String sql = """
                SELECT id, printer_id, state, started_at, stopped_at, created_at, updated_at, message
                FROM camera_analysis_sessions
                WHERE printer_id = ? AND id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setString(2, requireText(sessionId, "sessionId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSession(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera analysis session", exception);
        }
    }

    public Optional<CameraAnalysisSession> findActiveByPrinterId(String printerId) {
        String sql = """
                SELECT id, printer_id, state, started_at, stopped_at, created_at, updated_at, message
                FROM camera_analysis_sessions
                WHERE printer_id = ? AND state = 'RUNNING'
                ORDER BY started_at DESC, created_at DESC
                LIMIT 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSession(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active camera analysis session", exception);
        }
    }

    public List<CameraAnalysisSession> findByPrinterId(String printerId, int limit) {
        String sql = """
                SELECT id, printer_id, state, started_at, stopped_at, created_at, updated_at, message
                FROM camera_analysis_sessions
                WHERE printer_id = ?
                ORDER BY created_at DESC
                LIMIT ?;
                """;
        List<CameraAnalysisSession> sessions = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setInt(2, limit <= 0 ? 20 : limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sessions.add(mapSession(resultSet));
                }
            }
            return sessions;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list camera analysis sessions", exception);
        }
    }

    private static void bindSession(PreparedStatement statement, CameraAnalysisSession session) throws SQLException {
        statement.setString(1, session.id());
        statement.setString(2, session.printerId());
        statement.setString(3, session.state().name());
        statement.setString(4, session.startedAt().map(Instant::toString).orElse(null));
        statement.setString(5, session.stoppedAt().map(Instant::toString).orElse(null));
        statement.setString(6, session.createdAt().toString());
        statement.setString(7, session.updatedAt().toString());
        statement.setString(8, session.message().orElse(null));
    }

    private static CameraAnalysisSession mapSession(ResultSet resultSet) throws SQLException {
        return new CameraAnalysisSession(
                resultSet.getString("id"),
                resultSet.getString("printer_id"),
                CameraAnalysisSessionState.valueOf(resultSet.getString("state")),
                parseOptionalInstant(resultSet.getString("started_at")),
                parseOptionalInstant(resultSet.getString("stopped_at")),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at")),
                resultSet.getString("message"));
    }

    private static Instant parseOptionalInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
