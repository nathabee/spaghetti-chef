use crate::errors::AnalyzerError;
use image::DynamicImage;
use std::path::Path;

pub fn load_image(path: &Path) -> Result<DynamicImage, AnalyzerError> {
    if !path.exists() {
        return Err(AnalyzerError::InputFileNotFound(path.display().to_string()));
    }

    image::open(path)
        .map_err(|error| AnalyzerError::ImageDecodeFailed(format!("{}: {}", path.display(), error)))
}
