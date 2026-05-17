package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.security.LocalRole;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecuritySettingsStoreTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void loadReturnsDefaultsWhenNoSettingsSaved() {
        useDatabase("security-settings-defaults.db");

        SecuritySettings settings = new SecuritySettingsStore().load();

        assertFalse(settings.securityEnabled());
        assertEquals(LocalRole.ADMIN, settings.defaultRole());
        assertTrue(settings.requireDangerousActionConfirmation());
    }

    @Test
    void savePersistsSecuritySettings() {
        useDatabase("security-settings-save.db");

        SecuritySettingsStore store = new SecuritySettingsStore();
        store.save(new SecuritySettings(true, LocalRole.OPERATOR, false));

        SecuritySettings settings = store.load();
        assertTrue(settings.securityEnabled());
        assertEquals(LocalRole.OPERATOR, settings.defaultRole());
        assertFalse(settings.requireDangerousActionConfirmation());
    }

    private void useDatabase(String fileName) {
        System.setProperty("printerhub.databaseFile", tempDir.resolve(fileName).toString());
        new DatabaseInitializer().initialize();
    }
}
