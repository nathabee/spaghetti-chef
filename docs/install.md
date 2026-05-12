# Install

This document describes how to install and run PrinterHub from the small expert
packages produced by Jenkins.

PrinterHub does not bundle a Java runtime. Install Java 21 first, then use the
Linux or Windows package without recompiling the project.

---

## Requirements

 
* Required on runtime machine: Java 21
* Required only for build machine / source build: Maven
* Optional diagnostics: curl, sqlite3, minicom




Maven is only needed when building from source. It is not needed when running the
Jenkins packages.

---

## Linux Package

Download:

```text
printer-hub-<version>-linux.tar.gz
```

Extract:

```bash
tar -xzf printer-hub-<version>-linux.tar.gz
cd linux
```

Run with defaults:

```bash
./printerhub.sh
```

The default command uses:

```text 
API port: 18080
database: printerhub.db
```

Run with explicit values:

```bash
./printerhub.sh 18080
```

The launcher currently controls only the API port and database file.
Printer connection details and mode are configured later in the application and persisted in the database.

Use a custom database file:

```bash
PRINTERHUB_DATABASE_FILE=printerhub-prod.db ./printerhub.sh 18080
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

---

## Windows Package

Download:

```text
printer-hub-<version>-windows.zip
```

Extract the zip and open a terminal in the extracted folder.

Run with defaults:

```bat
printerhub.bat
```

The Windows launcher supports:

* explicit command-line arguments for API port
* environment overrides for Java command, API port, and database file
* built-in defaults when no override is provided

At startup, it prints the effective values it is using.

The default command uses:

```text
API port: 18080
database: printerhub.db
```

Run with explicit values:

```bat
printerhub.bat 18080
```

The launcher currently uses the port value and database file. Printer
configuration is managed in the dashboard or persisted database, not created
from the launcher arguments.

Use a custom database file:

```bat
set PRINTERHUB_DATABASE_FILE=printerhub-prod.db
printerhub.bat 18080
```

Open the dashboard:

```text
http://localhost:18080/dashboard
```

---

## Direct Jar Run

Both packages contain the same runnable jar:

```text
printer-hub.jar
```

Linux example:

```bash
java -Dprinterhub.databaseFile=printerhub.db -Dprinterhub.api.port=18080 -jar printer-hub.jar
```

Windows example:

```bat
java -Dprinterhub.databaseFile=printerhub.db -Dprinterhub.api.port=18080 -jar printer-hub.jar
```

If `-Dprinterhub.api.port` is omitted, PrinterHub uses its default API port `8080`.
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
printer-hub-<version>-linux.tar.gz
  Linux runtime package

printer-hub-<version>-windows.zip
  Windows runtime package

printer-hub-<version>-release.tar.gz
  CI evidence bundle with reports, smoke-test outputs, logs, and selected docs

printer-hub-<version>-admin.zip
  Windows remote-administration bootstrap package for OpenSSH-based setup
```

The Linux and Windows packages are the expert runtime packages.

The release archive is the CI evidence bundle. It contains build and test
artifacts such as reports, smoke-test outputs, logs, and selected
documentation.

The admin package contains the PowerShell helper scripts and example runtime
configuration used to bootstrap and operate a remote Windows PrinterHub host
through OpenSSH.

