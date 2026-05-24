use std::fmt;

#[derive(Debug)]
pub enum AnalyzerError {
    InvalidArguments(String),
    InputFileNotFound(String),
    ImageDecodeFailed(String),
    ImageSizeMismatch(String),
    AnalysisFailed(String),
}

impl AnalyzerError {
    pub fn exit_code(&self) -> i32 {
        match self {
            AnalyzerError::InvalidArguments(_) => 1,
            AnalyzerError::InputFileNotFound(_) => 2,
            AnalyzerError::ImageDecodeFailed(_) => 3,
            AnalyzerError::ImageSizeMismatch(_) => 4,
            AnalyzerError::AnalysisFailed(_) => 5,
        }
    }
}

impl fmt::Display for AnalyzerError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AnalyzerError::InvalidArguments(message) => {
                write!(formatter, "Invalid arguments: {message}")
            }
            AnalyzerError::InputFileNotFound(message) => {
                write!(formatter, "Input file not found: {message}")
            }
            AnalyzerError::ImageDecodeFailed(message) => {
                write!(formatter, "Image decoding failed: {message}")
            }
            AnalyzerError::ImageSizeMismatch(message) => {
                write!(formatter, "Image size mismatch: {message}")
            }
            AnalyzerError::AnalysisFailed(message) => {
                write!(formatter, "Analysis failed: {message}")
            }
        }
    }
}

impl std::error::Error for AnalyzerError {}
