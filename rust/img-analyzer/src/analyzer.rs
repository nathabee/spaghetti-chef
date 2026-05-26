use crate::cli::Cli;
use crate::errors::AnalyzerError;
use crate::image_io::load_image;
use crate::result::{AnalysisMetrics, AnalysisResult};

pub fn analyze(cli: &Cli) -> Result<AnalysisResult, AnalyzerError> {
    if !(0.0..=1.0).contains(&cli.threshold) {
        return Err(AnalyzerError::InvalidArguments(
            "threshold must be between 0.0 and 1.0".to_string(),
        ));
    }

    let from_image = load_image(&cli.from_snapshot)?.to_rgba8();
    let to_image = load_image(&cli.to_snapshot)?.to_rgba8();

    if from_image.dimensions() != to_image.dimensions() {
        return Err(AnalyzerError::ImageSizeMismatch(format!(
            "from-snapshot is {:?}, to-snapshot is {:?}",
            from_image.dimensions(),
            to_image.dimensions()
        )));
    }

    let mut changed_pixels: u64 = 0;
    let mut total_delta: f64 = 0.0;
    let total_pixels = (from_image.width() as u64) * (from_image.height() as u64);

    if total_pixels == 0 {
        return Err(AnalyzerError::AnalysisFailed(
            "image contains no pixels".to_string(),
        ));
    }

    for (from_pixel, to_pixel) in from_image.pixels().zip(to_image.pixels()) {
        let red_delta = channel_delta(from_pixel[0], to_pixel[0]);
        let green_delta = channel_delta(from_pixel[1], to_pixel[1]);
        let blue_delta = channel_delta(from_pixel[2], to_pixel[2]);

        let pixel_delta = (red_delta + green_delta + blue_delta) / 3.0;
        total_delta += pixel_delta;

        if pixel_delta >= cli.threshold {
            changed_pixels += 1;
        }
    }

    let changed_pixel_ratio = changed_pixels as f64 / total_pixels as f64;
    let average_pixel_delta = total_delta / total_pixels as f64;

    let suspected = changed_pixel_ratio >= cli.threshold || average_pixel_delta >= cli.threshold;

    let mut reason_codes = Vec::new();

    if changed_pixel_ratio >= cli.threshold {
        reason_codes.push("large_delta_area".to_string());
    }

    if average_pixel_delta >= cli.threshold {
        reason_codes.push("high_average_pixel_delta".to_string());
    }

    let message = if suspected {
        "Large visual difference detected between snapshots."
    } else {
        "No large visual difference detected between snapshots."
    };

    Ok(AnalysisResult {
        engine_name: "RUST_CLI_DELTA".to_string(),
        engine_version: env!("CARGO_PKG_VERSION").to_string(),
        algorithm_variant: "FRAME_DELTA".to_string(),
        confidence: average_pixel_delta.max(changed_pixel_ratio),
        suspected,
        reason_codes,
        message: message.to_string(),
        metrics: AnalysisMetrics {
            changed_pixel_ratio,
            average_pixel_delta,
        },
    })
}

fn channel_delta(left: u8, right: u8) -> f64 {
    ((left as i16 - right as i16).abs() as f64) / 255.0
}

#[cfg(test)]
mod tests {
    use super::channel_delta;

    #[test]
    fn channel_delta_returns_zero_for_equal_values() {
        assert_eq!(channel_delta(100, 100), 0.0);
    }

    #[test]
    fn channel_delta_returns_one_for_maximum_difference() {
        assert_eq!(channel_delta(0, 255), 1.0);
    }
}
