package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.config.RuntimeDefaults;

public final class PrintFileSettings {

    private final String storageDirectory;

    public PrintFileSettings(String storageDirectory) {
        if (storageDirectory == null || storageDirectory.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_STORAGE_DIRECTORY_MUST_NOT_BE_BLANK);
        }

        this.storageDirectory = storageDirectory.trim();
    }

    public String storageDirectory() {
        return storageDirectory;
    }

    public static PrintFileSettings defaults() {
        String configuredDirectory = System.getProperty(RuntimeDefaults.PRINT_FILE_STORAGE_DIRECTORY_PROPERTY);

        return new PrintFileSettings(
                configuredDirectory == null || configuredDirectory.isBlank()
                        ? RuntimeDefaults.DEFAULT_PRINT_FILE_STORAGE_DIRECTORY
                        : configuredDirectory);
    }
}
