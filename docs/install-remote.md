# Remote Windows Install and Update via OpenSSH

This document describes the emergency remote administration setup for a Windows
PrinterHub host reachable over Wi-Fi from a Linux admin machine.

Scope of this setup:

- one-time physical bootstrap on the Windows laptop
- remote status check from Linux
- remote update from Linux
- remote diagnostic collection from Linux
- reuse existing GitHub Windows release ZIP
- preserve runtime data on the Windows host

This is an operational bootstrap solution before the future admin
dashboard and remote farm management scope.

---

## Goal

Allow a Linux admin machine to remotely trigger a PrinterHub update on a Windows
10 laptop using OpenSSH and PowerShell.

The update flow must:

- verify Java 21 is installed on the Windows host
- stop the running PrinterHub runtime
- download a specific GitHub release asset
- extract the new Windows package
- preserve persisted data
- restart PrinterHub
- expose short remote status and diagnostic commands

---

## Architecture

### Repository

```text
printer-hub/
├── app source
├── docs/
│   └── install-remote.md
├── tools/
│   ├── linux/
│   │   └── camera/
│   │       ├── camera-capture-loop.sh
│   │       └── camera-capture-once.sh
│   ├── win/
│   │   ├── camera/
│   │   │   ├── camera-capture-loop.ps1
│   │   │   └── camera-capture-once.ps1
│   │   ├── run.env.example
│   │   ├── u.ps1
│   │   ├── r.ps1
│   │   ├── s.ps1
│   │   ├── t.ps1
│   │   └── v.ps1
│   └── README.md
└── ops/
    ├── phdiag
    ├── phu
    └── phv
````

### Windows laptop

```text
C:\printerhub\
├── app
├── bin
├── data
├── log
├── rel
└── tmp
```

Expected meaning:

```text
app\   runtime application package
bin\   operational PowerShell scripts and helper tools
data\  persistent database, runtime configuration, camera storage
log\   runtime logs
rel\   downloaded release archives
tmp\   temporary extraction, diagnostics, backups
```

### Linux admin machine

The Linux admin machine uses short helper commands to connect through SSH and
launch remote PowerShell scripts on the Windows host.

The `ops/` scripts are admin-workstation helpers. They are not part of the
Windows runtime package.

---

## Responsibilities of the scripts

### Windows scripts

* `t.ps1`

  * create or refresh the Windows Task Scheduler entry for PrinterHub
  * prepare detached background start on the Windows host

* `u.ps1`

  * remote update entrypoint
  * verify Java 21
  * download a specific release asset
  * stop PrinterHub
  * extract the package
  * replace app files
  * restart PrinterHub through Task Scheduler

* `r.ps1`

  * start PrinterHub through the scheduled task
  * verify health after start

* `s.ps1`

  * stop PrinterHub

* `v.ps1`

  * status, health, and scheduled-task diagnostics

* `run.env.example`

  * example local runtime configuration file
  * copied once to `C:\printerhub\data\run.env`

* `camera/`

  * optional Windows camera helper scripts
  * used for diagnostics and fallback ffmpeg capture
  * delivered with the admin/tool package
  * copied to `C:\printerhub\bin\camera\`
  * not part of the runtime app package
  * not bundled into `printer-hub.jar`

### Linux helper commands

* `phu`

  * remote update launcher

* `phv`

  * remote status launcher

* `phdiag`

  * remote diagnostic launcher
  * uploads and runs a temporary PowerShell diagnostic script on the Windows host
  * retrieves `C:\printerhub\tmp\response.txt` back to the Linux machine

---

## Release packaging assumption

The GitHub release tag uses:

```text
v<version>
```

The Windows application asset file uses:

```text
printer-hub-<version>-windows.zip
```

The Windows administration asset file uses:

```text
printer-hub-<version>-admin.zip
```

Example:

```text
tag: v1.0.0
application asset: printer-hub-1.0.0-windows.zip
admin asset:       printer-hub-1.0.0-admin.zip
```

---

## Runtime assumptions

The extracted Windows application package contains the runtime app files, for
example:

```text
printerhub.bat
printer-hub.jar
README.md
INSTALL.md
QUICKSTART.md
```

The Windows application package must not contain operational PowerShell helper
scripts such as camera capture scripts. Those belong in the admin/tool package.

Current launcher behavior:

* Java command: `PRINTERHUB_JAVA` from `C:\printerhub\data\run.env`, otherwise `java`
* API port: `PRINTERHUB_API_PORT` from `C:\printerhub\data\run.env`, otherwise `18080`
* database file: `PRINTERHUB_DATABASE_FILE` from `C:\printerhub\data\run.env`, otherwise `printerhub.db`

Use a full path for `PRINTERHUB_DATABASE_FILE`:

```text
PRINTERHUB_DATABASE_FILE=C:\printerhub\data\printerhub.db
```

Current dashboard URL:

```text
http://localhost:18080/dashboard
```

Java 21 must already be installed on the Windows laptop before remote install or
update is attempted.

---

## One-time physical setup on the Windows laptop

This must be done locally on the Windows machine once.

### 1. Install Java 21

Verify Java 21 is available:

```powershell
java -version
```

Expected major version:

```text
21
```

If Java is missing or the version is not `21`, install Java 21 before
continuing.

---

### 2. Install OpenSSH Server

Open PowerShell as Administrator and run:

```powershell
Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
```

Then start the SSH server and enable it at boot:

```powershell
Start-Service sshd
Set-Service -Name sshd -StartupType Automatic
```

Optional check:

```powershell
Get-Service sshd
```

Expected status:

```text
Running
```

---

### 3. Confirm the Windows host IP address

Open `cmd.exe` or PowerShell and run:

```powershell
ipconfig
```

Find the Wi-Fi IPv4 address, for example:

```text
192.168.1.42
```

This IP is needed by the Linux helper scripts unless local hostname resolution
already works.

---

### 4. Create the remote runtime folders

Open PowerShell as Administrator:

```powershell
New-Item -ItemType Directory -Force -Path C:\printerhub\app
New-Item -ItemType Directory -Force -Path C:\printerhub\bin
New-Item -ItemType Directory -Force -Path C:\printerhub\data
New-Item -ItemType Directory -Force -Path C:\printerhub\log
New-Item -ItemType Directory -Force -Path C:\printerhub\rel
New-Item -ItemType Directory -Force -Path C:\printerhub\tmp
```

---

### 5. Install the admin bootstrap files on the Windows laptop

Download:

```text
printer-hub-<version>-admin.zip
```

Extract the ZIP on the Windows laptop.

Copy the extracted PowerShell scripts into:

```text
C:\printerhub\bin\
```

Copy the extracted camera helper directory, if present, into:

```text
C:\printerhub\bin\camera\
```

Copy the extracted example environment file to:

```text
C:\printerhub\data\run.env
```

The admin package is operational tooling. It is separate from the Windows
application package.

Camera PowerShell helpers belong under:

```text
C:\printerhub\bin\camera\
```

They must not be placed under:

```text
C:\printerhub\app\camera\
```

---

### 6. Review local runtime configuration

Open:

```text
C:\printerhub\data\run.env
```

Initial example content:

```text
PRINTERHUB_JAVA=
PRINTERHUB_DATABASE_FILE=C:\printerhub\data\printerhub.db
PRINTERHUB_API_PORT=18080
```

Adjust values if needed for the target Windows laptop.

Example with explicit Java path:

```text
PRINTERHUB_JAVA=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe
PRINTERHUB_DATABASE_FILE=C:\printerhub\data\printerhub.db
PRINTERHUB_API_PORT=18080
```

---

### 7. Register the scheduled task once

Open a normal PowerShell window with the same Windows user that will run
PrinterHub and execute:

```powershell
C:\printerhub\bin\t.ps1
```

The scheduled task should run as the intended local Windows user.

For a simple lab setup, avoid mixing different users for runtime and update
unless permissions are deliberately configured.

---

### 8. Optional first manual package install

If desired, install the first Windows application release manually before remote
updates are used:

1. Download `printer-hub-<version>-windows.zip`
2. Extract it into `C:\printerhub\app`
3. Verify these files exist:

```text
C:\printerhub\app\printerhub.bat
C:\printerhub\app\printer-hub.jar
```

Then refresh the scheduled task:

```powershell
C:\printerhub\bin\t.ps1
```

Then test start:

```powershell
C:\printerhub\bin\r.ps1
```

Then test status:

```powershell
C:\printerhub\bin\v.ps1
```

---

## Linux admin machine setup

### 1. Create helper scripts

The repo contains:

* `ops/phu`
* `ops/phv`
* `ops/phdiag`

Make them executable:

```bash
chmod +x ops/phu ops/phv ops/phdiag
```

---

### 2. Configure remote host and user

The helper scripts use environment variables:

* `PH_HOST`
* `PH_USER`

Example:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phv
```

or:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phu 1.0.0
```

or:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phdiag
```

If you want less typing, export them once in the shell:

```bash
export PH_HOST=192.168.1.42
export PH_USER=myadmin
```

Then use:

```bash
ops/phv
ops/phu 1.0.0
ops/phdiag
```

---

### 3. Test SSH connectivity from Linux

Run:

```bash
ssh myadmin@192.168.1.42
```

If that works, test the status script:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phv
```

---

## Remote operation flow

The remote start path uses Windows Task Scheduler so that PrinterHub continues
running after the SSH session disconnects.

---

### Status check

From Linux:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phv
```

This launches:

```text
C:\printerhub\bin\v.ps1
```

on the Windows host.

---

### Update to a specific version

From Linux:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phu 1.0.0
```

This launches:

```text
C:\printerhub\bin\u.ps1 -Version 1.0.0
```

on the Windows host.

The updater downloads:

```text
https://github.com/<owner>/<repo>/releases/download/v<version>/printer-hub-<version>-windows.zip
```

Example download URL:

```text
https://github.com/nathabee/printer-hub/releases/download/v1.0.0/printer-hub-1.0.0-windows.zip
```

---

### Diagnostic collection

From Linux:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phdiag
```

The diagnostic helper uploads and runs a temporary PowerShell diagnostic script
on the Windows host.

