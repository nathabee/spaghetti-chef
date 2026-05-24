mod analyzer;
mod cli;
mod errors;
mod image_io;
mod result;

use clap::Parser;
use cli::Cli;

fn main() {
    let cli = Cli::parse();

    match analyzer::analyze(&cli) {
        Ok(result) => match serde_json::to_string_pretty(&result) {
            Ok(json) => {
                println!("{json}");
                std::process::exit(0);
            }
            Err(error) => {
                eprintln!("Failed to serialize result JSON: {error}");
                std::process::exit(6);
            }
        },
        Err(error) => {
            eprintln!("{error}");
            std::process::exit(error.exit_code());
        }
    }
}
