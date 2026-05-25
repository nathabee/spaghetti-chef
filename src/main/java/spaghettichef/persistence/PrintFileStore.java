package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.job.PrintFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PrintFileStore {

    public void save(PrintFile printFile) {
        if (printFile == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFile"));
        }

        String sql = """
                INSERT INTO print_files (
                    id,
                    original_filename,
                    path,
                    size_bytes,
                    media_type,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    original_filename = excluded.original_filename,
                    path = excluded.path,
                    size_bytes = excluded.size_bytes,
                    media_type = excluded.media_type
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printFile.id());
            statement.setString(2, printFile.originalFilename());
            statement.setString(3, printFile.path());
            statement.setLong(4, printFile.sizeBytes());
            statement.setString(5, printFile.mediaType());
            statement.setString(6, printFile.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_PRINT_FILE, exception);
        }
    }

    public Optional<PrintFile> findById(String printFileId) {
        if (printFileId == null || printFileId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_ID_MUST_NOT_BE_BLANK);
        }

        String sql = """
                SELECT
                    id,
                    original_filename,
                    path,
                    size_bytes,
                    media_type,
                    created_at
                FROM print_files
                WHERE id = ?
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printFileId.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINT_FILE, exception);
        }
    }

    public List<PrintFile> findAll() {
        String sql = """
                SELECT
                    id,
                    original_filename,
                    path,
                    size_bytes,
                    media_type,
                    created_at
                FROM print_files
                ORDER BY created_at DESC
                """;

        List<PrintFile> printFiles = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                printFiles.add(mapRow(resultSet));
            }

            return printFiles;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINT_FILES, exception);
        }
    }

    private PrintFile mapRow(ResultSet resultSet) throws SQLException {
        return new PrintFile(
                resultSet.getString("id"),
                resultSet.getString("original_filename"),
                resultSet.getString("path"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("media_type"),
                Instant.parse(resultSet.getString("created_at"))
        );
    }
}
