# DevOps Overview

This document summarizes the CI pipeline, verification scope, and expert
packaging workflow for SpaghettiChef.

Environment setup and Jenkins installation are described in:

* `install.md`

---

## Jenkins machine prerequisites

The Jenkins host must provide the tools used by the pipeline:

```bash
sudo apt update
sudo apt install openjdk-21-jdk
sudo apt install maven
sudo apt install sqlite3
sudo apt install curl
sudo apt install python3
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

The Jenkins Java installation must be Java 21. SpaghettiChef compiles with Maven
compiler release `21`.

---

## Current pipeline

The Jenkins pipeline currently performs:

```text
Checkout
-> Environment check
-> Maven verify
-> Local runtime smoke test
-> Robustness smoke test
-> Prepare release bundle
-> Package expert Linux and Windows distributions
-> Optional GitHub release publication
-> Archive reports and smoke artifacts
```

Core verification command:

```bash
mvn clean verify
```

This covers:

* compilation
* unit and component verification
* API verification
* persistence verification
* monitoring verification
* serial and simulation non-regression tests
* JaCoCo coverage generation

---

## Current DevOps phase coverage

| DevOps Phase   | Status  | Notes                                                                              |
| -------------- | ------- | ---------------------------------------------------------------------------------- |
| Checkout       | Done    | Source pulled from GitHub                                                          |
| Build          | Done    | Maven compile/package                                                              |
| Test           | Done    | JUnit verification across runtime, monitoring, persistence, API, and serial layers |
| Integrate      | Done    | Runtime components verified together                                               |
| Package        | Done    | shaded jar, Linux package, Windows package, and release archive produced           |
| Runtime verify | Done    | Jenkins smoke lifecycle and robustness checks                                      |
| Release        | Partial | optional GitHub release publication exists                                         |
| Deploy         | Not yet | no persistent staging or production deployment from Jenkins                        |

Current classification:

```text
Continuous Integration with release preparation
```

Not yet fully implemented:

```text
continuous deployment
```

---

## What the pipeline verifies

### Maven verification

The `Verify` stage runs:

```bash
mvn -B -ntp clean verify
```

This produces:

* compiled application
* JUnit reports
* JaCoCo coverage report
* shaded runtime jar

### Local runtime smoke test

The normal lifecycle smoke stage verifies the public runtime/API surface:

```text
remove test database
start runtime
verify /health
verify initial printer list
add simulated printer
verify monitoring updates status
disable printer
verify monitoring stops
enable printer
verify monitoring resumes
update printer configuration
delete printer
inspect SQLite database
restart runtime
verify persisted printers reload
```

### Robustness smoke test

The robustness stage verifies mixed healthy and failing printers together:

```text
good simulated printer
sim-error printer
sim-timeout printer
sim-disconnected printer
API remains responsive
good printer continues updating
bad printers report ERROR
failure events are persisted with origin printer id
database event noise stays bounded
dashboard still loads
```

### HTTP robustness checks

The pipeline also verifies controlled error responses:

```text
invalid POST body -> 400
unknown printer -> 404
wrong method -> 405
missing required field -> 400
```

---

## Generated artifacts

After a successful pipeline run, the archived artifacts include:

```text
target/surefire-reports/**
target/site/jacoco/**
target/runtime-smoke.log
target/runtime-robustness.log
target/operator-message-report.md
target/*.json
target/*.txt
release/**
dist/spaghetti-chef-<version>-linux.tar.gz
dist/spaghetti-chef-<version>-windows.zip
*.tar.gz
```

These artifacts provide evidence for:

* test execution
* runtime/API verification
* persistence inspection
* coverage reporting
* smoke scenario review
* release bundle contents

---

## Release bundle contents

The release preparation stage collects runtime and verification outputs into a reproducible bundle.

Typical contents:

```text
release/
├── spaghetti-chef-<version>-all.jar
├── jacoco/
├── README.md
├── test.md
├── devops.md
├── roadmap.md
├── version.md
└── smoke/
```

The smoke folder may include:

```text
health.json
printers-initial.json
printer-created.json
printer-after-enable.json
printers-after-restart.json
runtime-smoke.log
runtime-robustness.log
configured-printers.txt
printer-snapshots.txt
printer-events.txt
```

---

## Expert Runtime Packages

When `RELEASE_VERSION` is set, Jenkins also creates two small expert packages:

```text
spaghetti-chef-<version>-linux.tar.gz
spaghetti-chef-<version>-windows.zip
```

Both packages contain the same shaded runnable jar. They do not bundle Java.

Linux package:

```text
linux/
├── spaghetti-chef.jar
├── spaghettichef.sh
├── README.md
├── INSTALL.md
└── QUICKSTART.md
```

Windows package:

```text
windows/
├── spaghetti-chef.jar
├── spaghettichef.bat
├── README.md
├── INSTALL.md
└── QUICKSTART.md
```

This gives expert Linux and Windows users a no-recompile runtime package while
keeping GitHub artifacts small.

---

## Operator-facing evidence

The pipeline keeps an operator-facing report:

```text
target/operator-message-report.md
```

This report summarizes operationally relevant CI evidence such as:

* runtime startup and shutdown
* health and API behavior
* monitoring failure scenarios
* persistence-visible error evidence
* robustness verification outcomes

This continues the old `0.0.x` operator-message idea in the current `0.1.x` runtime form.

---

## Release and branch workflow

SpaghettiChef uses two GitHub branches:

* `develop` = current development
* `main` = approved production branch

A release is prepared from `develop` and marked with a tested tag such as:

```text
0.1.16
```

Later, `main` is aligned to that exact tag.

This allows `develop` to move forward independently while `main` stays on the approved release.

Important rule:

```text
If production must match a tested release, reset main to the tag, not to origin/develop.
```

### 1. On local `develop`: prepare and push the release candidate

```bash
git checkout develop
git add .
git status
git commit -m "0.1.16 docs update"
git push origin develop
```

If Jenkins creates the release tag, stop here and let Jenkins publish the release and tag `0.1.16`.

If the tag is created manually instead, use:

```bash
git tag 0.1.16
git push origin 0.1.16
```

### 2. Continue development on `develop`

After the release tag exists, `develop` may continue with new unfinished work.

That is valid.

Example:

```bash
git checkout develop
git add .
git commit -m "Start 0.1.17 work"
git push origin develop
```

Now:

* `0.1.16` still points to the tested release commit
* `develop` may already be ahead
* `main` is still unchanged until promotion is done

### 3. Later: promote `main` to the tested tag

From a local repository where branch switching is possible:

```bash
git fetch origin --tags
git checkout main
git reset --hard 0.1.16
git push --force-with-lease origin main
```

This aligns `main` to the tested release tag.

It does not use `origin/develop`.

### 4. Update the separate local `main` folder

If a second local clone or folder is used for the production branch:

```bash
cd ~/coding/github/spaghetti-chef/main
git fetch origin --tags
git checkout main
git reset --hard origin/main
```

This updates the local production folder to the current remote `main`.

---
