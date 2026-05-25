package spaghettichef.security;

import spaghettichef.OperationMessages;

import java.util.Map;
import java.util.Objects;

public final class AuthorizationService {
    private final Map<LocalRole, RoleProfile> roleProfiles;

    public AuthorizationService() {
        this(RoleProfile.builtInProfiles());
    }

    public AuthorizationService(Map<LocalRole, RoleProfile> roleProfiles) {
        if (roleProfiles == null) {
            throw new IllegalArgumentException(OperationMessages.ROLE_PROFILES_MUST_NOT_BE_NULL);
        }

        this.roleProfiles = Map.copyOf(roleProfiles);
    }

    public boolean isAllowed(LocalRole role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }

        RoleProfile profile = roleProfiles.get(role);
        return profile != null && (profile.allows(permission) || allowsLegacyEquivalent(profile, permission));
    }

    public void require(LocalRole role, Permission permission) {
        if (!isAllowed(role, permission)) {
            throw new AuthorizationException(OperationMessages.permissionDenied(role, permission));
        }
    }

    public RoleProfile profile(LocalRole role) {
        Objects.requireNonNull(role, OperationMessages.LOCAL_ROLE_MUST_NOT_BE_NULL);
        return roleProfiles.get(role);
    }

    public Map<LocalRole, RoleProfile> roleProfiles() {
        return roleProfiles;
    }

    private boolean allowsLegacyEquivalent(RoleProfile profile, Permission permission) {
        return switch (permission) {
            case PRINTER_VIEW -> profile.allows(Permission.VIEW_PRINTERS);
            case PRINTER_CONFIGURE -> profile.allows(Permission.CONFIGURE_PRINTERS);
            case MONITORING_VIEW -> profile.allows(Permission.VIEW_MONITORING);
            case MONITORING_CONFIGURE -> profile.allows(Permission.CONFIGURE_MONITORING);
            case JOB_VIEW -> profile.allows(Permission.VIEW_JOBS);
            case JOB_CREATE, JOB_START, JOB_PAUSE, JOB_RESUME, JOB_CANCEL, JOB_RESTART, JOB_DELETE ->
                    profile.allows(Permission.CONTROL_JOBS);
            case SD_VIEW, SD_REFRESH -> profile.allows(Permission.VIEW_PRINTERS);
            case SD_UPLOAD, SD_RECOVERY_CLOSE_UPLOAD -> profile.allows(Permission.UPLOAD_TO_SD_CARD);
            case SD_DELETE -> profile.allows(Permission.MANAGE_SD_CARD_FILES);
            case COMMAND_READ, COMMAND_SAFE_CONTROL -> profile.allows(Permission.EXECUTE_SAFE_COMMANDS);
            case COMMAND_DANGEROUS_CONTROL, COMMAND_RAW -> profile.allows(Permission.EXECUTE_DANGEROUS_COMMANDS);
            case SETTINGS_VIEW -> profile.allows(Permission.VIEW_SETTINGS);
            case SETTINGS_UPDATE -> profile.allows(Permission.CONFIGURE_MONITORING)
                    || profile.allows(Permission.CONFIGURE_TRANSFER_SETTINGS);
            case SECURITY_VIEW, SECURITY_MANAGE -> profile.allows(Permission.MANAGE_SECURITY);
            case CAMERA_DATA_MANAGE -> profile.allows(Permission.MANAGE_SECURITY);
            default -> false;
        };
    }
}
