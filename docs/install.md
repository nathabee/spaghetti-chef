# Install

This document describes how to install and run SpaghettiChef from the small expert
packages produced by Jenkins.

SpaghettiChef does not bundle a Java runtime. Install Java 21 first, then use the
Linux or Windows package without recompiling the project.

---

## Requirements

 
* Required on runtime machine: Java 21
* Required for real webcam capture: ffmpeg available on PATH or configured with a full executable path
* Required only for build machine / source build: Maven
* Optional diagnostics: curl, sqlite3, minicom




Maven is only needed when building from source. It is not needed when running the
Jenkins packages.

---

## Linux Package

Download:

```text
spaghetti-chef-<version>-linux.tar.gz
```

Extract:

```bash
tar -xzf spaghetti-chef-<version>-linux.tar.gz
cd linux
```

Run with defaults:

```bash
./spaghettichef.sh
```

The default command uses:

```text 
API port: 18080
database: spaghettichef.db
```

Run with explicit values:

```bash
./spaghettichef.sh 18080
```

The launcher currently controls only the API port and database file.
Printer connection details and mode are configured later in the application and persisted in the database.

Use a custom database file:

```bash
SPAGHETTICHEF_DATABASE_FILE=spaghettichef-prod.db ./spaghettichef.sh 18080
```

Open the dashboard:

```text
http://localhost:18080/dashboard
```

### Linux Serial Permission

If `/dev/ttyUSB0` cannot be opened, add your user to `dialout`:

```bash
sudo usermod -aG dialout $USER
```

Then log out and back in.

Check:

```bash
groups
```

### Stable Linux Serial Paths

For real printers, prefer the stable path from:

```bash
ls -l /dev/serial/by-id/
```

Use the full `/dev/serial/by-id/...` value as the printer `portName`. Paths such as `/dev/ttyUSB0` and `/dev/ttyACM0` are accepted, but they can change after reconnect or reboot. The dashboard shows a warning when a real printer is configured with one of those unstable Linux USB names.

### Linux Webcam Capture

SpaghettiChef real webcam capture uses ffmpeg behind the existing `CameraDevice` abstraction.
Install ffmpeg first:

```bash
sudo apt install ffmpeg
```

In the dashboard, open the selected printer camera settings and use:

```text
Source type: ffmpeg webcam
Source value: /dev/video0
ffmpeg command: ffmpeg
ffmpeg input format: v4l2
ffmpeg video size: 640x480
```

If capture fails, verify the device and ffmpeg command directly:

```bash
ffmpeg -f v4l2 -video_size 640x480 -i /dev/video0 -frames:v 1 test.jpg
```

---

## Windows Package

Download:

```text
spaghetti-chef-<version>-windows.zip
```

Extract the zip and open a terminal in the extracted folder.

Run with defaults:

```bat
spaghettichef.bat
```

The Windows launcher supports:

* explicit command-line arguments for API port
* environment overrides for Java command, API port, and database file
* built-in defaults when no override is provided

At startup, it prints the effective values it is using.

The default command uses:

```text
API port: 18080
database: spaghettichef.db
```

Run with explicit values:

```bat
spaghettichef.bat 18080
```

The launcher currently uses the port value and database file. Printer
configuration is managed in the dashboard or persisted database, not created
from the launcher arguments.

Use a custom database file:

```bat
set SPAGHETTICHEF_DATABASE_FILE=spaghettichef-prod.db
spaghettichef.bat 18080
```

Open the dashboard:

```text
http://localhost:18080/dashboard
```

### Windows Webcam Capture

Install ffmpeg and either put `ffmpeg.exe` on PATH or configure the full executable path in the camera settings.

List DirectShow devices:

```bat
ffmpeg -list_devices true -f dshow -i dummy
```

Then configure the selected printer camera settings:

```text
Source type: ffmpeg webcam
Source value: video=Integrated Camera
ffmpeg command: ffmpeg
ffmpeg input format: dshow
ffmpeg video size: 640x480
```

Test capture directly if needed:

```bat
ffmpeg -f dshow -video_size 640x480 -i "video=Integrated Camera" -frames:v 1 test.jpg
```

---

## Direct Jar Run

Both packages contain the same runnable jar:

```text
spaghetti-chef.jar
```

Linux example:

```bash
java -Dspaghettichef.databaseFile=spaghettichef.db -Dspaghettichef.api.port=18080 -jar spaghetti-chef.jar
```

Windows example:

```bat
java -Dspaghettichef.databaseFile=spaghettichef.db -Dspaghettichef.api.port=18080 -jar spaghetti-chef.jar
```

If `-Dspaghettichef.api.port` is omitted, SpaghettiChef uses its default API port `8080`.
The dashboard will then be available at: 

```text
http://localhost:8080/dashboard
```

---

## Jenkins Machine Requirements

The Jenkins machine that builds the packages needs:

```bash
sudo apt update
sudo apt install openjdk-21-jdk maven sqlite3 curl python3
```

Check:

```bash
java -version
javac -version
mvn -version
sqlite3 --version
curl --version
python3 --version
```

The pipeline uses these tools for:

```text
Java 21 compilation
Maven test/package execution
SQLite smoke-test inspection
curl HTTP smoke checks
python3 JSON field extraction in smoke tests
jar command for the Windows zip package
tar for the Linux package and release archive
```

---
 
## Jenkins Artifacts

For release builds with `RELEASE_VERSION` set, Jenkins produces:

```text
spaghetti-chef-<version>-linux.tar.gz
  Linux runtime package

spaghetti-chef-<version>-windows.zip
  Windows runtime package

spaghetti-chef-<version>-release.tar.gz
  CI evidence bundle with reports, smoke-test outputs, logs, and selected docs

spaghetti-chef-<version>-admin.zip
  Windows remote-administration bootstrap package for OpenSSH-based setup
```

The Linux and Windows packages are the expert runtime packages.

The release archive is the CI evidence bundle. It contains build and test
artifacts such as reports, smoke-test outputs, logs, and selected
documentation.

The admin package contains the PowerShell helper scripts and example runtime
configuration used to bootstrap and operate a remote Windows SpaghettiChef host
through OpenSSH.


---
