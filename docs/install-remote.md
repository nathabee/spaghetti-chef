# Remote Windows Install and Update via OpenSSH

This document describes the emergency remote administration setup for a Windows
PrinterHub host reachable over Wi-Fi from a Linux admin machine.

Scope of this setup:

- one-time physical bootstrap on the Windows laptop
- remote status check from Linux
- remote update from Linux
- reuse existing GitHub Windows release zip
- preserve runtime data on the Windows host

This is an operational bootstrap solution before the future `1.0.0` admin
dashboard and remote farm management scope.

---

## Goal

Allow a Linux admin machine to remotely trigger a PrinterHub update on a Windows
10 laptop using OpenSSH and PowerShell.

The update flow must:

- verify Java 21 is installed on the Windows host
- stop the running PrinterHub process
- download a specific GitHub release asset
- extract the new Windows package
- preserve persisted data
- restart PrinterHub
- expose a short remote status command

---

## Architecture

### Repository

```text
printer-hub/
├── app source
├── docs/
│   └── install-remote.md
├── tools/
│   └── win/
│       ├── run.env.example
│       ├── u.ps1
│       ├── r.ps1
│       ├── s.ps1
│       ├── t.ps1
│       └── v.ps1
└── ops/
    ├── phu
    └── phv
```

### Windows laptop

```text
C:\ph\
├── app
├── data
├── log
├── rel
├── tmp
└── bin
```

### Linux admin machine

The Linux admin machine uses short helper commands to connect through SSH and
launch remote PowerShell scripts on the Windows host.

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
  * copied once to `C:\ph\data\run.env`

### Linux helper commands

* `phu`

  * remote update launcher

* `phv`

  * remote status launcher

---

## Release packaging assumption

The GitHub release tag uses:

```text
v<version>
````

The Windows asset file uses:

```text
printer-hub-<version>-windows.zip
```

Example:

```text
tag: v1.0.0
asset: printer-hub-1.0.0-windows.zip
```

---

## Runtime assumptions

The extracted Windows package currently contains:

```text
printerhub.bat
printer-hub.jar
```

Current launcher behavior: 

* Java command: `PRINTERHUB_JAVA` from `C:\ph\data\run.env`, otherwise `java`
* API port: `PRINTERHUB_API_PORT` from `C:\ph\data\run.env`, otherwise `18080`
* database file: `PRINTERHUB_DATABASE_FILE` from `C:\ph\data\run.env`, otherwise `printerhub.db`


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

Expected major version: `21`

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

Expected status: `Running`

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
New-Item -ItemType Directory -Force -Path C:\ph
New-Item -ItemType Directory -Force -Path C:\ph\app
New-Item -ItemType Directory -Force -Path C:\ph\data
New-Item -ItemType Directory -Force -Path C:\ph\log
New-Item -ItemType Directory -Force -Path C:\ph\rel
New-Item -ItemType Directory -Force -Path C:\ph\tmp
New-Item -ItemType Directory -Force -Path C:\ph\bin
```

---

### 5. Install the admin bootstrap files on the Windows laptop

Download:

```text
printer-hub-<version>-admin.zip
```

Extract the zip on the Windows laptop.

Copy the extracted PowerShell scripts into:

```text
C:\ph\bin\
```

Copy the extracted example environment file to:

```text
C:\ph\data\run.env
```

### 6. Review local runtime configuration

Open:

```text
C:\ph\data\run.env
```

Initial example content:

```text
PRINTERHUB_JAVA=
PRINTERHUB_DATABASE_FILE=printerhub.db
PRINTERHUB_API_PORT=18080
```

Adjust values if needed for the target Windows laptop.


### 7. Register the scheduled task once

Open a normal PowerShell window with the same Windows user that will run
PrinterHub and execute:

```powershell
C:\ph\bin\t.ps1

```




---
 
### 8. Optional first manual package install

If desired, install the first Windows release manually before remote updates are
used:

1. Download `printer-hub-<version>-windows.zip`
2. Extract it into `C:\ph\app`
3. Verify these files exist:

```text
C:\ph\app\printerhub.bat
C:\ph\app\printer-hub.jar
```

Then refresh the scheduled task:

```powershell
C:\ph\bin\t.ps1
```

Then test start:

```powershell
C:\ph\bin\r.ps1
```

Then test status:

```powershell
C:\ph\bin\v.ps1
```

---

## Linux admin machine setup

### 1. Create helper scripts

The repo contains:

* `ops/phu`
* `ops/phv`

Make them executable:

```bash
chmod +x ops/phu ops/phv
```

---

### 2. Configure remote host and user

The helper scripts currently use environment variables:

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

If you want less typing, export them once in the shell:

```bash
export PH_HOST=192.168.1.42
export PH_USER=myadmin
```

Then use:

```bash
ops/phv
ops/phu 1.0.0
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

## Remote update flow


The remote start path uses Windows Task Scheduler so that PrinterHub continues
running after the SSH session disconnects.



### Status check

From Linux:

```bash
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phv
```

This launches:

```text
C:\ph\bin\v.ps1
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
C:\ph\bin\u.ps1 -Version 1.0.0
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

## Data preservation rules

The updater must not replace persisted runtime data in:

```text
C:\ph\data\
```

In particular, database files must remain intact across updates.

The updater replaces only the application package in:

```text
C:\ph\app\
```
 