package printerhub.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {
    @Test
    void builtInAuthorizationAllowsRolePermissions() {
        AuthorizationService authorizationService = new AuthorizationService();

        assertTrue(authorizationService.isAllowed(LocalRole.VIEWER, Permission.VIEW_DASHBOARD));
        assertTrue(authorizationService.isAllowed(LocalRole.OPERATOR, Permission.CONTROL_JOBS));
        assertTrue(authorizationService.isAllowed(LocalRole.ADMIN, Permission.MANAGE_SECURITY));
    }

    @Test
    void builtInAuthorizationDeniesMissingPermissions() {
        AuthorizationService authorizationService = new AuthorizationService();

        assertFalse(authorizationService.isAllowed(LocalRole.VIEWER, Permission.CONTROL_JOBS));
        assertFalse(authorizationService.isAllowed(LocalRole.OPERATOR, Permission.MANAGE_SECURITY));
        assertFalse(authorizationService.isAllowed(null, Permission.VIEW_DASHBOARD));
        assertFalse(authorizationService.isAllowed(LocalRole.ADMIN, null));
    }

    @Test
    void requireThrowsForDeniedPermission() {
        AuthorizationService authorizationService = new AuthorizationService();

        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> authorizationService.require(LocalRole.VIEWER, Permission.CONFIGURE_PRINTERS)
        );

        assertEquals("Permission denied for role VIEWER: CONFIGURE_PRINTERS", exception.getMessage());
    }

    @Test
    void requireDoesNotThrowForAllowedPermission() {
        AuthorizationService authorizationService = new AuthorizationService();

        assertDoesNotThrow(() -> authorizationService.require(LocalRole.ADMIN, Permission.CONFIGURE_PRINTERS));
    }
}