The Windows host writes:

```text
C:\printerhub\tmp\response.txt
```

The Linux helper downloads the diagnostic result as:

```text
./response.txt
```

Use this when remote update or runtime behavior needs to be checked without
manually copying logs.

---

## Data preservation rules

The updater must not replace persisted runtime data in:

```text
C:\printerhub\data\
```

In particular, database files must remain intact across updates.

The updater must also preserve these operational directories:

```text
C:\printerhub\bin\
C:\printerhub\log\
C:\printerhub\rel\
C:\printerhub\tmp\
```

The updater is responsible only for the application package in:

```text
C:\printerhub\app\
```

The admin/tool package is installed separately into:

```text
C:\printerhub\bin\
```

Camera helper scripts belong in:

```text
C:\printerhub\bin\camera\
```

not in:

```text
C:\printerhub\app\camera\
```

---

## Windows package separation

The Windows application package should contain only runtime application files,
for example:

```text
printer-hub.jar
printerhub.bat
README.md
INSTALL.md
QUICKSTART.md
```

The Windows admin package should contain operational tooling, for example:

```text
r.ps1
s.ps1
t.ps1
u.ps1
v.ps1
run.env.example
camera\camera-capture-loop.ps1
camera\camera-capture-once.ps1
INSTALL-REMOTE.md
README.txt
```

Rules:

* `tools/win/**` is packaged into the Windows admin package.
* `tools/win/camera/**` is packaged into the Windows admin package under `camera/`.
* `tools/linux/**` is packaged into the Linux package tool area.
* `tools/**` must not be bundled into `printer-hub.jar`.
* Windows camera helper scripts must not be copied into the Windows app package.

---

## Camera helper notes

PrinterHub supports direct ffmpeg webcam capture from the dashboard. The
external camera helper scripts are mainly diagnostic tools and fallback
utilities.

For normal runtime camera capture, prefer the dashboard camera configuration.

Windows DirectShow camera listing:

```powershell
ffmpeg -list_devices true -f dshow -i dummy
```

Example dashboard configuration:

```text
Source type: ffmpeg webcam
Source value: video=AUKEY Webcam
ffmpeg command: ffmpeg
ffmpeg input format: dshow
ffmpeg video size: 640x480
```

The Windows source value usually needs the `video=` prefix.

Example helper path after admin package installation:

```text
C:\printerhub\bin\camera\camera-capture-once.ps1
C:\printerhub\bin\camera\camera-capture-loop.ps1
```

---

## Troubleshooting

### Scheduled task state

After stopping PrinterHub manually, the scheduled task may show:

```text
Ready
```

This is normal. `Ready` means the task is registered and not currently running.

When PrinterHub is started through the task, the state should become:

```text
Running
```

---

### LastTaskResult 267014

`LastTaskResult` may show:

```text
267014
```

This corresponds to a task that was stopped or terminated. If you stopped the
task manually, this is not suspicious.

---

### Health endpoint

Check locally on the Windows host:

```powershell
curl.exe http://localhost:18080/health
```

Expected:

```json
{"status":"ok"}
```

From Linux, use:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phv
```

---

### Logs

Runtime logs are expected under:

```text
C:\printerhub\log\
```

Common files:

```text
printerhub-out.log
printerhub-err.log
start.log
update.log
```

---

### Remote diagnostic response

If `ops/phdiag` is used, the diagnostic result is downloaded to the Linux admin
machine as:

```text
./response.txt
```

The temporary remote result is written to:

```text
C:\printerhub\tmp\response.txt
```

---

## Known current limitation

The current Windows update script may still use an in-place app file replacement
strategy when directory replacement is blocked by Windows.

The intended future cleanup is:

```text
stop runtime
backup current app by copy
delete known package-owned app files
copy new app files
verify installed JAR hash
start runtime
```

This avoids fragile directory moves on Windows while still proving that the
installed JAR matches the downloaded package.

 