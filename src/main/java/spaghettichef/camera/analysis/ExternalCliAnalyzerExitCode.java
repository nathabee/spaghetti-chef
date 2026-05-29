package spaghettichef.camera.analysis;

public enum ExternalCliAnalyzerExitCode {
    SUCCESS(0, "success"),
    INVALID_ARGUMENTS(1, "invalid_arguments"),
    INPUT_FILE_NOT_FOUND(2, "input_file_not_found"),
    IMAGE_DECODING_FAILED(3, "image_decoding_failed"),
    IMAGE_SIZE_MISMATCH(4, "image_size_mismatch"),
    ANALYSIS_FAILED(5, "analysis_failed"),
    INTERNAL_ERROR(6, "internal_error"),
    UNKNOWN(-1, "unknown");

    private final int code;
    private final String label;

    ExternalCliAnalyzerExitCode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ExternalCliAnalyzerExitCode fromCode(int code) {
        for (ExternalCliAnalyzerExitCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
