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
│       ├── i.ps1
│       ├── u.ps1
│       ├── r.ps1
│       ├── s.ps1
│       └── v.ps1
└── ops/
    ├── phu
    └── phv
````

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

* `i.ps1`

  * one-time bootstrap
  * verify Java 21
  * create `C:\ph\...`
  * copy scripts to `C:\ph\bin`
  * create initial `run.env` if missing

* `u.ps1`

  * remote update entrypoint
  * download a specific release asset
  * stop PrinterHub
  * extract the package
  * replace app files
  * restart PrinterHub

* `r.ps1`

  * start PrinterHub

* `s.ps1`

  * stop PrinterHub

* `v.ps1`

  * status and health check

### Linux helper commands

* `phu`

  * remote update launcher

* `phv`

  * remote status launcher

---

## Release packaging assumption

The updater reuses the existing release asset naming pattern:

```text
printer-hub-<version>-windows.zip
```

Example:

```text
printer-hub-0.2.4.STEP_C_TEST-windows.zip
```

The Git tag and release name are expected to match the version string used by
the updater.

---

## Runtime assumptions

The extracted Windows package currently contains:

```text
printerhub.bat
printer-hub.jar
```

Current launcher behavior:

* default serial port: `COM3`
* default mode: `real`
* default API port: `18080`
* default database: `printerhub.db`

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

### 5. Copy the PowerShell scripts to the Windows laptop


### 5. Install the admin bootstrap scripts on the Windows laptop

Download:

```text
printer-hub-<version>-admin.zip
````

Extract the zip on the Windows laptop.

Create the target folder if needed:

```powershell
New-Item -ItemType Directory -Force -Path C:\ph\bin
```

Then copy the extracted `.ps1` files into:

```text
C:\ph\bin\
```

After that, run:

```powershell
C:\ph\bin\i.ps1
```


---

### 6. Run the bootstrap script once

Open PowerShell as Administrator:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
C:\ph\bin\i.ps1
```

This creates the final folder structure, copies the scripts again into
`C:\ph\bin`, and creates:

```text
C:\ph\data\run.env
```

Initial content:

```text
PRINTERHUB_DATABASE_FILE=printerhub.db
PRINTERHUB_API_PORT=18080
```

---

### 7. Optional first manual package install

If desired, install the first Windows release manually before remote updates are
used:

1. Download the release zip
2. Extract it into `C:\ph\app`
3. Verify these files exist:

```text
C:\ph\app\printerhub.bat
C:\ph\app\printer-hub.jar
```

Then test startup manually:

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
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phu 0.2.4.STEP_C_TEST
```

If you want less typing, export them once in the shell:

```bash
export PH_HOST=192.168.1.42
export PH_USER=myadmin
```

Then use:

```bash
ops/phv
ops/phu 0.2.4.STEP_C_TEST
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
PH_HOST=192.168.1.42 PH_USER=myadmin ops/phu 0.2.4.STEP_C_TEST
```

This launches:

```text
C:\ph\bin\u.ps1 -Version 0.2.4.STEP_C_TEST
```

on the Windows host.

The updater downloads:

```text
https://github.com/<owner>/<repo>/releases/download/<version>/printer-hub-<version>-windows.zip
```

Example asset:

```text
printer-hub-0.2.4.STEP_C_TEST-windows.zip
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

---

## Current limitations

This first emergency solution has known limits:

* no automatic rollback yet
* no service wrapper yet
* no packaged admin zip yet
* no latest-release auto-discovery yet
* no dynamic host discovery yet
* launcher parameters are still basic and should later move fully into `run.env`

This is acceptable for the current pre-`1.0.0` scope.

---

## Recommended future improvements

Planned later hardening:

* move serial port and mode into `run.env`
* add rollback-safe update behavior
* add optional latest-release mode
* add first-install bootstrap zip
* add Jenkins packaging for `printerhub-admin.zip`
* integrate remote administration into the future admin dashboard

---

## Files involved

### Repo files

* `docs/install-remote.md`
* `tools/win/i.ps1`
* `tools/win/u.ps1`
* `tools/win/r.ps1`
* `tools/win/s.ps1`
* `tools/win/v.ps1`
* `ops/phu`
* `ops/phv`

### Remote Windows files

* `C:\ph\app\printerhub.bat`
* `C:\ph\app\printer-hub.jar`
* `C:\ph\data\run.env`
* `C:\ph\log\start.log`
* `C:\ph\log\stop.log`
* `C:\ph\log\update.log`
* `C:\ph\log\printerhub-out.log`
* `C:\ph\log\printerhub-err.log`
* `C:\ph\bin\i.ps1`
* `C:\ph\bin\u.ps1`
* `C:\ph\bin\r.ps1`
* `C:\ph\bin\s.ps1`
* `C:\ph\bin\v.ps1`

### Local Linux files

* `ops/phu`
* `ops/phv`

---

````

---

## Additional repo to-do change

You also asked to “change to do list all the file(s) in remote and local”.

So yes, I would also add a short section in your existing roadmap or ops notes.

You can add this small block somewhere appropriate:

```md
## Remote Windows emergency administration bootstrap

Files introduced for the temporary pre-1.0.0 remote administration flow:

### Repo
- `docs/install-remote.md`
- `tools/win/i.ps1`
- `tools/win/u.ps1`
- `tools/win/r.ps1`
- `tools/win/s.ps1`
- `tools/win/v.ps1`
- `ops/phu`
- `ops/phv`

### Remote Windows host
- `C:\ph\app\`
- `C:\ph\data\`
- `C:\ph\log\`
- `C:\ph\rel\`
- `C:\ph\tmp\`
- `C:\ph\bin\`

### Local Linux admin machine
- `ops/phu`
- `ops/phv`
```