package printerhub.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DangerousActionGuardTest {
    private final DangerousActionGuard guard = new DangerousActionGuard();

    @Test
    void resolvesCommandDangerousActions() {
        assertEquals(
                DangerousAction.HEATING,
                guard.resolve("POST", "/printers/p1/commands", "{\"command\":\"M104 S200\"}").orElseThrow());
        assertEquals(
                DangerousAction.HOMING,
                guard.resolve("POST", "/printers/p1/commands", "{\"command\":\"G28\"}").orElseThrow());
        assertEquals(
                DangerousAction.MOVEMENT,
                guard.resolve("POST", "/printers/p1/commands", "{\"command\":\"G1 X10\"}").orElseThrow());
        assertEquals(
                DangerousAction.RAW_COMMAND,
                guard.resolve("POST", "/printers/p1/commands", "{\"command\":\"M999\"}").orElseThrow());
        assertFalse(guard.resolve("POST", "/printers/p1/commands", "{\"command\":\"M105\"}").isPresent());
    }

    @Test
    void resolvesRiskyWorkflowActions() {
        assertEquals(
                DangerousAction.FILE_UPLOAD_OVERWRITE,
                guard.resolve("POST", "/printers/p1/sd-card/uploads", "{}").orElseThrow());
        assertEquals(
                DangerousAction.RECOVERY_CLOSE_UPLOAD,
                guard.resolve("POST", "/printers/p1/sd-card/recovery/close-upload", "{}").orElseThrow());
        assertEquals(
                DangerousAction.SD_DELETE,
                guard.resolve("DELETE", "/printer-sd-files/file-1", "{}").orElseThrow());
        assertEquals(
                DangerousAction.PRINT_START,
                guard.resolve("POST", "/jobs/job-1/start", "{}").orElseThrow());
        assertEquals(
                DangerousAction.PRINT_CANCEL,
                guard.resolve("POST", "/jobs/job-1/cancel", "{}").orElseThrow());
    }

    @Test
    void requiresConfirmedFlag() {
        ConfirmationRequiredException exception = assertThrows(
                ConfirmationRequiredException.class,
                () -> guard.requireConfirmed(DangerousAction.HEATING, "{\"confirmed\":false}"));

        assertEquals(DangerousAction.HEATING, exception.requiredConfirmation());
        guard.requireConfirmed(DangerousAction.HEATING, "{\"confirmed\":true}");
    }
}
