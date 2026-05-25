package spaghettichef.persistence;

import java.nio.file.Path;

import spaghettichef.OperationMessages;
import spaghettichef.config.RuntimeDefaults;

public final class DatabaseConfig {

    private DatabaseConfig() {
    }

    public static String databaseFile() {
        String configuredFile = System.getProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);

        if (configuredFile == null || configuredFile.isBlank()) {
            return RuntimeDefaults.DEFAULT_DATABASE_FILE;
        }

        String normalized = configuredFile.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.DATABASE_FILE_MUST_NOT_BE_BLANK);
        }

        return normalized;
    }

    public static String jdbcUrl() {
        return RuntimeDefaults.SQLITE_JDBC_PREFIX + databaseFile();
    }

    public static Path dataDirectory() {
        Path databasePath = Path.of(databaseFile()).toAbsolutePath().normalize();
        Path parent = databasePath.getParent();
        if (parent == null) {
            return Path.of(".").toAbsolutePath().normalize();
        }
        return parent;
    }
}
