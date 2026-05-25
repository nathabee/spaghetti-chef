package spaghettichef;

public final class AppVersion {

    private AppVersion() {
    }

    public static String current() {
        String configured = System.getProperty("spaghettichef.version");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }

        String implementationVersion = AppVersion.class.getPackage().getImplementationVersion();
        if (implementationVersion != null && !implementationVersion.isBlank()) {
            return implementationVersion.trim();
        }

        return "development";
    }
}
