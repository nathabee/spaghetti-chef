package printerhub.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import printerhub.config.RuntimeDefaults;

public final class CameraSnapshotMetadataStore {

    public CameraSnapshotMetadata save(CameraSnapshotMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("camera snapshot metadata must not be null");
        }

        String sql = """
                INSERT INTO camera_snapshot_metadata (
                    printer_id,
                    captured_at,
                    content_type,
                    file_path,
                    width,
                    height,
                    source_description
                )
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, metadata.printerId());
            statement.setString(2, metadata.capturedAt().toString());
            statement.setString(3, metadata.contentType());
            statement.setString(4, metadata.filePath());

            if (metadata.width().isPresent()) {
                statement.setInt(5, metadata.width().getAsInt());
            } else {
                statement.setObject(5, null);
            }

            if (metadata.height().isPresent()) {
                statement.setInt(6, metadata.height().getAsInt());
            } else {
                statement.setObject(6, null);
            }

            statement.setString(7, metadata.sourceDescription().orElse(null));

            statement.executeUpdate();
            return metadata;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera snapshot metadata", exception);
        }
    }

    public Optional<CameraSnapshotMetadata> findLatestByPrinterId(String printerId) {
        String normalizedPrinterId = requirePrinterId(printerId);

        String sql = """
                SELECT
                    id,
                    printer_id,
                    captured_at,
                    content_type,
                    file_path,
                    width,
                    height,
                    source_description
                FROM camera_snapshot_metadata
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapMetadata(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load latest camera snapshot metadata", exception);
        }
    }

    public List<CameraSnapshotMetadata> findRecentByPrinterId(String printerId, int limit) {
        String normalizedPrinterId = requirePrinterId(printerId);

        int safeLimit = limit <= 0
                ? RuntimeDefaults.DEFAULT_RECENT_SNAPSHOT_LIMIT
                : limit;

        String sql = """
                SELECT
                    id,
                    printer_id,
                    captured_at,
                    content_type,
                    file_path,
                    width,
                    height,
                    source_description
                FROM camera_snapshot_metadata
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT ?;
                """;

        List<CameraSnapshotMetadata> metadata = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    metadata.add(mapMetadata(resultSet));
                }
            }

            return metadata;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera snapshot metadata", exception);
        }
    }

    private CameraSnapshotMetadata mapMetadata(ResultSet resultSet) throws SQLException {
        return new CameraSnapshotMetadata(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                parseInstant(resultSet.getString("captured_at")),
                resultSet.getString("content_type"),
                resultSet.getString("file_path"),
                readNullableInt(resultSet, "width"),
                readNullableInt(resultSet, "height"),
                resultSet.getString("source_description")
        );
    }

    private static Integer readNullableInt(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    private static Instant parseInstant(String storedTimestamp) {
        if (storedTimestamp == null || storedTimestamp.isBlank()) {
            throw new IllegalStateException("Invalid stored camera snapshot timestamp");
        }

        try {
            return Instant.parse(storedTimestamp);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Invalid stored camera snapshot timestamp: " + storedTimestamp,
                    exception);
        }
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        return printerId.trim();
    }
}