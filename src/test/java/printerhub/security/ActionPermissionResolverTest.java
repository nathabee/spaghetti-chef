package printerhub.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionPermissionResolverTest {
    private final ActionPermissionResolver resolver = new ActionPermissionResolver();

    @Test
    void resolvesPrinterCrudPermissions() {
        assertEquals(Permission.PRINTER_VIEW, resolver.resolve("GET", "/printers", "").orElseThrow());
        assertEquals(Permission.PRINTER_CONFIGURE, resolver.resolve("POST", "/printers", "").orElseThrow());
        assertEquals(Permission.PRINTER_CONFIGURE, resolver.resolve("PUT", "/printers/printer-1", "").orElseThrow());
        assertEquals(Permission.PRINTER_CONFIGURE, resolver.resolve("DELETE", "/printers/printer-1", "").orElseThrow());
    }

    @Test
    void resolvesJobControlPermissions() {
        assertEquals(Permission.JOB_CREATE, resolver.resolve("POST", "/jobs", "").orElseThrow());
        assertEquals(Permission.JOB_START, resolver.resolve("POST", "/jobs/job-1/start", "").orElseThrow());
        assertEquals(Permission.JOB_PAUSE, resolver.resolve("POST", "/jobs/job-1/pause", "").orElseThrow());
        assertEquals(Permission.JOB_RESUME, resolver.resolve("POST", "/jobs/job-1/resume", "").orElseThrow());
        assertEquals(Permission.JOB_CANCEL, resolver.resolve("POST", "/jobs/job-1/cancel", "").orElseThrow());
        assertEquals(Permission.JOB_RESTART, resolver.resolve("POST", "/jobs/job-1/restart", "").orElseThrow());
        assertEquals(Permission.JOB_DELETE, resolver.resolve("DELETE", "/jobs/job-1", "").orElseThrow());
    }

    @Test
    void resolvesCommandPermissionsFromBody() {
        assertEquals(
                Permission.COMMAND_READ,
                resolver.resolve("POST", "/printers/printer-1/commands", "{\"command\":\"M105\"}").orElseThrow());
        assertEquals(
                Permission.COMMAND_SAFE_CONTROL,
                resolver.resolve("POST", "/printers/printer-1/commands", "{\"command\":\"M104\"}").orElseThrow());
        assertEquals(
                Permission.COMMAND_RAW,
                resolver.resolve("POST", "/printers/printer-1/commands", "{\"command\":\"G1 X100\"}").orElseThrow());
    }
}
