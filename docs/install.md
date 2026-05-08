# Install

This document describes how to install and run PrinterHub from the small expert
packages produced by Jenkins.

PrinterHub does not bundle a Java runtime. Install Java 21 first, then use the
Linux or Windows package without recompiling the project.

---

## Requirements

Required on every runtime machine:

```text
Java 21
```

Check:

```bash
java -version
```

Expected:

```text
version 21
```

Optional developer/diagnostic tools:

```text
Maven
curl
sqlite3
minicom or another serial console
```

Maven is only needed when building from source. It is not needed when running the
Jenkins package.

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
serial port: /dev/ttyUSB0
mode: real
API port: 18080
database: printerhub.db
```

Run with explicit values:

```bash
./printerhub.sh /dev/ttyUSB0 real 18080
```

Use a custom database file:

```bash
PRINTERHUB_DATABASE_FILE=printerhub-real.db ./printerhub.sh /dev/ttyUSB0 real 18080
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

The default command uses:

```text
serial port: COM3
mode: real
API port: 18080
database: printerhub.db
```

Run with explicit values:

```bat
printerhub.bat COM3 real 18080
```

Use a custom database file:

```bat
set PRINTERHUB_DATABASE_FILE=printerhub-real.db
printerhub.bat COM3 real 18080
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
java -Dprinterhub.databaseFile=printerhub-real.db -jar printer-hub.jar api /dev/ttyUSB0 real 18080
```

Windows example:

```bat
java -Dprinterhub.databaseFile=printerhub-real.db -jar printer-hub.jar api COM3 real 18080
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
dist/printer-hub-<version>-linux.tar.gz
dist/printer-hub-<version>-windows.zip
printer-hub-<version>-release.tar.gz
```

The Linux and Windows packages are the expert runtime packages. The release
archive is CI evidence: reports, smoke-test logs, docs, and release notes.
