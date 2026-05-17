package printerhub.security;

import printerhub.OperationMessages;

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
        return profile != null && profile.allows(permission);
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
}
