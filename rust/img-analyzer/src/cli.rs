use clap::Parser;
use std::path::PathBuf;

#[derive(Debug, Parser)]
#[command(name = "printerhub-image-analyzer")]
#[command(about = "Standalone Rust image analyzer for PrinterHub camera snapshots")]
pub struct Cli {
    #[arg(long)]
    pub from_snapshot: PathBuf,

    #[arg(long)]
    pub to_snapshot: PathBuf,

    #[arg(long)]
    pub delta_frame: Option<PathBuf>,

    #[arg(long, default_value = "delta-basic")]
    pub method: String,

    #[arg(long, default_value_t = 0.65)]
    pub threshold: f64,
}
