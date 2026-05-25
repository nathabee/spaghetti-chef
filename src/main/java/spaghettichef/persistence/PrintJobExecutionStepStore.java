package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.job.PrintJobExecutionStep;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PrintJobExecutionStepStore {

    public PrintJobExecutionStep save(PrintJobExecutionStep step) {
        if (step == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("step"));
        }

        String sql = """
                INSERT INTO print_job_execution_steps (
                    job_id,
                    step_index,
                    step_name,
                    wire_command,
                    response,
                    outcome,
                    success,
                    failure_reason,
                    failure_detail,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, step.jobId());
            statement.setInt(2, step.stepIndex());
            statement.setString(3, step.stepName());
            statement.setString(4, step.wireCommand());
            statement.setString(5, step.response());
            statement.setString(6, step.outcome());
            statement.setInt(7, step.success() ? 1 : 0);
            statement.setString(8, step.failureReason());
            statement.setString(9, step.failureDetail());
            statement.setString(10, step.createdAt().toString());

            statement.executeUpdate();
            return step;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save print job execution step.", exception);
        }
    }

    public List<PrintJobExecutionStep> findByJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_ID_MUST_NOT_BE_BLANK);
        }

        String sql = """
                SELECT
                    id,
                    job_id,
                    step_index,
                    step_name,
                    wire_command,
                    response,
                    outcome,
                    success,
                    failure_reason,
                    failure_detail,
                    created_at
                FROM print_job_execution_steps
                WHERE job_id = ?
                ORDER BY step_index ASC, id ASC
                """;

        List<PrintJobExecutionStep> steps = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, jobId.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    steps.add(mapRow(resultSet));
                }
            }

            return steps;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load print job execution steps.", exception);
        }
    }

    private PrintJobExecutionStep mapRow(ResultSet resultSet) throws SQLException {
        return new PrintJobExecutionStep(
                resultSet.getLong("id"),
                resultSet.getString("job_id"),
                resultSet.getInt("step_index"),
                resultSet.getString("step_name"),
                resultSet.getString("wire_command"),
                resultSet.getString("response"),
                resultSet.getString("outcome"),
                resultSet.getInt("success") != 0,
                resultSet.getString("failure_reason"),
                resultSet.getString("failure_detail"),
                Instant.parse(resultSet.getString("created_at"))
        );
    }
}