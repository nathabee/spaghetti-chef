package printerhub.persistence;

import printerhub.OperationMessages;
import printerhub.security.LocalRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SecuritySettingsStore {
    private static final String SETTINGS_ID = "default";

    public SecuritySettings load() {
        String sql = """
                SELECT
                    security_enabled,
                    default_role,
                    require_dangerous_action_confirmation
                FROM security_settings
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SETTINGS_ID);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return SecuritySettings.defaults();
                }

                return new SecuritySettings(
                        resultSet.getInt("security_enabled") == 1,
                        LocalRole.valueOf(resultSet.getString("default_role")),
                        resultSet.getInt("require_dangerous_action_confirmation") == 1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_SECURITY_SETTINGS, exception);
        }
    }

    public SecuritySettings save(SecuritySettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("securitySettings"));
        }

        String sql = """
                INSERT INTO security_settings (
                    id,
                    security_enabled,
                    default_role,
                    require_dangerous_action_confirmation,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO UPDATE SET
                    security_enabled = excluded.security_enabled,
                    default_role = excluded.default_role,
                    require_dangerous_action_confirmation = excluded.require_dangerous_action_confirmation,
                    updated_at = CURRENT_TIMESTAMP;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SETTINGS_ID);
            statement.setInt(2, settings.securityEnabled() ? 1 : 0);
            statement.setString(3, settings.defaultRole().name());
            statement.setInt(4, settings.requireDangerousActionConfirmation() ? 1 : 0);
            statement.executeUpdate();
            return settings;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_SECURITY_SETTINGS, exception);
        }
    }
}
