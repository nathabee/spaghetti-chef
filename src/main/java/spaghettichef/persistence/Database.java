package spaghettichef.persistence;

import spaghettichef.OperationMessages;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(DatabaseConfig.jdbcUrl());
        } catch (SQLException exception) {
            throw new SQLException(OperationMessages.failedToOpenDatabaseConnection(
                    DatabaseConfig.databaseFile()
            ), exception);
        }
    }
}