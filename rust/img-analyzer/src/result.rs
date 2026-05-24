use serde::Serialize;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AnalysisResult {
    pub engine_name: String,
    pub engine_version: String,
    pub algorithm_variant: String,
    pub confidence: f64,
    pub suspected: bool,
    pub reason_codes: Vec<String>,
    pub message: String,
    pub metrics: AnalysisMetrics,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AnalysisMetrics {
    pub changed_pixel_ratio: f64,
    pub average_pixel_delta: f64,
}
