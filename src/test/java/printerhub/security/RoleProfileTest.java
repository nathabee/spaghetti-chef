package printerhub.security;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleProfileTest {
    @Test
    void builtInProfilesExposeExpectedRoles() {
        Map<LocalRole, RoleProfile> profiles = RoleProfile.builtInProfiles();

        assertEquals(Set.of(LocalRole.VIEWER, LocalRole.OPERATOR, LocalRole.ADMIN), profiles.keySet());
        assertTrue(profiles.get(LocalRole.VIEWER).builtIn());
        assertEquals("Viewer", profiles.get(LocalRole.VIEWER).displayName());
    }

    @Test
    void viewerIsReadOnly() {
        RoleProfile viewer = RoleProfile.builtIn(LocalRole.VIEWER);

        assertTrue(viewer.allows(Permission.VIEW_DASHBOARD));
        assertTrue(viewer.allows(Permission.VIEW_MONITORING));
        assertTrue(viewer.allows(Permission.VIEW_JOBS));
        assertFalse(viewer.allows(Permission.CONTROL_JOBS));
        assertFalse(viewer.allows(Permission.CONFIGURE_PRINTERS));
        assertFalse(viewer.allows(Permission.EXECUTE_SAFE_COMMANDS));
    }

    @Test
    void operatorCanRunNormalOperationsWithoutAdminRights() {
        RoleProfile operator = RoleProfile.builtIn(LocalRole.OPERATOR);

        assertTrue(operator.allows(Permission.CONTROL_JOBS));
        assertTrue(operator.allows(Permission.MANAGE_PRINT_FILES));
        assertTrue(operator.allows(Permission.MANAGE_SD_CARD_FILES));
        assertTrue(operator.allows(Permission.UPLOAD_TO_SD_CARD));
        assertTrue(operator.allows(Permission.EXECUTE_SAFE_COMMANDS));
        assertFalse(operator.allows(Permission.CONFIGURE_PRINTERS));
        assertFalse(operator.allows(Permission.CONFIGURE_MONITORING));
        assertFalse(operator.allows(Permission.EXECUTE_DANGEROUS_COMMANDS));
        assertFalse(operator.allows(Permission.MANAGE_SECURITY));
        assertFalse(operator.allows(Permission.CAMERA_DATA_MANAGE));
    }

    @Test
    void adminHasConfigurationAndSecurityRights() {
        RoleProfile admin = RoleProfile.builtIn(LocalRole.ADMIN);

        assertTrue(admin.allows(Permission.CONFIGURE_PRINTERS));
        assertTrue(admin.allows(Permission.CONFIGURE_MONITORING));
        assertTrue(admin.allows(Permission.CONFIGURE_TRANSFER_SETTINGS));
        assertTrue(admin.allows(Permission.EXECUTE_DANGEROUS_COMMANDS));
        assertTrue(admin.allows(Permission.MANAGE_SECURITY));
        assertTrue(admin.allows(Permission.CAMERA_DATA_MANAGE));
    }

    @Test
    void permissionsAreDefensivelyCopied() {
        Set<Permission> permissions = java.util.EnumSet.of(Permission.VIEW_DASHBOARD);
        RoleProfile profile = new RoleProfile(LocalRole.VIEWER, "Custom Viewer", permissions, false);

        permissions.add(Permission.MANAGE_SECURITY);

        assertTrue(profile.allows(Permission.VIEW_DASHBOARD));
        assertFalse(profile.allows(Permission.MANAGE_SECURITY));
        assertThrows(UnsupportedOperationException.class, () -> profile.permissions().add(Permission.VIEW_JOBS));
    }
}
