package spaghettichef.security;

import spaghettichef.OperationMessages;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public record RoleProfile(
        LocalRole role,
        String displayName,
        Set<Permission> permissions,
        boolean builtIn
) {
    private static final Set<Permission> VIEWER_PERMISSIONS = EnumSet.of(
            Permission.VIEW_DASHBOARD,
            Permission.VIEW_PRINTERS,
            Permission.VIEW_MONITORING,
            Permission.VIEW_JOBS,
            Permission.VIEW_HISTORY,
            Permission.VIEW_SETTINGS,
            Permission.PRINTER_VIEW,
            Permission.MONITORING_VIEW,
            Permission.JOB_VIEW,
            Permission.SD_VIEW,
            Permission.SETTINGS_VIEW,
            Permission.SECURITY_VIEW
    );

    private static final Set<Permission> OPERATOR_PERMISSIONS = union(
            VIEWER_PERMISSIONS,
            EnumSet.of(
                    Permission.MANAGE_PRINT_FILES,
                    Permission.MANAGE_SD_CARD_FILES,
                    Permission.UPLOAD_TO_SD_CARD,
                    Permission.CONTROL_JOBS,
                    Permission.EXECUTE_SAFE_COMMANDS,
                    Permission.JOB_CREATE,
                    Permission.JOB_START,
                    Permission.JOB_PAUSE,
                    Permission.JOB_RESUME,
                    Permission.JOB_CANCEL,
                    Permission.JOB_RESTART,
                    Permission.SD_REFRESH,
                    Permission.SD_UPLOAD,
                    Permission.SD_RECOVERY_CLOSE_UPLOAD,
                    Permission.COMMAND_READ,
                    Permission.COMMAND_SAFE_CONTROL
            )
    );

    private static final Set<Permission> ADMIN_PERMISSIONS = union(
            OPERATOR_PERMISSIONS,
            EnumSet.of(
                    Permission.VIEW_SETTINGS,
                    Permission.CONFIGURE_PRINTERS,
                    Permission.CONFIGURE_MONITORING,
                    Permission.CONFIGURE_TRANSFER_SETTINGS,
                    Permission.EXECUTE_DANGEROUS_COMMANDS,
                    Permission.MANAGE_SECURITY,
                    Permission.PRINTER_CONFIGURE,
                    Permission.MONITORING_CONFIGURE,
                    Permission.JOB_DELETE,
                    Permission.SD_DELETE,
                    Permission.COMMAND_DANGEROUS_CONTROL,
                    Permission.COMMAND_RAW,
                    Permission.SETTINGS_UPDATE,
                    Permission.SECURITY_MANAGE,
                    Permission.CAMERA_DATA_MANAGE
            )
    );

    public RoleProfile {
        if (role == null) {
            throw new IllegalArgumentException(OperationMessages.LOCAL_ROLE_MUST_NOT_BE_NULL);
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.ROLE_DISPLAY_NAME_MUST_NOT_BE_BLANK);
        }
        if (permissions == null) {
            throw new IllegalArgumentException(OperationMessages.PERMISSIONS_MUST_NOT_BE_NULL);
        }

        displayName = displayName.trim();
        permissions = Collections.unmodifiableSet(copyPermissions(permissions));
    }

    public boolean allows(Permission permission) {
        return permission != null && permissions.contains(permission);
    }

    public static Map<LocalRole, RoleProfile> builtInProfiles() {
        Map<LocalRole, RoleProfile> profiles = new EnumMap<>(LocalRole.class);
        profiles.put(LocalRole.VIEWER, builtIn(LocalRole.VIEWER, VIEWER_PERMISSIONS));
        profiles.put(LocalRole.OPERATOR, builtIn(LocalRole.OPERATOR, OPERATOR_PERMISSIONS));
        profiles.put(LocalRole.ADMIN, builtIn(LocalRole.ADMIN, ADMIN_PERMISSIONS));
        return Collections.unmodifiableMap(profiles);
    }

    public static RoleProfile builtIn(LocalRole role) {
        return builtInProfiles().get(role);
    }

    private static RoleProfile builtIn(LocalRole role, Set<Permission> permissions) {
        return new RoleProfile(role, role.displayName(), permissions, true);
    }

    private static Set<Permission> copyPermissions(Set<Permission> permissions) {
        if (permissions.isEmpty()) {
            return EnumSet.noneOf(Permission.class);
        }

        return EnumSet.copyOf(permissions);
    }

    private static Set<Permission> union(Set<Permission> base, Set<Permission> additions) {
        Set<Permission> result = copyPermissions(base);
        result.addAll(additions);
        return result;
    }
}
