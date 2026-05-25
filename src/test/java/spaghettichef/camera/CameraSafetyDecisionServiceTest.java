package spaghettichef.camera;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.OperationMessages;
import spaghettichef.job.AutonomousPrintControlService;
import spaghettichef.job.PrintJobService;
import spaghettichef.monitoring.PrinterMonitoringScheduler;
import spaghettichef.persistence.CameraAnalysisSample;
import spaghettichef.persistence.CameraAnalysisSampleStore;
import spaghettichef.persistence.CameraEvent;
import spaghettichef.persistence.CameraEventStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.DatabaseInitializer;
import spaghettichef.persistence.PrintJobExecutionStepStore;
import spaghettichef.persistence.PrintJobStore;
import spaghettichef.persistence.PrinterEventStore;
import spaghettichef.runtime.PrinterRegistry;
import spaghettichef.runtime.PrinterRuntimeStateCache;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraSafetyDecisionServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void evaluateDoesNotConfirmWhenSafetyIsDisabledByDefault() {
        useDatabase("camera-safety-disabled.db");

        CameraAnalysisSampleStore sampleStore = new CameraAnalysisSampleStore();
        CameraEventStore eventStore = new CameraEventStore();
        CameraSafetyDecisionService service = createService(sampleStore, eventStore);
        CameraAnalysisSample sample = saveSample(sampleStore, "session-1", 0.95, true);

        service.evaluate(sample);

        List<CameraEvent> events = eventStore.findRecentByPrinterId("printer-1", 10);
        assertEquals(1, events.size());
        assertEquals(OperationMessages.EVENT_SPAGHETTI_SUSPECTED, events.get(0).eventType());
    }

    @Test
    void evaluateConfirmsAfterRequiredHighConfidenceSamplesAndSkipsPauseWhenNoActiveJobExists() {
        useDatabase("camera-safety-confirmed.db");

        CameraSettingsStore settingsStore = new CameraSettingsStore();
        settingsStore.save(new CameraSettings(
                "printer-1",
                true,
                CameraSourceType.SIMULATED,
                "default",
                10,
                20,
                true,
                true,
                true,
                0.80,
                2,
                Instant.parse("2026-05-19T08:00:00Z")));

        CameraAnalysisSampleStore sampleStore = new CameraAnalysisSampleStore();
        CameraEventStore eventStore = new CameraEventStore();
        CameraSafetyDecisionService service = createService(sampleStore, eventStore);

        service.evaluate(saveSample(sampleStore, "session-1", 0.90, true));
        service.evaluate(saveSample(sampleStore, "session-1", 0.92, true));

        List<CameraEvent> events = eventStore.findRecentByPrinterId("printer-1", 10);
        assertTrue(events.stream().anyMatch(event ->
                OperationMessages.EVENT_SPAGHETTI_CONFIRMED.equals(event.eventType())));
        assertTrue(events.stream().anyMatch(event ->
                OperationMessages.EVENT_CAMERA_SAFETY_ACTION_SKIPPED.equals(event.eventType())));
    }

    private CameraSafetyDecisionService createService(
            CameraAnalysisSampleStore sampleStore,
            CameraEventStore eventStore) {
        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrintJobService printJobService = new PrintJobService(
                new PrintJobStore(),
                new PrinterEventStore());
        AutonomousPrintControlService controlService = new AutonomousPrintControlService(
                printJobService,
                printerRegistry,
                new PrinterMonitoringScheduler(printerRegistry, new PrinterRuntimeStateCache()),
                new PrintJobExecutionStepStore());

        return new CameraSafetyDecisionService(
                new CameraSettingsService(new CameraSettingsStore()),
                sampleStore,
                eventStore,
                printJobService,
                controlService);
    }

    private CameraAnalysisSample saveSample(
            CameraAnalysisSampleStore sampleStore,
            String sessionId,
            double confidence,
            boolean suspected) {
        return sampleStore.save(new CameraAnalysisSample(
                null,
                sessionId,
                "printer-1",
                Instant.parse("2026-05-19T08:00:00Z").plusMillis((long) (confidence * 1000)),
                Instant.parse("2026-05-19T08:00:01Z").plusMillis((long) (confidence * 1000)),
                "/tmp/latest.jpg",
                "/tmp/previous.jpg",
                "/tmp/delta.jpg",
                0.91,
                0.82,
                0.73,
                confidence,
                suspected,
                "POSSIBLE_SPAGHETTI_PATTERN",
                "Possible spaghetti failure detected"));
    }

    private void useDatabase(String filename) {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve(filename).toString());
        new DatabaseInitializer().initialize();
    }
}
