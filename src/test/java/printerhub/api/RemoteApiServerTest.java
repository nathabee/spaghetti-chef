package printerhub.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.OperationMessages;
import printerhub.OperatorMessageReportWriter;
import printerhub.PrinterPort;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.SerialIOMode;
import printerhub.command.PrinterCommandService;
import printerhub.command.SdCardService;
import printerhub.job.AsyncPrintJobExecutor;
import printerhub.job.JobState;
import printerhub.job.PrintFileService;
import printerhub.job.PrintJobExecutionService;
import printerhub.job.PrintJobService;
import printerhub.job.PrinterActionGuard;
import printerhub.job.PrinterActionMapper;
import printerhub.job.PrinterSdFileService;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.CameraJob;
import printerhub.persistence.CameraJobStore;
import printerhub.persistence.CameraSnapshotEntry;
import printerhub.persistence.CameraSnapshotEntryStore;
import printerhub.persistence.MonitoringRulesStore;
import printerhub.persistence.PrintFileSettingsStore;
import printerhub.persistence.PrintFileStore;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterConfigurationStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrintJobExecutionStepStore;
import printerhub.persistence.PrinterSdFileStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeNodeFactory;
import printerhub.runtime.PrinterRuntimeStateCache;
import printerhub.command.SdCardUploadService;
import printerhub.persistence.SecuritySettings;
import printerhub.persistence.SecuritySettingsStore;
import printerhub.persistence.SerialTransferSettingsStore;
import printerhub.security.LocalRole;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoteApiServerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void dashboardApiJsReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-api-js.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/api.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardViewModuleReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-view-module.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/views/farm-home.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardComponentModuleReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-component-module.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/components/nav.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void constructorFailsForInvalidPort() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createApiServerForConstructorTest(0));

        assertEquals("port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void getHealthReturnsOk() throws Exception {
        TestContext context = createContext("health.db");

        try {
            HttpResponse<String> response = context.get("/health");

            assertEquals(200, response.statusCode());
            assertEquals("{\"status\":\"ok\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getVersionReturnsRuntimeVersion() throws Exception {
        TestContext context = createContext("version.db");

        try {
            HttpResponse<String> response = context.get("/version");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"version\":"));
        } finally {
            context.close();
        }
    }

    @Test
    void wrongMethodOnHealthReturns405() throws Exception {
        TestContext context = createContext("health-405.db");

        try {
            HttpResponse<String> response = context.request("POST", "/health", null);

            assertEquals(405, response.statusCode());
            assertEquals("{\"error\":\"method_not_allowed\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrintersReturnsEmptyListInitially() throws Exception {
        TestContext context = createContext("printers-empty.db");

        try {
            HttpResponse<String> response = context.get("/printers");

            assertEquals(200, response.statusCode());
            assertEquals("{\"printers\":[]}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getMonitoringSettingsReturnsDefaults() throws Exception {
        TestContext context = createContext("monitoring-settings-get.db");

        try {
            HttpResponse<String> response = context.get("/settings/monitoring");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"pollIntervalSeconds\":"));
            assertTrue(response.body().contains("\"snapshotMinimumIntervalSeconds\":"));
            assertTrue(response.body().contains("\"temperatureDeltaThreshold\":"));
            assertTrue(response.body().contains("\"eventDeduplicationWindowSeconds\":"));
            assertTrue(response.body().contains("\"errorPersistenceBehavior\":\"DEDUPLICATED\""));
            assertTrue(response.body().contains("\"debugWireTracingEnabled\":false"));
        } finally {
            context.close();
        }
    }

    @Test
    void getMonitoringReturnsGlobalRuntimeSnapshot() throws Exception {
        TestContext context = createContext("monitoring-overview.db");

        try {
            HttpResponse<String> createPrinterResponse = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer One","portName":"COM1","mode":"simulated","enabled":true}
                            """);
            assertEquals(201, createPrinterResponse.statusCode());

            HttpResponse<String> createJobResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read firmware","type":"READ_FIRMWARE_INFO","printerId":"printer-1"}
                            """);
            assertEquals(201, createJobResponse.statusCode());

            HttpResponse<String> response = context.get("/monitoring");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"generatedAt\":"));
            assertTrue(response.body().contains("\"summary\":"));
            assertTrue(response.body().contains("\"totalPrinters\":1"));
            assertTrue(response.body().contains("\"enabledPrinters\":1"));
            assertTrue(response.body().contains("\"activeJobs\":"));
            assertTrue(response.body().contains("\"printers\":["));
            assertTrue(response.body().contains("\"displayName\":\"Printer One\""));
            assertTrue(response.body().contains("\"activeUploads\":["));
        } finally {
            context.close();
        }
    }

    @Test
    void putMonitoringSettingsUpdatesRules() throws Exception {
        TestContext context = createContext("monitoring-settings-put.db");

        try {
            HttpResponse<String> response = context.request(
                    "PUT",
                    "/settings/monitoring",
                    """
                            {"pollIntervalSeconds":12,"snapshotMinimumIntervalSeconds":45,"temperatureDeltaThreshold":2.5,"eventDeduplicationWindowSeconds":90,"errorPersistenceBehavior":"ALWAYS","debugWireTracingEnabled":true}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"pollIntervalSeconds\":12"));
            assertTrue(response.body().contains("\"snapshotMinimumIntervalSeconds\":45"));
            assertTrue(response.body().contains("\"temperatureDeltaThreshold\":2.50"));
            assertTrue(response.body().contains("\"eventDeduplicationWindowSeconds\":90"));
            assertTrue(response.body().contains("\"errorPersistenceBehavior\":\"ALWAYS\""));
            assertTrue(response.body().contains("\"debugWireTracingEnabled\":true"));
        } finally {
            context.close();
        }
    }

    @Test
    void getSerialTransferSettingsReturnsDefaults() throws Exception {
        TestContext context = createContext("serial-transfer-settings-get.db");

        try {
            HttpResponse<String> response = context.get("/settings/serial-transfer");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"sdUploadBatchSize\":5"));
            assertTrue(response.body().contains("\"sdUploadRecoveryWindowMultiplier\":2"));
            assertTrue(response.body().contains("\"sdUploadMaxErrors\":100"));
            assertTrue(response.body().contains("\"sdUploadMaxConsecutiveIdenticalResends\":10"));
            assertTrue(response.body().contains("\"sdUploadMinPerformancePercent\":5"));
            assertTrue(response.body().contains("\"sdUploadMaxRetriesPerLine\":3"));
            assertTrue(response.body().contains("\"fileStreamingReadTimeoutMs\":5000"));
            assertTrue(response.body().contains("\"fileStreamingQuietPeriodMs\":10"));
            assertTrue(response.body().contains("\"fileStreamingReadActivitySleepMs\":1"));
            assertTrue(response.body().contains("\"fileStreamingReadIdleSleepMs\":1"));
            assertTrue(response.body().contains("\"fileStreamingRecoveryReplayDelayMs\":15"));
        } finally {
            context.close();
        }
    }

    @Test
    void putSerialTransferSettingsUpdatesSettings() throws Exception {
        TestContext context = createContext("serial-transfer-settings-put.db");

        try {
            HttpResponse<String> response = context.request(
                    "PUT",
                    "/settings/serial-transfer",
                    """
                            {"sdUploadBatchSize":7,"sdUploadRecoveryWindowMultiplier":3,"sdUploadMaxErrors":200,"sdUploadMaxConsecutiveIdenticalResends":12,"sdUploadMinPerformancePercent":8,"sdUploadMaxRetriesPerLine":4,"fileStreamingReadTimeoutMs":6000,"fileStreamingQuietPeriodMs":20,"fileStreamingReadActivitySleepMs":2,"fileStreamingReadIdleSleepMs":3,"fileStreamingRecoveryReplayDelayMs":25}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"sdUploadBatchSize\":7"));
            assertTrue(response.body().contains("\"sdUploadRecoveryWindowMultiplier\":3"));
            assertTrue(response.body().contains("\"sdUploadMaxErrors\":200"));
            assertTrue(response.body().contains("\"sdUploadMaxConsecutiveIdenticalResends\":12"));
            assertTrue(response.body().contains("\"sdUploadMinPerformancePercent\":8"));
            assertTrue(response.body().contains("\"sdUploadMaxRetriesPerLine\":4"));
            assertTrue(response.body().contains("\"fileStreamingReadTimeoutMs\":6000"));
            assertTrue(response.body().contains("\"fileStreamingQuietPeriodMs\":20"));
            assertTrue(response.body().contains("\"fileStreamingReadActivitySleepMs\":2"));
            assertTrue(response.body().contains("\"fileStreamingReadIdleSleepMs\":3"));
            assertTrue(response.body().contains("\"fileStreamingRecoveryReplayDelayMs\":25"));
        } finally {
            context.close();
        }
    }

    @Test
    void getSecuritySettingsReturnsDefaults() throws Exception {
        TestContext context = createContext("security-settings-get.db");

        try {
            HttpResponse<String> response = context.get("/settings/security");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"securityEnabled\":false"));
            assertTrue(response.body().contains("\"defaultRole\":\"ADMIN\""));
            assertTrue(response.body().contains("\"requireDangerousActionConfirmation\":true"));
        } finally {
            context.close();
        }
    }

    @Test
    void putSecuritySettingsUpdatesSettings() throws Exception {
        TestContext context = createContext("security-settings-put.db");

        try {
            HttpResponse<String> response = context.request(
                    "PUT",
                    "/settings/security",
                    """
                            {"securityEnabled":true,"defaultRole":"OPERATOR","requireDangerousActionConfirmation":false}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"securityEnabled\":true"));
            assertTrue(response.body().contains("\"defaultRole\":\"OPERATOR\""));
            assertTrue(response.body().contains("\"requireDangerousActionConfirmation\":false"));
        } finally {
            context.close();
        }
    }

    @Test
    void getSecurityRolesReturnsBuiltInProfiles() throws Exception {
        TestContext context = createContext("security-roles-get.db");

        try {
            HttpResponse<String> response = context.get("/security/roles");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"role\":\"VIEWER\""));
            assertTrue(response.body().contains("\"role\":\"OPERATOR\""));
            assertTrue(response.body().contains("\"role\":\"ADMIN\""));
            assertTrue(response.body().contains("\"permissions\":["));
            assertTrue(response.body().contains("\"MANAGE_SECURITY\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getSecurityProfileReturnsDefaultRoleProfile() throws Exception {
        TestContext context = createContext("security-profile-get.db");

        try {
            HttpResponse<String> response = context.get("/security/profile");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"settings\":"));
            assertTrue(response.body().contains("\"roleProfile\":"));
            assertTrue(response.body().contains("\"role\":\"ADMIN\""));
        } finally {
            context.close();
        }
    }

    @Test
    void securityEnabledRejectsForbiddenStateChangingAction() throws Exception {
        TestContext context = createContext("security-forbidden-printer-create.db");

        try {
            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/settings/security",
                    """
                            {"securityEnabled":true,"defaultRole":"VIEWER","requireDangerousActionConfirmation":true}
                            """);
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);

            assertEquals(403, response.statusCode());
            assertEquals("{\"error\":\"Permission denied for role VIEWER: PRINTER_CONFIGURE\"}", response.body());
            assertTrue(context.printerRegistry.findById("printer-1").isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    void securityEnabledAllowsExplicitAdminRoleHeader() throws Exception {
        TestContext context = createContext("security-admin-printer-create.db");

        try {
            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/settings/security",
                    """
                            {"securityEnabled":true,"defaultRole":"VIEWER","requireDangerousActionConfirmation":true}
                            """);
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> response = context.requestAsRole(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """,
                    "ADMIN");

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"printer-1\""));
            assertTrue(context.printerRegistry.findById("printer-1").isPresent());
        } finally {
            context.close();
        }
    }

    @Test
    void securityEnabledAllowsViewerReadEndpoints() throws Exception {
        TestContext context = createContext("security-viewer-read.db");

        try {
            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/settings/security",
                    """
                            {"securityEnabled":true,"defaultRole":"VIEWER","requireDangerousActionConfirmation":true}
                            """);
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> response = context.get("/printers");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"printers\""));
        } finally {
            context.close();
        }
    }

    @Test
    void dangerousActionConfirmationRejectsMissingConfirmation() throws Exception {
        TestContext context = createContext("dangerous-confirmation-missing.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104 S200"}
                            """);

            assertEquals(428, response.statusCode());
            assertEquals(
                    "{\"error\":\"confirmation_required\",\"requiredConfirmation\":\"HEATING\"}",
                    response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void dangerousActionConfirmationAllowsConfirmedAction() throws Exception {
        TestContext context = createContext("dangerous-confirmation-accepted.db");

        try {
            HttpResponse<String> createPrinterResponse = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);
            assertEquals(201, createPrinterResponse.statusCode());

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104","targetTemperature":200,"confirmed":true,"confirmationReason":"Operator confirmed nozzle heating"}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"sentCommand\":\"M104 S200\""));
        } finally {
            context.close();
        }
    }

    @Test
    void dangerousActionConfirmationCanBeDisabled() throws Exception {
        TestContext context = createContext("dangerous-confirmation-disabled.db");

        try {
            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/settings/security",
                    """
                            {"securityEnabled":false,"defaultRole":"ADMIN","requireDangerousActionConfirmation":false}
                            """);
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> createPrinterResponse = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);
            assertEquals(201, createPrinterResponse.statusCode());

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104","targetTemperature":200}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"sentCommand\":\"M104 S200\""));
        } finally {
            context.close();
        }
    }

    @Test
    void operatorAuditRecordsAcceptedStateChangingAction() throws Exception {
        TestContext context = createContext("operator-audit-accepted.db");

        try {
            HttpResponse<String> createPrinterResponse = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);
            assertEquals(201, createPrinterResponse.statusCode());

            HttpResponse<String> auditResponse = context.get("/operator-audit");

            assertEquals(200, auditResponse.statusCode());
            assertTrue(auditResponse.body().contains("\"result\":\"ACCEPTED\""));
            assertTrue(auditResponse.body().contains("\"permission\":\"PRINTER_CONFIGURE\""));
            assertTrue(auditResponse.body().contains("\"role\":\"ADMIN\""));
            assertTrue(auditResponse.body().contains("\"actor\":\"local-dashboard\""));
        } finally {
            context.close();
        }
    }

    @Test
    void operatorAuditRecordsAuthorizationRejection() throws Exception {
        TestContext context = createContext("operator-audit-authorization-rejected.db");

        try {
            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/settings/security",
                    """
                            {"securityEnabled":true,"defaultRole":"VIEWER","requireDangerousActionConfirmation":false}
                            """);
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> createPrinterResponse = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);
            assertEquals(403, createPrinterResponse.statusCode());

            HttpResponse<String> auditResponse = context.requestAsRole("GET", "/operator-audit", null, "ADMIN");

            assertEquals(200, auditResponse.statusCode());
            assertTrue(auditResponse.body().contains("\"result\":\"REJECTED\""));
            assertTrue(auditResponse.body().contains("\"permission\":\"PRINTER_CONFIGURE\""));
            assertTrue(auditResponse.body().contains("Permission denied for role VIEWER"));
        } finally {
            context.close();
        }
    }

    @Test
    void operatorAuditRecordsConfirmationRejection() throws Exception {
        TestContext context = createContext("dangerous-confirmation-audit-rejected.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104","targetTemperature":200}
                            """);
            assertEquals(428, response.statusCode());

            HttpResponse<String> auditResponse = context.get("/operator-audit");

            assertEquals(200, auditResponse.statusCode());
            assertTrue(auditResponse.body().contains("\"result\":\"REJECTED\""));
            assertTrue(auditResponse.body().contains("\"dangerousAction\":\"HEATING\""));
            assertTrue(auditResponse.body().contains("\"targetType\":\"printer\""));
            assertTrue(auditResponse.body().contains("\"targetId\":\"printer-1\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintersCreatesPrinter() throws Exception {
        TestContext context = createContext("printers-post.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"printer-1\""));
            assertTrue(response.body().contains("\"displayName\":\"Printer 1\""));
            assertTrue(response.body().contains("\"portName\":\"SIM_PORT\""));
            assertTrue(response.body().contains("\"mode\":\"sim\""));
            assertTrue(response.body().contains("\"enabled\":true"));

            assertTrue(context.printerRegistry.findById("printer-1").isPresent());
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintersReturnsStableSerialPathGuidance() throws Exception {
        TestContext context = createContext("printers-post-stable-serial-path.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0","mode":"real","enabled":true}
                            """);

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"portName\":\"/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0\""));
            assertTrue(response.body().contains("\"serialPortKind\":\"STABLE_LINUX_BY_ID\""));
            assertTrue(response.body().contains("\"stableSerialPath\":true"));
            assertTrue(response.body().contains("\"serialPathWarning\":null"));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintersWarnsForUnstableLinuxUsbSerialPath() throws Exception {
        TestContext context = createContext("printers-post-unstable-serial-path.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"/dev/ttyUSB0","mode":"real","enabled":true}
                            """);

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"portName\":\"/dev/ttyUSB0\""));
            assertTrue(response.body().contains("\"serialPortKind\":\"UNSTABLE_LINUX_USB\""));
            assertTrue(response.body().contains("\"stableSerialPath\":false"));
            assertTrue(response.body().contains("Prefer /dev/serial/by-id/..."));
        } finally {
            context.close();
        }
    }

    @Test
    void optionsPrintersAllowsDashboardPreflight() throws Exception {
        TestContext context = createContext("printers-options.db");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + context.port + "/printers"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Origin", "http://localhost:5500")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Content-Type")
                    .build();

            HttpResponse<String> response = context.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(204, response.statusCode());
            assertEquals("*", response.headers().firstValue("access-control-allow-origin").orElse(""));
            assertTrue(response.headers()
                    .firstValue("access-control-allow-methods")
                    .orElse("")
                    .contains("POST"));
            assertTrue(response.headers()
                    .firstValue("access-control-allow-headers")
                    .orElse("")
                    .contains("Content-Type"));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintersWithMissingRequiredFieldReturns400() throws Exception {
        TestContext context = createContext("printers-post-400.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","portName":"SIM_PORT","mode":"sim"}
                            """);

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"displayName must not be blank\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandExecutesAllowedReadCommand() throws Exception {
        TestContext context = createContext("printer-command-m105.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M105"}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(response.body().contains("\"command\":\"M105\""));
            assertTrue(response.body().contains("\"sentCommand\":\"M105\""));
            assertTrue(response.body().contains("\"response\":\"ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandExecutesTemperatureCommandWithParameter() throws Exception {
        TestContext context = createContext("printer-command-m104.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104","targetTemperature":200}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"command\":\"M104\""));
            assertTrue(response.body().contains("\"sentCommand\":\"M104 S200\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandFailsForMissingTemperatureParameter() throws Exception {
        TestContext context = createContext("printer-command-m104-400.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104"}
                            """);

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"targetTemperature is required for command M104\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandFailsForInvalidCommand() throws Exception {
        TestContext context = createContext("printer-command-invalid.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"G0"}
                            """);

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"Invalid printer command: G0\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterSdCardFilesListsSimulatedFiles() throws Exception {
        TestContext context = createContext("printer-sd-card-files.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.get("/printers/printer-1/sd-card/files");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(response.body().contains("\"filename\":\"CUBE.GCO\""));
            assertTrue(response.body().contains("\"sizeBytes\":12345"));
            assertTrue(response.body().contains("\"filename\":\"BENCHY.GCO\""));
            assertTrue(response.body().contains("\"rawResponse\":\"Begin file list\\n"));

            HttpResponse<String> registeredResponse = context.get("/printer-sd-files?printerId=printer-1");
            assertEquals(200, registeredResponse.statusCode());
            assertTrue(registeredResponse.body().contains("\"firmwarePath\":\"CUBE.GCO\""));
            assertTrue(registeredResponse.body().contains("\"firmwarePath\":\"BENCHY.GCO\""));

            HttpResponse<String> eventsResponse = context.get("/printers/printer-1/events");

            assertEquals(200, eventsResponse.statusCode());
            assertTrue(eventsResponse.body().contains("\"eventType\":\"SD_CARD_FILES_LISTED\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterSdCardFilesRejectsWhilePrinterExecutionInProgress() throws Exception {
        TestContext context = createContext("printer-sd-card-files-busy.db");

        try {
            PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    true);
            context.configurationStore.save(node);
            context.printerRegistry.register(node);
            node.beginJobExecution("sd-upload:test");

            HttpResponse<String> response = context.get("/printers/printer-1/sd-card/files");

            assertEquals(409, response.statusCode());
            assertEquals("{\"error\":\"printer_busy\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandRejectsWhilePrinterExecutionInProgress() throws Exception {
        TestContext context = createContext("printer-command-busy.db");

        try {
            PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    true);
            context.configurationStore.save(node);
            context.printerRegistry.register(node);
            node.beginJobExecution("sd-upload:test");

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M105"}
                            """);

            assertEquals(409, response.statusCode());
            assertEquals("{\"error\":\"printer_busy\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterEventsReturnsPersistedCommandEvents() throws Exception {
        TestContext context = createContext("printer-events.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> commandResponse = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M114"}
                            """);

            assertEquals(200, commandResponse.statusCode());

            HttpResponse<String> eventsResponse = context.get("/printers/printer-1/events");

            assertEquals(200, eventsResponse.statusCode());
            assertTrue(eventsResponse.body().contains("\"events\":["));
            assertTrue(eventsResponse.body().contains("\"eventType\":\"COMMAND_EXECUTED\""));
            assertTrue(eventsResponse.body().contains("Manual command executed: M114"));
        } finally {
            context.close();
        }
    }

    @Test
    void getCameraSettingsReturnsDisabledDefaultForExistingPrinter() throws Exception {
        TestContext context = createContext("camera-settings-get-default.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.get("/printers/printer-1/camera/settings");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(response.body().contains("\"enabled\":false"));
            assertTrue(response.body().contains("\"sourceType\":\"disabled\""));
            assertTrue(response.body().contains("\"captureIntervalSeconds\":10"));
            assertTrue(response.body().contains("\"retentionSnapshotCount\":20"));
            assertTrue(response.body().contains("\"analysisEnabled\":false"));
            assertTrue(response.body().contains("\"safetyEnabled\":false"));
        } finally {
            context.close();
        }
    }

    @Test
    void putCameraSettingsEnablesSimulatedCamera() throws Exception {
        TestContext context = createContext("camera-settings-put-simulated.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","captureIntervalSeconds":15,"retentionSnapshotCount":30}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(response.body().contains("\"enabled\":true"));
            assertTrue(response.body().contains("\"sourceType\":\"simulated\""));
            assertTrue(response.body().contains("\"sourceValue\":\"default\""));
            assertTrue(response.body().contains("\"captureIntervalSeconds\":15"));
            assertTrue(response.body().contains("\"retentionSnapshotCount\":30"));

            HttpResponse<String> getResponse = context.get("/printers/printer-1/camera/settings");

            assertEquals(200, getResponse.statusCode());
            assertTrue(getResponse.body().contains("\"enabled\":true"));
            assertTrue(getResponse.body().contains("\"sourceType\":\"simulated\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postCameraSnapshotCapturesSimulatedFrameAndGetSnapshotReturnsImage() throws Exception {
        Path cameraStorageDirectory = tempDir.resolve("camera-api-storage");

        TestContext context = createContext("camera-snapshot-simulated.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-2", "Printer 2", "SIM_PORT_2", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-2", "Printer 2", "SIM_PORT_2", "sim", true));

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","storageDirectory":"%s"}
                            """.formatted(cameraStorageDirectory));
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> captureResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/snapshot",
                    null);

            assertEquals(200, captureResponse.statusCode());
            assertTrue(captureResponse.body().contains("\"success\":true"));
            assertTrue(captureResponse.body().contains("\"hasFrame\":true"));
            assertTrue(captureResponse.body().contains("\"contentType\":\"image/jpeg\""));
            assertTrue(captureResponse.body().contains("\"width\":320"));
            assertTrue(captureResponse.body().contains("\"height\":240"));

            assertTrue(Files.exists(cameraStorageDirectory.resolve("printer-1").resolve("latest.jpg")));
            assertTrue(Files.isDirectory(cameraStorageDirectory.resolve("printer-1").resolve("snapshots")));

            HttpResponse<String> snapshotResponse = context.get("/printers/printer-1/camera/snapshot");

            assertEquals(200, snapshotResponse.statusCode());
            assertTrue(snapshotResponse.headers().firstValue("content-type").orElse("").contains("image/jpeg"));
            assertTrue(snapshotResponse.headers().firstValue("cache-control").orElse("").contains("no-store"));
        } finally {
            context.close();
        }
    }
    @Test
    void cameraSnapshotListsAndServesCapturedFiles() throws Exception {
        Path cameraStorageDirectory = tempDir.resolve("camera-snapshot-storage");

        TestContext context = createContext("camera-snapshot.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","storageDirectory":"%s","retentionSnapshotCount":20}
                            """.formatted(cameraStorageDirectory));
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> captureResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/snapshot",
                    null);
            assertEquals(200, captureResponse.statusCode());

            HttpResponse<String> latestSnapshotResponse = context.get("/printers/printer-1/camera/snapshot");

            assertEquals(200, latestSnapshotResponse.statusCode());
            assertTrue(latestSnapshotResponse.headers().firstValue("content-type").orElse("").contains("image/jpeg"));
            assertTrue(latestSnapshotResponse.headers().firstValue("cache-control").orElse("").contains("no-store"));

            HttpResponse<String> jobsResponse = context.get("/admin/camera/snapshot/jobs?printerId=printer-1");
            assertEquals(200, jobsResponse.statusCode());
            assertTrue(jobsResponse.body().contains("\"fileCount\":1"));

            String cameraJobId = extractJsonString(jobsResponse.body(), "jobId");
            assertNotNull(cameraJobId);

            HttpResponse<String> timelineResponse = context.get(
                    "/admin/camera/snapshot/jobs/" + cameraJobId + "/timeline?printerId=printer-1");

            assertEquals(200, timelineResponse.statusCode());
            assertTrue(timelineResponse.body().contains("\"timeline\":["));
            assertTrue(timelineResponse.body().contains("\"type\":\"snapshot\""));
            assertTrue(timelineResponse.body().contains("snapshots/" + cameraJobId + "/"));
            assertTrue(timelineResponse.body().contains("\"snapshotPath\":"));
            assertTrue(timelineResponse.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(timelineResponse.body().contains("\"cameraJobId\":" + cameraJobId));

            Integer snapshotEntryId = extractJsonInteger(timelineResponse.body(), "id");
            assertNotNull(snapshotEntryId);

            HttpResponse<String> fileResponse = context.get("/admin/camera/snapshot/files/" + snapshotEntryId);

            assertEquals(200, fileResponse.statusCode());
            assertTrue(fileResponse.headers().firstValue("content-type").orElse("").contains("image/jpeg"));
            assertTrue(fileResponse.headers().firstValue("cache-control").orElse("").contains("no-store"));
        } finally {
            context.close();
        }
    }

    @Test
    void cameraDeltaSetAdminEndpointsGenerateAndListFrames() throws Exception {
        Path cameraStorageDirectory = tempDir.resolve("camera-delta-storage");

        TestContext context = createContext("camera-delta-admin.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","storageDirectory":"%s","retentionSnapshotCount":20}
                            """.formatted(cameraStorageDirectory));
            assertEquals(200, settingsResponse.statusCode());

            for (int i = 0; i < 3; i++) {
                HttpResponse<String> captureResponse = context.request(
                        "POST",
                        "/printers/printer-1/camera/snapshot",
                        null);
                assertEquals(200, captureResponse.statusCode());
            }

            HttpResponse<String> jobsResponse = context.get("/admin/camera/snapshot/jobs?printerId=printer-1");
            assertEquals(200, jobsResponse.statusCode());
            String cameraJobId = extractJsonString(jobsResponse.body(), "jobId");
            assertNotNull(cameraJobId);

            HttpResponse<String> createDeltaSetResponse = context.request(
                    "POST",
                    "/admin/camera/snapshot/jobs/" + cameraJobId + "/delta-sets?printerId=printer-1",
                    """
                            {"deltaSnapshotStep":1,"methodName":"image-delta","message":"api test"}
                            """);

            assertEquals(201, createDeltaSetResponse.statusCode());
            assertTrue(createDeltaSetResponse.body().contains("\"sourceSnapshotCount\":3"));
            assertTrue(createDeltaSetResponse.body().contains("\"generatedDeltaCount\":2"));
            assertTrue(createDeltaSetResponse.body().contains("\"deltaSnapshotStep\":1"));

            Integer deltaSetId = extractJsonInteger(createDeltaSetResponse.body(), "id");
            assertNotNull(deltaSetId);

            HttpResponse<String> deltaSetsResponse = context.get(
                    "/admin/camera/snapshot/jobs/" + cameraJobId + "/delta-sets?printerId=printer-1");
            assertEquals(200, deltaSetsResponse.statusCode());
            assertTrue(deltaSetsResponse.body().contains("\"deltaSets\":["));
            assertTrue(deltaSetsResponse.body().contains("\"generatedDeltaCount\":2"));

            HttpResponse<String> framesResponse = context.get("/admin/camera/delta-sets/" + deltaSetId + "/frames");
            assertEquals(200, framesResponse.statusCode());
            assertTrue(framesResponse.body().contains("\"frames\":["));
            assertTrue(framesResponse.body().contains("\"fromSnapshotId\":1"));
            assertTrue(framesResponse.body().contains("\"toSnapshotId\":2"));
            assertTrue(framesResponse.body().contains("\"fromSnapshotId\":2"));
            assertTrue(framesResponse.body().contains("\"toSnapshotId\":3"));
            assertTrue(framesResponse.body().contains("deltas/" + cameraJobId + "/" + deltaSetId + "/"));

            HttpResponse<String> firstRunResponse = context.request(
                    "POST",
                    "/admin/camera/delta-sets/" + deltaSetId + "/calculation-runs",
                    """
                            {"methodName":"threshold-v1","confidenceThreshold":0.25,"message":"first run"}
                            """);
            HttpResponse<String> secondRunResponse = context.request(
                    "POST",
                    "/admin/camera/delta-sets/" + deltaSetId + "/calculation-runs",
                    """
                            {"methodName":"threshold-v1","confidenceThreshold":0.75,"message":"second run"}
                            """);

            assertEquals(201, firstRunResponse.statusCode());
            assertEquals(201, secondRunResponse.statusCode());
            assertTrue(firstRunResponse.body().contains("\"resultCount\":2"));
            assertTrue(secondRunResponse.body().contains("\"resultCount\":2"));

            Integer firstRunId = extractJsonInteger(firstRunResponse.body(), "id");
            Integer secondRunId = extractJsonInteger(secondRunResponse.body(), "id");
            assertNotNull(firstRunId);
            assertNotNull(secondRunId);

            HttpResponse<String> runsResponse = context.get(
                    "/admin/camera/delta-sets/" + deltaSetId + "/calculation-runs");
            assertEquals(200, runsResponse.statusCode());
            assertTrue(runsResponse.body().contains("\"calculationRuns\":["));
            assertTrue(runsResponse.body().contains("\"id\":" + firstRunId));
            assertTrue(runsResponse.body().contains("\"id\":" + secondRunId));

            HttpResponse<String> resultsResponse = context.get(
                    "/admin/camera/calculation-runs/" + firstRunId + "/results");
            assertEquals(200, resultsResponse.statusCode());
            assertTrue(resultsResponse.body().contains("\"results\":["));
            assertTrue(resultsResponse.body().contains("\"calculationRunId\":" + firstRunId));
            assertTrue(resultsResponse.body().contains("\"deltaFrameId\":1"));
            assertTrue(resultsResponse.body().contains("\"deltaFrameId\":2"));

            HttpResponse<String> traceResponse = context.get(
                    "/admin/camera/calculation-runs/" + firstRunId + "/trace?printerId=printer-1");
            assertEquals(200, traceResponse.statusCode());
            assertTrue(traceResponse.body().contains("\"trace\":["));
            assertTrue(traceResponse.body().contains("\"cameraJobId\":" + cameraJobId));
            assertTrue(traceResponse.body().contains("\"deltaSetId\":" + deltaSetId));
            assertTrue(traceResponse.body().contains("\"calculationRunId\":" + firstRunId));
            assertTrue(traceResponse.body().contains("\"calculationResultId\":1"));
            assertTrue(traceResponse.body().contains("\"fromSnapshotPath\":"));
            assertTrue(traceResponse.body().contains("\"toSnapshotPath\":"));
            assertTrue(traceResponse.body().contains("\"deltaPath\":"));
            assertTrue(traceResponse.body().contains("000001_snapshot.jpg"));
            assertTrue(traceResponse.body().contains("000002_snapshot.jpg"));
            assertTrue(traceResponse.body().contains("000001_000002_delta.jpg"));
            assertFalse(traceResponse.body().contains("latest.jpg"));
            assertFalse(traceResponse.body().contains("previous.jpg"));
            assertFalse(traceResponse.body().contains("/delta.jpg"));
        } finally {
            context.close();
        }
    }

    @Test
    void cameraSnapshotAdminEndpointsExposeTimelineAndDeleteJobSnapshot() throws Exception {
        Path cameraStorageDirectory = tempDir.resolve("camera-snapshot-admin-storage");

        TestContext context = createContext("camera-snapshot-admin.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","storageDirectory":"%s","retentionSnapshotCount":20}
                            """
                            .formatted(cameraStorageDirectory));
            assertEquals(200, settingsResponse.statusCode());

            assertEquals(200, context.request("POST", "/printers/printer-1/camera/snapshot", null).statusCode());
            assertEquals(200, context.request("POST", "/printers/printer-1/camera/snapshot", null).statusCode());

            HttpResponse<String> jobsResponse = context.get("/admin/camera/snapshot/jobs?printerId=printer-1");
            assertEquals(200, jobsResponse.statusCode());
            assertTrue(jobsResponse.body().contains("\"fileCount\":2"));

            String printer1CameraJobId = extractJsonString(jobsResponse.body(), "jobId");
            assertNotNull(printer1CameraJobId);

            CameraJob printer2CameraJob = new CameraJobStore().save(CameraJob.running(
                    "printer-2",
                    null,
                    null,
                    Instant.parse("2026-05-21T12:00:00Z"),
                    5,
                    20,
                    "simulated",
                    "default",
                    cameraStorageDirectory.resolve("printer-2").resolve("snapshots").resolve("2").toString(),
                    "test"));
            long printer2CameraJobId = printer2CameraJob.requireId();

            Path printer2SnapshotDirectory = cameraStorageDirectory
                    .resolve("printer-2")
                    .resolve("snapshots")
                    .resolve(Long.toString(printer2CameraJobId));
            Files.createDirectories(printer2SnapshotDirectory);

            Path printer2SnapshotPath = printer2SnapshotDirectory.resolve("printer-2-example.jpg");
            Files.write(printer2SnapshotPath, "fake-jpeg".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            new CameraSnapshotEntryStore().save(CameraSnapshotEntry.captured(
                    "printer-2",
                    printer2CameraJobId,
                    null,
                    printer2SnapshotPath.toString(),
                    "image/jpeg",
                    Files.size(printer2SnapshotPath),
                    Instant.parse("2026-05-21T12:00:00Z"),
                    Instant.parse("2026-05-21T12:00:01Z"),
                    "simulated",
                    "test"));

            HttpResponse<String> timelineResponse = context.get(
                    "/admin/camera/snapshot/jobs/" + printer1CameraJobId + "/timeline?printerId=printer-1");
            assertEquals(200, timelineResponse.statusCode());
            assertTrue(timelineResponse.body().contains("\"timeline\":["));
            assertTrue(timelineResponse.body().contains("\"snapshotPath\":"));
            assertTrue(timelineResponse.body().contains("\"printerId\":\"printer-1\""));
            assertFalse(timelineResponse.body().contains("\"printerId\":\"printer-2\""));

            Integer snapshotEntryId = extractJsonInteger(timelineResponse.body(), "id");
            assertNotNull(snapshotEntryId);

            HttpResponse<String> fileResponse = context.get("/admin/camera/snapshot/files/" + snapshotEntryId);
            assertEquals(200, fileResponse.statusCode());
            assertTrue(fileResponse.headers().firstValue("content-type").orElse("").contains("image/jpeg"));

            HttpResponse<String> previewResponse = context.request(
                    "POST",
                    "/admin/camera/snapshot/jobs/" + printer1CameraJobId + "/recalculate-preview",
                    "{}");
            assertEquals(202, previewResponse.statusCode());
            assertTrue(previewResponse.body().contains("camera_recalculate_preview_not_implemented"));

            Path snapshotsDirectory = cameraStorageDirectory
                    .resolve("printer-1")
                    .resolve("snapshots")
                    .resolve(printer1CameraJobId);
            try (var snapshots = Files.list(snapshotsDirectory)) {
                assertEquals(2, snapshots.filter(Files::isRegularFile).count());
            }

            HttpResponse<String> deleteResponse = context.request(
                    "DELETE",
                    "/admin/camera/snapshot/jobs/" + printer1CameraJobId + "?printerId=printer-1",
                    null);
            assertEquals(200, deleteResponse.statusCode());
            assertTrue(deleteResponse.body().contains("\"deletedMetadataRows\":2"));

            HttpResponse<String> jobsAfterDeleteResponse = context
                    .get("/admin/camera/snapshot/jobs?printerId=printer-1");
            assertEquals(200, jobsAfterDeleteResponse.statusCode());
            assertFalse(jobsAfterDeleteResponse.body().contains("\"jobId\":\"" + printer1CameraJobId + "\""));

            HttpResponse<String> secondPrinterJobsResponse = context
                    .get("/admin/camera/snapshot/jobs?printerId=printer-2");
            assertEquals(200, secondPrinterJobsResponse.statusCode());
            assertTrue(secondPrinterJobsResponse.body().contains("\"jobId\":\"" + printer2CameraJobId + "\""));
            assertTrue(secondPrinterJobsResponse.body().contains("\"fileCount\":1"));
        } finally {
            context.close();
        }
    }

    @Test
    void getCameraStatusIncludesLastCaptureAfterSnapshot() throws Exception {
        Path cameraStorageDirectory = tempDir.resolve("camera-status-storage");

        TestContext context = createContext("camera-status-after-capture.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","storageDirectory":"%s"}
                            """.formatted(cameraStorageDirectory));
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> captureResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/snapshot",
                    null);
            assertEquals(200, captureResponse.statusCode());

            HttpResponse<String> statusResponse = context.get("/printers/printer-1/camera/status");

            assertEquals(200, statusResponse.statusCode());
            assertTrue(statusResponse.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(statusResponse.body().contains("\"enabled\":true"));
            assertTrue(statusResponse.body().contains("\"available\":true"));
            assertTrue(statusResponse.body().contains("\"sourceType\":\"simulated\""));
            assertTrue(statusResponse.body().contains("\"sourceDescription\":\"simulated-camera:printer-1\""));
            assertTrue(statusResponse.body().contains("\"lastCaptureAt\":"));
        } finally {
            context.close();
        }
    }

    @Test
    void getCameraEventsReturnsCaptureEvents() throws Exception {
        Path cameraStorageDirectory = tempDir.resolve("camera-events-storage");

        TestContext context = createContext("camera-events.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","storageDirectory":"%s"}
                            """.formatted(cameraStorageDirectory));
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> captureResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/snapshot",
                    null);
            assertEquals(200, captureResponse.statusCode());

            HttpResponse<String> eventsResponse = context.get("/printers/printer-1/camera/events");

            assertEquals(200, eventsResponse.statusCode());
            assertTrue(eventsResponse.body().contains("\"eventType\":\"CAMERA_FRAME_CAPTURED\""));
            assertTrue(eventsResponse.body().contains("\"message\":\"Camera frame captured\""));
        } finally {
            context.close();
        }
    }

    @Test
    void cameraAnalysisSessionEndpointsCreateStopAndListSamples() throws Exception {
        Path cameraStorageDirectory = tempDir.resolve("camera-analysis-storage");

        TestContext context = createContext("camera-analysis-sessions.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/printers/printer-1/camera/settings",
                    """
                            {"enabled":true,"sourceType":"simulated","sourceValue":"default","analysisEnabled":true,"storageDirectory":"%s"}
                            """
                            .formatted(cameraStorageDirectory));
            assertEquals(200, settingsResponse.statusCode());

            HttpResponse<String> startResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/analysis-sessions",
                    null);
            assertEquals(201, startResponse.statusCode());
            assertTrue(startResponse.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(startResponse.body().contains("\"state\":\"RUNNING\""));

            String sessionId = extractJsonString(startResponse.body(), "id");

            HttpResponse<String> samplesResponse = context.get(
                    "/printers/printer-1/camera/analysis-sessions/" + sessionId + "/samples");
            assertEquals(200, samplesResponse.statusCode());
            assertTrue(samplesResponse.body().contains("\"samples\":["));
            assertTrue(samplesResponse.body().contains("\"sessionId\":\"" + sessionId + "\""));
            assertTrue(samplesResponse.body().contains("\"deltaScore\":"));
            assertTrue(samplesResponse.body().contains("\"confidence\":"));
            assertTrue(samplesResponse.body().contains("/snapshots/1/000001_snapshot.jpg"));
            assertFalse(samplesResponse.body().contains("/snapshots/previous.jpg"));
            assertFalse(samplesResponse.body().contains("/snapshots/delta.jpg"));

            HttpResponse<String> secondSampleResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/analysis-sessions/" + sessionId + "/samples",
                    null);
            assertEquals(201, secondSampleResponse.statusCode());
            assertTrue(secondSampleResponse.body().contains("/snapshots/1/000002_snapshot.jpg"));
            assertTrue(secondSampleResponse.body().contains("/snapshots/1/000001_snapshot.jpg"));
            assertTrue(secondSampleResponse.body().contains("/deltas/1/1/000001_000002_delta.jpg"));
            assertFalse(secondSampleResponse.body().contains("/snapshots/previous.jpg"));
            assertFalse(secondSampleResponse.body().contains("/snapshots/delta.jpg"));

            HttpResponse<String> listResponse = context.get("/printers/printer-1/camera/analysis-sessions");
            assertEquals(200, listResponse.statusCode());
            assertTrue(listResponse.body().contains("\"sessions\":["));
            assertTrue(listResponse.body().contains("\"id\":\"" + sessionId + "\""));

            HttpResponse<String> stopResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/analysis-sessions/" + sessionId + "/stop",
                    null);
            assertEquals(200, stopResponse.statusCode());
            assertTrue(stopResponse.body().contains("\"state\":\"COMPLETED\""));
            assertTrue(stopResponse.body().contains("\"stoppedAt\":"));
            assertEquals("COMPLETED", new CameraJobStore().findById(1L).orElseThrow().state().name());

            HttpResponse<String> restartedResponse = context.request(
                    "POST",
                    "/printers/printer-1/camera/analysis-sessions",
                    null);
            assertEquals(201, restartedResponse.statusCode());
            assertTrue(restartedResponse.body().contains("\"state\":\"RUNNING\""));
            assertEquals("RUNNING", new CameraJobStore().findById(2L).orElseThrow().state().name());
        } finally {
            context.close();
        }
    }

    @Test
    void cameraEndpointReturns404ForMissingPrinter() throws Exception {
        TestContext context = createContext("camera-missing-printer.db");

        try {
            HttpResponse<String> response = context.get("/printers/missing/camera/status");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void cameraSnapshotReturns404WhenNoSnapshotExists() throws Exception {
        TestContext context = createContext("camera-snapshot-missing.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.get("/printers/printer-1/camera/snapshot");

            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("camera_snapshot_not_available"));
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterByIdReturnsPrinter() throws Exception {
        TestContext context = createContext("printer-get.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.stateCache.update(
                    "printer-1",
                    PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:00:00Z")));

            HttpResponse<String> response = context.get("/printers/printer-1");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"printer-1\""));
            assertTrue(response.body().contains("\"displayName\":\"Printer 1\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterByIdReturns404WhenMissing() throws Exception {
        TestContext context = createContext("printer-get-404.db");

        try {
            HttpResponse<String> response = context.get("/printers/missing");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterStatusReturnsUnknownWhenNoSnapshotExists() throws Exception {
        TestContext context = createContext("printer-status-unknown.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));

            HttpResponse<String> response = context.get("/printers/printer-1/status");

            assertEquals(200, response.statusCode());
            assertEquals(
                    "{\"state\":\"UNKNOWN\",\"hotendTemperature\":null,\"bedTemperature\":null,"
                            + "\"lastResponse\":null,\"errorMessage\":null,"
                            + "\"serialFailureType\":null,\"updatedAt\":null}",
                    response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterStatusReturnsSnapshot() throws Exception {
        TestContext context = createContext("printer-status.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.stateCache.update(
                    "printer-1",
                    PrinterSnapshot.error(
                            PrinterState.ERROR,
                            55.0,
                            25.0,
                            "Error: heater",
                            "Heater failure",
                            Instant.parse("2026-04-29T10:01:00Z")));

            HttpResponse<String> response = context.get("/printers/printer-1/status");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"state\":\"ERROR\""));
            assertTrue(response.body().contains("\"hotendTemperature\":55.00"));
            assertTrue(response.body().contains("\"bedTemperature\":25.00"));
            assertTrue(response.body().contains("\"errorMessage\":\"Heater failure\""));
        } finally {
            context.close();
        }
    }

    @Test
    void putPrinterUpdatesPrinter() throws Exception {
        TestContext context = createContext("printer-put.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Old Printer", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Old Printer", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "PUT",
                    "/printers/printer-1",
                    """
                            {"displayName":"Updated Printer","portName":"SIM_PORT_2","mode":"sim-error","enabled":false}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"displayName\":\"Updated Printer\""));
            assertTrue(response.body().contains("\"portName\":\"SIM_PORT_2\""));
            assertTrue(response.body().contains("\"mode\":\"sim-error\""));
            assertTrue(response.body().contains("\"enabled\":false"));

            assertEquals("Updated Printer", context.printerRegistry.findById("printer-1").orElseThrow().displayName());
        } finally {
            context.close();
        }
    }

    @Test
    void putPrinterWithoutEnabledPreservesExistingEnabledState() throws Exception {
        TestContext context = createContext("printer-put-preserve-enabled.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Old Printer", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Old Printer", "SIM_PORT", "sim", false));

            HttpResponse<String> response = context.request(
                    "PUT",
                    "/printers/printer-1",
                    """
                            {"displayName":"Updated Printer","portName":"SIM_PORT_2","mode":"sim-error"}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"displayName\":\"Updated Printer\""));
            assertTrue(response.body().contains("\"enabled\":false"));
            assertFalse(context.printerRegistry.findById("printer-1").orElseThrow().enabled());
        } finally {
            context.close();
        }
    }

    @Test
    void putPrinterReturns404WhenMissing() throws Exception {
        TestContext context = createContext("printer-put-404.db");

        try {
            HttpResponse<String> response = context.request(
                    "PUT",
                    "/printers/missing",
                    """
                            {"displayName":"Updated Printer","portName":"SIM_PORT_2","mode":"sim","enabled":true}
                            """);

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void deletePrinterRemovesPrinter() throws Exception {
        TestContext context = createContext("printer-delete.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.stateCache.update(
                    "printer-1",
                    PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:00:00Z")));

            HttpResponse<String> response = context.request("DELETE", "/printers/printer-1", null);

            assertEquals(200, response.statusCode());
            assertEquals("{\"deleted\":\"printer-1\"}", response.body());
            assertTrue(context.printerRegistry.findById("printer-1").isEmpty());
            assertTrue(context.stateCache.findByPrinterId("printer-1").isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    void deletePrinterReturns404WhenMissing() throws Exception {
        TestContext context = createContext("printer-delete-404.db");

        try {
            HttpResponse<String> response = context.request("DELETE", "/printers/missing", null);

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void enablePrinterSetsEnabledTrue() throws Exception {
        TestContext context = createContext("printer-enable.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));

            HttpResponse<String> response = context.request("POST", "/printers/printer-1/enable", null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"enabled\":true"));
            assertTrue(context.printerRegistry.findById("printer-1").orElseThrow().enabled());
        } finally {
            context.close();
        }
    }

    @Test
    void disablePrinterSetsEnabledFalse() throws Exception {
        TestContext context = createContext("printer-disable.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request("POST", "/printers/printer-1/disable", null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"enabled\":false"));
            assertFalse(context.printerRegistry.findById("printer-1").orElseThrow().enabled());
        } finally {
            context.close();
        }
    }

    @Test
    void wrongMethodOnPrinterEnableReturns405() throws Exception {
        TestContext context = createContext("printer-enable-405.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));

            HttpResponse<String> response = context.request("GET", "/printers/printer-1/enable", null);

            assertEquals(405, response.statusCode());
            assertEquals("{\"error\":\"method_not_allowed\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void unknownPrinterEndpointReturns404() throws Exception {
        TestContext context = createContext("printer-endpoint-404.db");

        try {
            HttpResponse<String> response = context.get("/printers/printer-1/unknown");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_endpoint_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardReturnsHtml() throws Exception {
        TestContext context = createContext("dashboard.db");

        try {
            HttpResponse<String> response = context.get("/dashboard");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("<!DOCTYPE html>") || response.body().contains("<html"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardCssReturnsCss() throws Exception {
        TestContext context = createContext("dashboard-css.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/dashboard.css");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/css"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardFaviconReturnsSvg() throws Exception {
        TestContext context = createContext("dashboard-favicon.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/favicon.svg");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("image/svg+xml"));
            assertTrue(response.body().contains("<svg"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardJsReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-js.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/dashboard.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardWrongMethodReturns405() throws Exception {
        TestContext context = createContext("dashboard-405.db");

        try {
            HttpResponse<String> response = context.request("POST", "/dashboard", null);

            assertEquals(405, response.statusCode());
            assertEquals("{\"error\":\"method_not_allowed\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        TestContext context = createContext("printers-malformed-json.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim
                            """);

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("\"error\":"));
        } finally {
            context.close();
        }
    }

    @Test
    void persistenceFailureReturnsControlled500() throws Exception {
        Path invalidDatabasePath = tempDir.resolve("not-a-db-dir");
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(invalidDatabasePath));
        System.setProperty("printerhub.databaseFile", invalidDatabasePath.toString());

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
        PrintFileSettingsStore printFileSettingsStore = new PrintFileSettingsStore();
        PrinterEventStore printerEventStore = new PrinterEventStore();
        SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache);

        int port = findFreePort();

        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);
        PrintJobStore printJobStore = new PrintJobStore();

        PrintJobService printJobService = new PrintJobService(
                printJobStore,
                printerEventStore);

        PrinterActionGuard printerActionGuard = new PrinterActionGuard();
        SdCardService sdCardService = new SdCardService(printerEventStore);

        SdCardUploadService sdCardUploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                printFileService,
                sdCardService,
                printerSdFileService,
                printerEventStore,
                monitoringRulesStore,
                serialTransferSettingsStore);

        PrintJobExecutionService printJobExecutionService = new PrintJobExecutionService(
                printJobService,
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                new PrinterActionMapper(),
                new PrintJobExecutionStepStore());

        AsyncPrintJobExecutor asyncPrintJobExecutor = new AsyncPrintJobExecutor(
                printJobService,
                printerRegistry,
                printerActionGuard,
                printJobExecutionService);

        RemoteApiServer server = new RemoteApiServer(
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore,
                monitoringRulesStore,
                printFileSettingsStore,
                serialTransferSettingsStore,
                printerEventStore,
                new PrinterCommandService(printerEventStore),
                new SdCardService(printerEventStore),
                sdCardUploadService,
                printFileService,
                printerSdFileService,
                printJobService,
                asyncPrintJobExecutor,
                new PrintJobExecutionStepStore());

        server.start();

        TestContext context = new TestContext(
                port,
                server,
                monitoringScheduler,
                printerRegistry,
                stateCache,
                configurationStore,
                printJobService);

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("\"error\":"));
            assertTrue(context.printerRegistry.findById("printer-1").isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    void postJobsCreatesAssignedJob() throws Exception {
        TestContext context = createContext("jobs-post.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Home axes","type":"HOME_AXES","printerId":"printer-1"}
                            """);

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"name\":\"Home axes\""));
            assertTrue(response.body().contains("\"type\":\"HOME_AXES\""));
            assertTrue(response.body().contains("\"state\":\"ASSIGNED\""));
            assertTrue(response.body().contains("\"printerId\":\"printer-1\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintFilesRegistersReadableGcodeFile() throws Exception {
        TestContext context = createContext("print-files-post.db");

        try {
            Path gcode = tempDir.resolve("api-cube.gcode");
            Files.writeString(gcode, "G28\n");

            HttpResponse<String> response = context.request(
                    "POST",
                    "/print-files",
                    "{\"path\":\"" + gcode.toString() + "\"}");

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"originalFilename\":\"api-cube.gcode\""));
            assertTrue(response.body().contains("\"mediaType\":\"text/x.gcode\""));

            String printFileId = extractJsonString(response.body(), "id");
            assertNotNull(printFileId);

            HttpResponse<String> listResponse = context.get("/print-files");

            assertEquals(200, listResponse.statusCode());
            assertTrue(listResponse.body().contains("\"printFiles\":["));
            assertTrue(listResponse.body().contains("\"id\":\"" + printFileId + "\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintFilesRejectsUnsupportedSourceFormat() throws Exception {
        TestContext context = createContext("print-files-post-unsupported.db");

        try {
            Path stl = tempDir.resolve("api-model.stl");
            Files.writeString(stl, "solid model");

            HttpResponse<String> response = context.request(
                    "POST",
                    "/print-files",
                    "{\"path\":\"" + stl.toString() + "\"}");

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"unsupported_print_file_type\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void printFileSettingsCanBeSavedAndUsedForUploads() throws Exception {
        TestContext context = createContext("print-file-settings-upload.db");

        try {
            Path storageDirectory = tempDir.resolve("uploaded-gcode");

            HttpResponse<String> settingsResponse = context.request(
                    "PUT",
                    "/settings/print-files",
                    "{\"storageDirectory\":\"" + storageDirectory.toString() + "\"}");

            assertEquals(200, settingsResponse.statusCode());
            assertTrue(settingsResponse.body().contains("\"storageDirectory\":\"" + storageDirectory.toString()));

            HttpResponse<String> uploadResponse = context.requestBytes(
                    "POST",
                    "/print-files/uploads?filename=dashboard-cube.gcode",
                    "G28\nM105\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertEquals(201, uploadResponse.statusCode());
            assertTrue(uploadResponse.body().contains("\"originalFilename\":\"dashboard-cube.gcode\""));
            assertTrue(uploadResponse.body().contains(storageDirectory.toString().replace("\\", "\\\\")));

            String printFileId = extractJsonString(uploadResponse.body(), "id");
            assertNotNull(printFileId);

            HttpResponse<String> contentResponse = context.get("/print-files/" + printFileId + "/content");

            assertEquals(200, contentResponse.statusCode());
            assertTrue(contentResponse.body().contains("\"content\":\"G28\\nM105\\n\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintFileUploadsRejectsUnsupportedSourceFormat() throws Exception {
        TestContext context = createContext("print-file-upload-unsupported.db");

        try {
            HttpResponse<String> uploadResponse = context.requestBytes(
                    "POST",
                    "/print-files/uploads?filename=model.stl",
                    "solid model".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertEquals(400, uploadResponse.statusCode());
            assertEquals("{\"error\":\"unsupported_print_file_type\"}", uploadResponse.body());
        } finally {
            context.close();
        }
    }

    @Test
    void postJobsCreatesFileBackedPrintJob() throws Exception {
        TestContext context = createContext("jobs-post-print-file.db");

        try {
            Path gcode = tempDir.resolve("benchy.gcode");
            Files.writeString(gcode, "G28\n");

            HttpResponse<String> fileResponse = context.request(
                    "POST",
                    "/print-files",
                    "{\"path\":\"" + gcode.toString() + "\"}");
            assertEquals(201, fileResponse.statusCode());

            String printFileId = extractJsonString(fileResponse.body(), "id");
            assertNotNull(printFileId);

            String printerSdFileId = registerPrinterSdFile(context, "printer-1", "BENCHY.GCO", "benchy.gcode",
                    printFileId);

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs",
                    "{\"name\":\"Print benchy\",\"type\":\"PRINT_FILE\",\"printerId\":\"printer-1\",\"printerSdFileId\":\""
                            + printerSdFileId
                            + "\"}");

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"name\":\"Print benchy\""));
            assertTrue(response.body().contains("\"type\":\"PRINT_FILE\""));
            assertTrue(response.body().contains("\"printFileId\":\"" + printFileId + "\""));
            assertTrue(response.body().contains("\"printerSdFileId\":\"" + printerSdFileId + "\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobsDerivesNameForFileBackedPrintJob() throws Exception {
        TestContext context = createContext("jobs-post-print-file-derived-name.db");

        try {
            Path gcode = tempDir.resolve("cube.gcode");
            Files.writeString(gcode, "G28\n");

            HttpResponse<String> fileResponse = context.request(
                    "POST",
                    "/print-files",
                    "{\"path\":\"" + gcode.toString() + "\"}");
            assertEquals(201, fileResponse.statusCode());

            String printFileId = extractJsonString(fileResponse.body(), "id");
            assertNotNull(printFileId);

            String printerSdFileId = registerPrinterSdFile(context, "printer-1", "CUBE.GCO", "cube.gcode", printFileId);

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs",
                    "{\"type\":\"PRINT_FILE\",\"printerId\":\"printer-1\",\"printerSdFileId\":\""
                            + printerSdFileId
                            + "\"}");

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"name\":\"Print cube.gcode\""));
            assertTrue(response.body().contains("\"type\":\"PRINT_FILE\""));
            assertTrue(response.body().contains("\"printFileId\":\"" + printFileId + "\""));
            assertTrue(response.body().contains("\"printerSdFileId\":\"" + printerSdFileId + "\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobsRejectsDisabledPrinterSdFileTarget() throws Exception {
        TestContext context = createContext("jobs-post-disabled-printer-sd-file.db");

        try {
            String printerSdFileId = registerPrinterSdFile(context, "printer-1", "OLD.GCO", "old.gcode", null);

            HttpResponse<String> disableResponse = context.request(
                    "POST",
                    "/printer-sd-files/" + printerSdFileId + "/disable",
                    "");
            assertEquals(200, disableResponse.statusCode());
            assertTrue(disableResponse.body().contains("\"enabled\":false"));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs",
                    "{\"type\":\"PRINT_FILE\",\"printerId\":\"printer-1\",\"printerSdFileId\":\""
                            + printerSdFileId
                            + "\"}");

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"printer_sd_file_disabled\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void deletePrinterSdFileMarksItDeleted() throws Exception {
        TestContext context = createContext("printer-sd-file-delete.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            String printerSdFileId = registerPrinterSdFile(context, "printer-1", "TEST4.GCO", "test4.gco", null);

            HttpResponse<String> response = context.request(
                    "DELETE",
                    "/printer-sd-files/" + printerSdFileId,
                    "");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"deleted\":true"));
            assertTrue(response.body().contains("\"enabled\":false"));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobsRejectsDeletedPrinterSdFileTarget() throws Exception {
        TestContext context = createContext("jobs-post-deleted-printer-sd-file.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            String printerSdFileId = registerPrinterSdFile(context, "printer-1", "OLD.GCO", "old.gcode", null);
            HttpResponse<String> deleteResponse = context.request(
                    "DELETE",
                    "/printer-sd-files/" + printerSdFileId,
                    "");
            assertEquals(200, deleteResponse.statusCode());

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs",
                    "{\"type\":\"PRINT_FILE\",\"printerId\":\"printer-1\",\"printerSdFileId\":\""
                            + printerSdFileId
                            + "\"}");

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"printer_sd_file_deleted\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getJobsReturnsCreatedJobs() throws Exception {
        TestContext context = createContext("jobs-get.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read firmware","type":"READ_FIRMWARE_INFO","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());

            HttpResponse<String> response = context.get("/jobs");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"jobs\":["));
            assertTrue(response.body().contains("\"name\":\"Read firmware\""));
            assertTrue(response.body().contains("\"type\":\"READ_FIRMWARE_INFO\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getJobByIdReturnsJob() throws Exception {
        TestContext context = createContext("job-get.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read temperature","type":"READ_TEMPERATURE","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            HttpResponse<String> response = context.get("/jobs/" + jobId);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"" + jobId + "\""));
            assertTrue(response.body().contains("\"name\":\"Read temperature\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobStartStartsAssignedJobAsynchronously() throws Exception {
        TestContext context = createContext("job-start.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read firmware","type":"READ_FIRMWARE_INFO","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs/" + jobId + "/start",
                    null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"success\":true"));
            assertTrue(response.body().contains("\"accepted\":true"));
            assertTrue(response.body().contains("\"outcome\":\"QUEUED\""));
            assertTrue(response.body().contains("\"state\":\"RUNNING\""));

            String completedJob = waitForJobState(context, jobId, "COMPLETED");
            assertTrue(completedJob.contains("\"state\":\"COMPLETED\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobCancelCancelsJob() throws Exception {
        TestContext context = createContext("job-cancel.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Fan off","type":"TURN_FAN_OFF","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs/" + jobId + "/cancel",
                    null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"" + jobId + "\""));
            assertTrue(response.body().contains("\"state\":\"CANCELLED\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobCancelRejectsCompletedJob() throws Exception {
        TestContext context = createContext("job-cancel-completed.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read firmware","type":"READ_FIRMWARE_INFO","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);
            context.printJobService.markCompleted(jobId);

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs/" + jobId + "/cancel",
                    null);

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains(OperationMessages.INVALID_JOB_STATE));
            assertEquals(JobState.COMPLETED, context.printJobService.findById(jobId).orElseThrow().state());
        } finally {
            context.close();
        }
    }

    @Test
    void postJobCancelSendsAbortForRunningPrintFileJob() throws Exception {
        TestContext context = createContext("job-cancel-running-print-file.db");

        try {
            String printerId = "printer-1";
            RecordingPrinterPort printerPort = new RecordingPrinterPort();
            context.printerRegistry.register(new PrinterRuntimeNode(
                    printerId,
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    printerPort,
                    true));

            String printerSdFileId = registerPrinterSdFile(context, printerId, "TEST.GCO", "test.gcode", null);
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    "{\"name\":\"Print test\",\"type\":\"PRINT_FILE\",\"printerId\":\""
                            + printerId
                            + "\",\"printerSdFileId\":\""
                            + printerSdFileId
                            + "\"}");

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);
            context.printJobService.markRunning(jobId);

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs/" + jobId + "/cancel",
                    null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"" + jobId + "\""));
            assertTrue(response.body().contains("\"state\":\"CANCELLED\""));
            assertTrue(printerPort.commands().contains("M524"));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobPauseAndResumeControlRunningPrintFileJob() throws Exception {
        TestContext context = createContext("job-pause-resume-running-print-file.db");

        try {
            String printerId = "printer-1";
            RecordingPrinterPort printerPort = new RecordingPrinterPort();
            context.printerRegistry.register(new PrinterRuntimeNode(
                    printerId,
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    printerPort,
                    true));

            String printerSdFileId = registerPrinterSdFile(context, printerId, "TEST.GCO", "test.gcode", null);
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    "{\"name\":\"Print test\",\"type\":\"PRINT_FILE\",\"printerId\":\""
                            + printerId
                            + "\",\"printerSdFileId\":\""
                            + printerSdFileId
                            + "\"}");

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);
            context.printJobService.markRunning(jobId);

            HttpResponse<String> pauseResponse = context.request(
                    "POST",
                    "/jobs/" + jobId + "/pause",
                    null);

            assertEquals(200, pauseResponse.statusCode());
            assertTrue(pauseResponse.body().contains("\"state\":\"PAUSED\""));
            assertTrue(pauseResponse.body().contains("\"wireCommand\":\"M25\""));

            HttpResponse<String> resumeResponse = context.request(
                    "POST",
                    "/jobs/" + jobId + "/resume",
                    null);

            assertEquals(200, resumeResponse.statusCode());
            assertTrue(resumeResponse.body().contains("\"state\":\"RUNNING\""));
            assertTrue(resumeResponse.body().contains("\"wireCommand\":\"M24\""));
            assertTrue(printerPort.commands().contains("M25"));
            assertTrue(printerPort.commands().contains("M24"));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobRestartCreatesNewPrintFileJobFromTerminalJob() throws Exception {
        TestContext context = createContext("job-restart-print-file.db");

        try {
            String printerId = "printer-1";
            String printerSdFileId = registerPrinterSdFile(context, printerId, "TEST.GCO", "test.gcode", null);
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    "{\"name\":\"Print test\",\"type\":\"PRINT_FILE\",\"printerId\":\""
                            + printerId
                            + "\",\"printerSdFileId\":\""
                            + printerSdFileId
                            + "\"}");

            assertEquals(201, createResponse.statusCode());
            String sourceJobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(sourceJobId);
            context.printJobService.markCompleted(sourceJobId);

            HttpResponse<String> restartResponse = context.request(
                    "POST",
                    "/jobs/" + sourceJobId + "/restart",
                    null);

            assertEquals(201, restartResponse.statusCode());
            assertTrue(restartResponse.body().contains("\"sourceJobId\":\"" + sourceJobId + "\""));
            assertTrue(restartResponse.body().contains("\"state\":\"ASSIGNED\""));
            assertTrue(restartResponse.body().contains("\"printerSdFileId\":\"" + printerSdFileId + "\""));
            assertEquals(JobState.COMPLETED, context.printJobService.findById(sourceJobId).orElseThrow().state());
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterSdUploadRecoveryStartsAtLine2AndReturnsActualCloseResult() throws Exception {
        TestContext context = createContext("sd-upload-recovery-close.db");

        try {
            String printerId = "printer-1";
            RecordingPrinterPort printerPort = new RecordingPrinterPort();
            context.printerRegistry.register(new PrinterRuntimeNode(
                    printerId,
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    printerPort,
                    true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/" + printerId + "/sd-card/recovery/close-upload",
                    "");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"printerId\":\"" + printerId + "\""));
            assertTrue(response.body().contains("\"lineNumber\":2"));
            assertTrue(response.body().contains("\"attempts\":1"));
            assertTrue(response.body().contains("\"success\":true"));
            assertTrue(printerPort.commands().stream().anyMatch(command -> command.startsWith("N2 M29*")));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterSdUploadRecoveryRetriesWithRequestedResendLine() throws Exception {
        TestContext context = createContext("sd-upload-recovery-resend.db");

        try {
            String printerId = "printer-1";
            RecordingPrinterPort printerPort = new RecordingPrinterPort()
                    .queueResponse("Error:No Checksum with line number, Last Line: 3182\nResend: 3183")
                    .queueResponse("ok");

            context.printerRegistry.register(new PrinterRuntimeNode(
                    printerId,
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    printerPort,
                    true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/" + printerId + "/sd-card/recovery/close-upload",
                    "");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"lineNumber\":3183"));
            assertTrue(response.body().contains("\"attempts\":2"));
            assertTrue(response.body().contains("\"success\":true"));
            assertTrue(printerPort.commands().stream().anyMatch(command -> command.startsWith("N2 M29*")));
            assertTrue(printerPort.commands().stream().anyMatch(command -> command.startsWith("N3183 M29*")));
        } finally {
            context.close();
        }
    }

    @Test
    void deleteJobRemovesJob() throws Exception {
        TestContext context = createContext("job-delete.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read position","type":"READ_POSITION","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            HttpResponse<String> deleteResponse = context.request(
                    "DELETE",
                    "/jobs/" + jobId,
                    null);

            assertEquals(200, deleteResponse.statusCode());
            assertEquals("{\"deleted\":\"" + jobId + "\"}", deleteResponse.body());

            HttpResponse<String> getResponse = context.get("/jobs/" + jobId);
            assertEquals(404, getResponse.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", getResponse.body());
        } finally {
            context.close();
        }
    }

    @Test
    void deleteJobReturns404WhenMissing() throws Exception {
        TestContext context = createContext("job-delete-404.db");

        try {
            HttpResponse<String> response = context.request(
                    "DELETE",
                    "/jobs/missing-job",
                    null);

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getMissingJobReturns404() throws Exception {
        TestContext context = createContext("job-get-404.db");

        try {
            HttpResponse<String> response = context.get("/jobs/missing-job");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void writesOperatorMessageReportScenario() throws Exception {
        OperatorMessageReportWriter.appendScenario(
                "remote api verification summary",
                "Remote API unit verification covered health, printer CRUD, monitoring settings, command execution, event reads, dashboard access, and controlled error handling.",
                "[PrinterHub] API server started on port ...\n"
                        + "[PrinterHub] API server stopped\n"
                        + "[PrinterHub] API operation failed: Failed to save printer configuration",
                "GET /health -> 200\n"
                        + "GET /printers -> 200\n"
                        + "GET /settings/monitoring -> 200\n"
                        + "PUT /settings/monitoring -> 200\n"
                        + "POST /printers -> 201\n"
                        + "POST /printers/{id}/commands -> 200\n"
                        + "GET /printers/{id}/events -> 200\n"
                        + "PUT /printers/{id} -> 200\n"
                        + "DELETE /printers/{id} -> 200\n"
                        + "DELETE /jobs/{id} -> 200\n"
                        + "invalid POST -> 400\n"
                        + "unknown printer -> 404\n"
                        + "wrong method -> 405\n"
                        + "persistence failure -> 500",
                "Printer configuration persistence was exercised through create/update/delete flows.\n"
                        + "Monitoring settings persistence was exercised through GET/PUT flows.\n"
                        + "Manual command execution and event retrieval were exercised through dedicated endpoints.\n"
                        + "Controlled persistence failure path was also verified.");

        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("target", "operator-message-report.md")));
    }

    @Test
    void getJobExecutionStepsReturnsStructuredExecutionDiagnostics() throws Exception {
        TestContext context = createContext("job-execution-steps-get.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Home axes","type":"HOME_AXES","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> startResponse = context.request(
                    "POST",
                    "/jobs/" + jobId + "/start",
                    null);

            assertEquals(200, startResponse.statusCode());
            waitForJobState(context, jobId, "COMPLETED");

            HttpResponse<String> response = context.get("/jobs/" + jobId + "/execution-steps");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"executionSteps\":["));
            assertTrue(response.body().contains("\"jobId\":\"" + jobId + "\""));
            assertTrue(response.body().contains("\"stepIndex\":0"));
            assertTrue(response.body().contains("\"stepName\":\"validate-position-before-home\""));
            assertTrue(response.body().contains("\"wireCommand\":\"M114\""));
            assertTrue(response.body().contains("\"outcome\":\"SUCCESS\""));
            assertTrue(response.body().contains("\"success\":true"));
            assertTrue(response.body().contains("\"stepIndex\":1"));
            assertTrue(response.body().contains("\"stepName\":\"home-axes\""));
            assertTrue(response.body().contains("\"wireCommand\":\"G28\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getJobExecutionStepsReturns404WhenJobIsMissing() throws Exception {
        TestContext context = createContext("job-execution-steps-404.db");

        try {
            HttpResponse<String> response = context.get("/jobs/missing-job/execution-steps");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    private TestContext createContext(String dbName) throws Exception {
        Path dbFile = tempDir.resolve(dbName);
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
        if (!dbName.startsWith("dangerous-confirmation-") && !"security-settings-get.db".equals(dbName)) {
            new SecuritySettingsStore().save(new SecuritySettings(false, LocalRole.ADMIN, false));
        }

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);
        PrintJobStore printJobStore = new PrintJobStore();

        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache);

        PrintJobService printJobService = new PrintJobService(
                printJobStore,
                printerEventStore);

        PrinterActionGuard printerActionGuard = new PrinterActionGuard();
        SdCardService sdCardService = new SdCardService(printerEventStore);
        SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();

        SdCardUploadService sdCardUploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                printFileService,
                sdCardService,
                printerSdFileService,
                printerEventStore, monitoringRulesStore, serialTransferSettingsStore);

        PrintJobExecutionService printJobExecutionService = new PrintJobExecutionService(
                printJobService,
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                new PrinterActionMapper(),
                new PrintJobExecutionStepStore());

        AsyncPrintJobExecutor asyncPrintJobExecutor = new AsyncPrintJobExecutor(
                printJobService,
                printerRegistry,
                printerActionGuard,
                printJobExecutionService);

        int port = findFreePort();

        RemoteApiServer server = new RemoteApiServer(
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore,
                monitoringRulesStore,
                new PrintFileSettingsStore(),
                serialTransferSettingsStore,
                printerEventStore,
                new PrinterCommandService(printerEventStore),
                new SdCardService(printerEventStore),
                sdCardUploadService,
                printFileService,
                printerSdFileService,
                printJobService,
                asyncPrintJobExecutor,
                new PrintJobExecutionStepStore());
        server.start();

        return new TestContext(
                port,
                server,
                monitoringScheduler,
                printerRegistry,
                stateCache,
                configurationStore,
                printJobService);
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String extractJsonString(String body, String fieldName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    private Integer extractJsonInteger(String body, String fieldName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return Integer.parseInt(matcher.group(1));
    }

    private String registerPrinterSdFile(
            TestContext context,
            String printerId,
            String firmwarePath,
            String displayName,
            String printFileId) throws Exception {
        String printFileJson = printFileId == null ? "" : ",\"printFileId\":\"" + printFileId + "\"";
        HttpResponse<String> response = context.request(
                "POST",
                "/printer-sd-files",
                "{\"printerId\":\"" + printerId
                        + "\",\"firmwarePath\":\"" + firmwarePath
                        + "\",\"displayName\":\"" + displayName
                        + "\""
                        + printFileJson
                        + "}");

        assertEquals(201, response.statusCode());
        String printerSdFileId = extractJsonString(response.body(), "id");
        assertNotNull(printerSdFileId);
        return printerSdFileId;
    }

    private String waitForJobState(TestContext context, String jobId, String expectedState) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        String body = "";

        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = context.get("/jobs/" + jobId);
            body = response.body();

            if (body.contains("\"state\":\"" + expectedState + "\"")) {
                return body;
            }

            Thread.sleep(50L);
        }

        fail("Timed out waiting for job " + jobId + " to reach state " + expectedState + ". Last body: " + body);
        return body;
    }

    private RemoteApiServer createApiServerForConstructorTest(int port) {
        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache);
        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintJobStore printJobStore = new PrintJobStore();

        PrintJobService printJobService = new PrintJobService(
                printJobStore,
                printerEventStore);
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);

        PrinterActionGuard printerActionGuard = new PrinterActionGuard();
        SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();
        SdCardService sdCardService = new SdCardService(printerEventStore);

        SdCardUploadService sdCardUploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                printFileService,
                sdCardService,
                printerSdFileService,
                printerEventStore, monitoringRulesStore, serialTransferSettingsStore);

        PrintJobExecutionService printJobExecutionService = new PrintJobExecutionService(
                printJobService,
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                new PrinterActionMapper(),
                new PrintJobExecutionStepStore());

        AsyncPrintJobExecutor asyncPrintJobExecutor = new AsyncPrintJobExecutor(
                printJobService,
                printerRegistry,
                printerActionGuard,
                printJobExecutionService);

        return new RemoteApiServer(
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore,
                monitoringRulesStore,
                new PrintFileSettingsStore(),
                serialTransferSettingsStore,
                printerEventStore,
                new PrinterCommandService(printerEventStore),
                new SdCardService(printerEventStore),
                sdCardUploadService,
                printFileService,
                printerSdFileService,
                printJobService,
                asyncPrintJobExecutor,
                new PrintJobExecutionStepStore());
    }

    private static final class TestContext {
        private final int port;
        private final RemoteApiServer server;
        private final PrinterMonitoringScheduler monitoringScheduler;
        private final PrinterRegistry printerRegistry;
        private final PrinterRuntimeStateCache stateCache;
        private final PrinterConfigurationStore configurationStore;
        private final PrintJobService printJobService;
        private final HttpClient httpClient = HttpClient.newHttpClient();

        private TestContext(
                int port,
                RemoteApiServer server,
                PrinterMonitoringScheduler monitoringScheduler,
                PrinterRegistry printerRegistry,
                PrinterRuntimeStateCache stateCache,
                PrinterConfigurationStore configurationStore,
                PrintJobService printJobService) {
            this.port = port;
            this.server = server;
            this.monitoringScheduler = monitoringScheduler;
            this.printerRegistry = printerRegistry;
            this.stateCache = stateCache;
            this.configurationStore = configurationStore;
            this.printJobService = printJobService;
        }

        private HttpResponse<String> get(String path) throws Exception {
            return request("GET", path, null);
        }

        private HttpResponse<String> request(String method, String path, String body) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path));

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json");
            }

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> requestAsRole(String method, String path, String body, String role)
                throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .header("X-PrinterHub-Role", role);

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json");
            }

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> requestBytes(String method, String path, byte[] body) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                    .header("Content-Type", "application/octet-stream");

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private void close() {
            try {
                monitoringScheduler.stop();
            } finally {
                server.stop();
            }
        }
    }

    private static final class RecordingPrinterPort implements PrinterPort {
        private final List<String> commands = new ArrayList<>();
        private final List<String> queuedRawResponses = new ArrayList<>();
        private boolean connected;

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            commands.add(command);
            if ("M27".equals(command)) {
                return "Not SD printing";
            }
            return "ok";
        }

        @Override
        public String sendRawLine(String line) {
            return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
        }

        @Override
        public String sendRawLine(String line, SerialIOMode mode) {
            ensureConnected();

            if (line == null) {
                throw new IllegalArgumentException("line must not be null");
            }
            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }

            commands.add(line);

            if (!queuedRawResponses.isEmpty()) {
                return queuedRawResponses.remove(0);
            }

            return "ok";
        }

        @Override
        public void writeRawLine(String line, SerialIOMode mode) {
            ensureConnected();

            if (line == null) {
                throw new IllegalArgumentException("line must not be null");
            }
            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }

            commands.add(line);
        }

        @Override
        public String readRawResponse(SerialIOMode mode) {
            ensureConnected();

            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }

            if (!queuedRawResponses.isEmpty()) {
                return queuedRawResponses.remove(0);
            }

            return "ok";
        }

        @Override
        public List<String> sendRawLinesPipelined(List<String> lines, SerialIOMode mode) {
            ensureConnected();

            if (lines == null || lines.isEmpty()) {
                return List.of();
            }
            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }

            List<String> responses = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (line == null) {
                    throw new IllegalArgumentException("line must not be null");
                }
                commands.add(line);

                if (!queuedRawResponses.isEmpty()) {
                    responses.add(queuedRawResponses.remove(0));
                } else {
                    responses.add("ok");
                }
            }

            return responses;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
            ensureConnected();
            queuedRawResponses.clear();
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        private RecordingPrinterPort queueResponse(String response) {
            queuedRawResponses.add(response);
            return this;
        }

        private RecordingPrinterPort queueResponses(String... responses) {
            if (responses == null) {
                return this;
            }
            for (String response : responses) {
                queuedRawResponses.add(response);
            }
            return this;
        }

        private List<String> commands() {
            return commands;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }
    }
}
