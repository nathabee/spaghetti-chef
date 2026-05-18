package printerhub.persistence;

import java.time.Instant;

public record OperatorAuditEvent(
        Long id,
        String actor,
        String role,
        String permission,
        String dangerousAction,
        String actionType,
        String targetType,
        String targetId,
        String result,
        String failureReason,
        Instant createdAt
) {
    public static OperatorAuditEvent create(
            String actor,
            String role,
            String permission,
            String dangerousAction,
            String actionType,
            String targetType,
            String targetId,
            String result,
            String failureReason
    ) {
        return new OperatorAuditEvent(
                null,
                actor,
                role,
                permission,
                dangerousAction,
                actionType,
                targetType,
                targetId,
                result,
                failureReason,
                Instant.now());
    }
}
