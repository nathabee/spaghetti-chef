package printerhub.persistence;

import printerhub.OperationMessages;
import printerhub.security.LocalRole;

public record SecuritySettings(
        boolean securityEnabled,
        LocalRole defaultRole,
        boolean requireDangerousActionConfirmation
) {
    public SecuritySettings {
        if (defaultRole == null) {
            throw new IllegalArgumentException(OperationMessages.LOCAL_ROLE_MUST_NOT_BE_NULL);
        }
    }

    public static SecuritySettings defaults() {
        return new SecuritySettings(false, LocalRole.ADMIN, true);
    }
}
