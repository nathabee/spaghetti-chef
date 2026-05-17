package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.security.LocalRole;
import printerhub.security.Permission;
import printerhub.security.RoleProfile;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleProfileStoreTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void databaseInitializationSeedsBuiltInProfiles() {
        useDatabase("role-profiles-built-in.db");

        Map<LocalRole, RoleProfile> profiles = new RoleProfileStore().loadAll();

        assertEquals(Set.of(LocalRole.VIEWER, LocalRole.OPERATOR, LocalRole.ADMIN), profiles.keySet());
        assertTrue(profiles.get(LocalRole.VIEWER).allows(Permission.VIEW_DASHBOARD));
        assertFalse(profiles.get(LocalRole.VIEWER).allows(Permission.CONTROL_JOBS));
        assertTrue(profiles.get(LocalRole.ADMIN).allows(Permission.MANAGE_SECURITY));
    }

    @Test
    void saveUpdatesPermissionsForRole() {
        useDatabase("role-profiles-save.db");

        RoleProfileStore store = new RoleProfileStore();
        store.save(new RoleProfile(
                LocalRole.OPERATOR,
                "Operator",
                Set.of(Permission.VIEW_DASHBOARD, Permission.VIEW_JOBS),
                true));

        RoleProfile operator = store.loadAll().get(LocalRole.OPERATOR);
        assertTrue(operator.allows(Permission.VIEW_DASHBOARD));
        assertTrue(operator.allows(Permission.VIEW_JOBS));
        assertFalse(operator.allows(Permission.CONTROL_JOBS));
    }

    private void useDatabase(String fileName) {
        System.setProperty("printerhub.databaseFile", tempDir.resolve(fileName).toString());
        new DatabaseInitializer().initialize();
    }
}
