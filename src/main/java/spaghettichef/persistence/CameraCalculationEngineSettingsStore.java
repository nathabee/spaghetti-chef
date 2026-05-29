package spaghettichef.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CameraCalculationEngineSettingsStore {

    public List<CameraCalculationEngineSettings> findAll() {
        String sql = """
                SELECT
                    engine_name,
                    engine_label,
                    enabled,
                    default_method_name,
                    default_confidence_threshold,
                    default_parameter_json,
                    default_cli_method,
                    executable_path,
                    timeout_ms,
                    sort_order,
                    created_at,
                    updated_at
                FROM camera_calculation_engine_settings
                ORDER BY sort_order ASC, engine_label ASC, engine_name ASC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            List<CameraCalculationEngineSettings> settings = new ArrayList<>();
            while (resultSet.next()) {
                settings.add(mapSettings(resultSet));
            }
            return settings;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera calculation engine settings", exception);
        }
    }

    public Optional<CameraCalculationEngineSettings> findByEngineName(String engineName) {
        String normalizedEngineName = requireEngineName(engineName);
        String sql = """
                SELECT
                    engine_name,
                    engine_label,
                    enabled,
                    default_method_name,
                    default_confidence_threshold,
                    default_parameter_json,
                    default_cli_method,
                    executable_path,
                    timeout_ms,
                    sort_order,
                    created_at,
                    updated_at
                FROM camera_calculation_engine_settings
                WHERE engine_name = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedEngineName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSettings(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera calculation engine settings", exception);
        }
    }

    public CameraCalculationEngineSettings save(CameraCalculationEngineSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("camera calculation engine settings must not be null");
        }

        CameraCalculationEngineSettings updated = settings.withUpdatedAt(Instant.now());
        String sql = """
                INSERT INTO camera_calculation_engine_settings (
                    engine_name,
                    engine_label,
                    enabled,
                    default_method_name,
                    default_confidence_threshold,
                    default_parameter_json,
                    default_cli_method,
                    executable_path,
                    timeout_ms,
                    sort_order,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(engine_name) DO UPDATE SET
                    engine_label = excluded.engine_label,
                    enabled = excluded.enabled,
                    default_method_name = excluded.default_method_name,
                    default_confidence_threshold = excluded.default_confidence_threshold,
                    default_parameter_json = excluded.default_parameter_json,
                    default_cli_method = excluded.default_cli_method,
                    executable_path = excluded.executable_path,
                    timeout_ms = excluded.timeout_ms,
                    sort_order = excluded.sort_order,
                    updated_at = excluded.updated_at;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            bindSettings(statement, updated);
            statement.executeUpdate();
            return updated;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera calculation engine settings", exception);
        }
    }

    public void ensureBuiltInDefaults() {
        for (CameraCalculationEngineSettings settings
                : CameraCalculationEngineSettingsDefaults.builtInDefaults(Instant.now())) {
            insertIfMissing(settings);
        }
    }

    private void insertIfMissing(CameraCalculationEngineSettings settings) {
        String sql = """
                INSERT OR IGNORE INTO camera_calculation_engine_settings (
                    engine_name,
                    engine_label,
                    enabled,
                    default_method_name,
                    default_confidence_threshold,
                    default_parameter_json,
                    default_cli_method,
                    executable_path,
                    timeout_ms,
                    sort_order,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            bindSettings(statement, settings);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize camera calculation engine settings", exception);
        }
    }

    static void insertIfMissing(Connection connection, CameraCalculationEngineSettings settings) throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO camera_calculation_engine_settings (
                    engine_name,
                    engine_label,
                    enabled,
                    default_method_name,
                    default_confidence_threshold,
                    default_parameter_json,
                    default_cli_method,
                    executable_path,
                    timeout_ms,
                    sort_order,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSettings(statement, settings);
            statement.executeUpdate();
        }
    }

    private static void bindSettings(
            PreparedStatement statement,
            CameraCalculationEngineSettings settings) throws SQLException {
        statement.setString(1, settings.engineName());
        statement.setString(2, settings.engineLabel());
        statement.setInt(3, settings.enabled() ? 1 : 0);
        statement.setString(4, settings.defaultMethodName());
        statement.setDouble(5, settings.defaultConfidenceThreshold());
        statement.setString(6, settings.defaultParameterJson());
        statement.setString(7, settings.defaultCliMethod());
        statement.setString(8, settings.executablePath());
        statement.setInt(9, settings.timeoutMs());
        statement.setInt(10, settings.sortOrder());
        statement.setString(11, settings.createdAt().toString());
        statement.setString(12, settings.updatedAt().toString());
    }

    private CameraCalculationEngineSettings mapSettings(ResultSet resultSet) throws SQLException {
        return new CameraCalculationEngineSettings(
                resultSet.getString("engine_name"),
                resultSet.getString("engine_label"),
                resultSet.getInt("enabled") == 1,
                resultSet.getString("default_method_name"),
                resultSet.getDouble("default_confidence_threshold"),
                resultSet.getString("default_parameter_json"),
                resultSet.getString("default_cli_method"),
                resultSet.getString("executable_path"),
                resultSet.getInt("timeout_ms"),
                resultSet.getInt("sort_order"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at")));
    }

    private static String requireEngineName(String engineName) {
        if (engineName == null || engineName.isBlank()) {
            throw new IllegalArgumentException("engineName must not be blank");
        }
        return engineName.trim();
    }
}
