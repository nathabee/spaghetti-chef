package spaghettichef.job;

import org.junit.jupiter.api.Test;
import spaghettichef.PrinterPort;
import spaghettichef.SerialIOMode;
import spaghettichef.runtime.PrinterRuntimeNode;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PrinterActionGuardTest {

    private final PrinterActionGuard guard = new PrinterActionGuard();

    @Test
    void rejectsDisabledPrinter() {
        PrintJob job = assignedJob("job-1", JobType.HOME_AXES, "printer-1", null, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", false);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertFalse(decision.allowed());
        assertEquals(JobFailureReason.PRINTER_DISABLED, decision.failureReason());
    }

    @Test
    void rejectsBusyPrinter() {
        PrintJob job = assignedJob("job-2", JobType.HOME_AXES, "printer-1", null, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", true);
        node.beginJobExecution("other-job");

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertFalse(decision.allowed());
        assertEquals(JobFailureReason.PRINTER_BUSY, decision.failureReason());
    }

    @Test
    void rejectsMissingPrinterIdOnJob() {
        PrintJob job = new PrintJob(
                "job-3",
                "Home axes",
                JobType.HOME_AXES,
                JobState.CREATED,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-04T08:00:00Z"),
                Instant.parse("2026-05-04T08:00:00Z"),
                null,
                null
        );
        PrinterRuntimeNode node = runtimeNode("printer-1", true);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertFalse(decision.allowed());
        assertEquals(JobFailureReason.PRECONDITION_FAILED, decision.failureReason());
    }

    @Test
    void rejectsMismatchedPrinterId() {
        PrintJob job = assignedJob("job-4", JobType.HOME_AXES, "printer-2", null, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", true);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertFalse(decision.allowed());
        assertEquals(JobFailureReason.PRECONDITION_FAILED, decision.failureReason());
    }

    @Test
    void rejectsMissingTargetTemperatureForNozzleJob() {
        PrintJob job = assignedJob("job-5", JobType.SET_NOZZLE_TEMPERATURE, "printer-1", null, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", true);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertFalse(decision.allowed());
        assertEquals(JobFailureReason.INVALID_PARAMETER, decision.failureReason());
    }

    @Test
    void rejectsMissingTargetTemperatureForBedJob() {
        PrintJob job = assignedJob("job-6", JobType.SET_BED_TEMPERATURE, "printer-1", null, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", true);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertFalse(decision.allowed());
        assertEquals(JobFailureReason.INVALID_PARAMETER, decision.failureReason());
    }

    @Test
    void rejectsMissingFanSpeed() {
        PrintJob job = assignedJob("job-7", JobType.SET_FAN_SPEED, "printer-1", null, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", true);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertFalse(decision.allowed());
        assertEquals(JobFailureReason.INVALID_PARAMETER, decision.failureReason());
    }

    @Test
    void acceptsValidHomeAxesJob() {
        PrintJob job = assignedJob("job-8", JobType.HOME_AXES, "printer-1", null, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", true);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertTrue(decision.allowed());
        assertNull(decision.failureReason());
    }

    @Test
    void acceptsValidTemperatureJob() {
        PrintJob job = assignedJob("job-9", JobType.SET_NOZZLE_TEMPERATURE, "printer-1", 200.0, null);
        PrinterRuntimeNode node = runtimeNode("printer-1", true);

        PrinterActionGuard.GuardDecision decision = guard.validateForExecution(job, node);

        assertTrue(decision.allowed());
        assertNull(decision.failureReason());
    }

    private PrintJob assignedJob(
            String id,
            JobType type,
            String printerId,
            Double targetTemperature,
            Integer fanSpeed
    ) {
        Instant now = Instant.parse("2026-05-04T08:00:00Z");

        return new PrintJob(
                id,
                "Test job " + id,
                type,
                JobState.ASSIGNED,
                printerId,
                targetTemperature,
                fanSpeed,
                null,
                null,
                now,
                now,
                null,
                null
        );
    }

    private PrinterRuntimeNode runtimeNode(String printerId, boolean enabled) {
        return new PrinterRuntimeNode(
                printerId,
                "Printer " + printerId,
                "SIM_PORT",
                "sim",
                new NoOpPrinterPort(),
                enabled
        );
    }

    private static final class NoOpPrinterPort implements PrinterPort {
        @Override
        public void connect() {
        }

        @Override
        public String sendRawLine(String line) {
            return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
        }

        @Override
        public String sendRawLine(String line, SerialIOMode mode) {
            return "ok";
        }

        @Override
        public void writeRawLine(String line, SerialIOMode mode) {
        }

        @Override
        public String readRawResponse(SerialIOMode mode) {
            return "ok";
        }

        @Override
        public java.util.List<String> sendRawLinesPipelined(java.util.List<String> lines, SerialIOMode mode) {
            if (lines == null || lines.isEmpty()) {
                return java.util.List.of();
            }

            java.util.List<String> responses = new java.util.ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                responses.add("ok");
            }
            return responses;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
        }

        @Override
        public String sendCommand(String command) {
            return "ok";
        }

        @Override
        public void disconnect() {
        }
    }
}
