package printerhub.security;

public final class ConfirmationRequiredException extends RuntimeException {
    private final DangerousAction requiredConfirmation;

    public ConfirmationRequiredException(DangerousAction requiredConfirmation) {
        super("Confirmation required for " + requiredConfirmation);
        this.requiredConfirmation = requiredConfirmation;
    }

    public DangerousAction requiredConfirmation() {
        return requiredConfirmation;
    }
}
