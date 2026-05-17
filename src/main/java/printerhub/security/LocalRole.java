package printerhub.security;

public enum LocalRole {
    VIEWER("Viewer"),
    OPERATOR("Operator"),
    ADMIN("Admin");

    private final String displayName;

    LocalRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
