package spaghettichef.persistence;

import spaghettichef.OperationMessages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public final class PrintFileSettingsStore {

    private static final String SETTINGS_ID = "default";

    public PrintFileSettings load() {
        String sql = """
                SELECT storage_directory
                FROM print_file_settings
                WHERE id = ?
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, SETTINGS_ID);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return PrintFileSettings.defaults();
                }

                return new PrintFileSettings(resultSet.getString("storage_directory"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINT_FILE_SETTINGS, exception);
        }
    }

    public PrintFileSettings save(PrintFileSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFileSettings"));
        }

        String sql = """
                INSERT INTO print_file_settings (
                    id,
                    storage_directory,
                    updated_at
                ) VALUES (?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    storage_directory = excluded.storage_directory,
                    updated_at = excluded.updated_at
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, SETTINGS_ID);
            statement.setString(2, settings.storageDirectory());
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
            return settings;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_PRINT_FILE_SETTINGS, exception);
        }
    }
}
