package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.job.JobState;
import spaghettichef.job.JobType;
import spaghettichef.job.PrintJob;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PrintJobStore {

    public void save(PrintJob job) {
        if (job == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("job"));
        }

        String sql = """
                INSERT INTO print_jobs (
                    id,
                    name,
                    type,
                    state,
                    printer_id,
                    print_file_id,
                    printer_sd_file_id,
                    target_temperature,
                    fan_speed,
                    failure_reason,
                    failure_detail,
                    created_at,
                    updated_at,
                    started_at,
                    finished_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    type = excluded.type,
                    state = excluded.state,
                    printer_id = excluded.printer_id,
                    print_file_id = excluded.print_file_id,
                    printer_sd_file_id = excluded.printer_sd_file_id,
                    target_temperature = excluded.target_temperature,
                    fan_speed = excluded.fan_speed,
                    failure_reason = excluded.failure_reason,
                    failure_detail = excluded.failure_detail,
                    updated_at = excluded.updated_at,
                    started_at = excluded.started_at,
                    finished_at = excluded.finished_at
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            bindJob(statement, job);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_PRINT_JOB, exception);
        }
    }

    public Optional<PrintJob> findById(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_ID_MUST_NOT_BE_BLANK);
        }

        String sql = """
                SELECT
                    id,
                    name,
                    type,
                    state,
                    printer_id,
                    print_file_id,
                    printer_sd_file_id,
                    target_temperature,
                    fan_speed,
                    failure_reason,
                    failure_detail,
                    created_at,
                    updated_at,
                    started_at,
                    finished_at
                FROM print_jobs
                WHERE id = ?
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, jobId.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINT_JOB, exception);
        }
    }

    public List<PrintJob> findRecent(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(OperationMessages.INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO);
        }

        String sql = """
                SELECT
                    id,
                    name,
                    type,
                    state,
                    printer_id,
                    print_file_id,
                    printer_sd_file_id,
                    target_temperature,
                    fan_speed,
                    failure_reason,
                    failure_detail,
                    created_at,
                    updated_at,
                    started_at,
                    finished_at
                FROM print_jobs
                ORDER BY updated_at DESC
                LIMIT ?
                """;

        List<PrintJob> jobs = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jobs.add(mapRow(resultSet));
                }
            }

            return jobs;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINT_JOBS, exception);
        }
    }

    public Optional<PrintJob> findActivePrintFileJobByPrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        String sql = """
                SELECT
                    id,
                    name,
                    type,
                    state,
                    printer_id,
                    print_file_id,
                    printer_sd_file_id,
                    target_temperature,
                    fan_speed,
                    failure_reason,
                    failure_detail,
                    created_at,
                    updated_at,
                    started_at,
                    finished_at
                FROM print_jobs
                WHERE printer_id = ?
                    AND type = ?
                    AND state = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerId.trim());
            statement.setString(2, JobType.PRINT_FILE.name());
            statement.setString(3, JobState.RUNNING.name());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINT_JOB, exception);
        }
    }

    public void update(PrintJob job) {
        if (job == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("job"));
        }

        String sql = """
                UPDATE print_jobs
                SET
                    name = ?,
                    type = ?,
                    state = ?,
                    printer_id = ?,
                    print_file_id = ?,
                    printer_sd_file_id = ?,
                    target_temperature = ?,
                    fan_speed = ?,
                    failure_reason = ?,
                    failure_detail = ?,
                    updated_at = ?,
                    started_at = ?,
                    finished_at = ?
                WHERE id = ?
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, job.name());
            statement.setString(2, job.type().name());
            statement.setString(3, job.state().name());
            statement.setString(4, job.printerId());
            statement.setString(5, job.printFileId());
            statement.setString(6, job.printerSdFileId());

            if (job.targetTemperature() == null) {
                statement.setNull(7, java.sql.Types.REAL);
            } else {
                statement.setDouble(7, job.targetTemperature());
            }

            if (job.fanSpeed() == null) {
                statement.setNull(8, java.sql.Types.INTEGER);
            } else {
                statement.setInt(8, job.fanSpeed());
            }

            statement.setString(9, job.failureReason());
            statement.setString(10, job.failureDetail());
            statement.setString(11, job.updatedAt().toString());
            statement.setString(12, job.startedAt() == null ? null : job.startedAt().toString());
            statement.setString(13, job.finishedAt() == null ? null : job.finishedAt().toString());
            statement.setString(14, job.id());

            int updatedRows = statement.executeUpdate();

            if (updatedRows == 0) {
                throw new IllegalStateException(OperationMessages.FAILED_TO_UPDATE_PRINT_JOB);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_UPDATE_PRINT_JOB, exception);
        }
    }

    public boolean delete(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_ID_MUST_NOT_BE_BLANK);
        }

        try (Connection connection = Database.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                deleteByJobId(connection, "print_job_execution_steps", jobId);
                deleteByJobId(connection, "printer_events", jobId);

                int deletedRows;
                String sql = """
                        DELETE FROM print_jobs
                        WHERE id = ?
                        """;

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, jobId.trim());
                    deletedRows = statement.executeUpdate();
                }

                connection.commit();
                connection.setAutoCommit(originalAutoCommit);
                return deletedRows > 0;
            } catch (SQLException exception) {
                connection.rollback();
                connection.setAutoCommit(originalAutoCommit);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_DELETE_PRINT_JOB, exception);
        }
    }

    private void deleteByJobId(Connection connection, String tableName, String jobId) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE job_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId.trim());
            statement.executeUpdate();
        }
    }

    private void bindJob(PreparedStatement statement, PrintJob job) throws SQLException {
        statement.setString(1, job.id());
        statement.setString(2, job.name());
        statement.setString(3, job.type().name());
        statement.setString(4, job.state().name());
        statement.setString(5, job.printerId());
        statement.setString(6, job.printFileId());
        statement.setString(7, job.printerSdFileId());

        if (job.targetTemperature() == null) {
            statement.setNull(8, java.sql.Types.REAL);
        } else {
            statement.setDouble(8, job.targetTemperature());
        }

        if (job.fanSpeed() == null) {
            statement.setNull(9, java.sql.Types.INTEGER);
        } else {
            statement.setInt(9, job.fanSpeed());
        }

        statement.setString(10, job.failureReason());
        statement.setString(11, job.failureDetail());
        statement.setString(12, job.createdAt().toString());
        statement.setString(13, job.updatedAt().toString());
        statement.setString(14, job.startedAt() == null ? null : job.startedAt().toString());
        statement.setString(15, job.finishedAt() == null ? null : job.finishedAt().toString());
    }

    private PrintJob mapRow(ResultSet resultSet) throws SQLException {
        return new PrintJob(
                resultSet.getString("id"),
                resultSet.getString("name"),
                JobType.valueOf(resultSet.getString("type")),
                JobState.valueOf(resultSet.getString("state")),
                resultSet.getString("printer_id"),
                resultSet.getString("print_file_id"),
                resultSet.getString("printer_sd_file_id"),
                nullableDouble(resultSet, "target_temperature"),
                nullableInteger(resultSet, "fan_speed"),
                resultSet.getString("failure_reason"),
                resultSet.getString("failure_detail"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at")),
                nullableInstant(resultSet, "started_at"),
                nullableInstant(resultSet, "finished_at")
        );
    }

    private Double nullableDouble(ResultSet resultSet, String columnName) throws SQLException {
        double value = resultSet.getDouble(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    private Integer nullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    private Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
