# SpaghettiChef Dashboard User Manual

SpaghettiChef includes a local browser dashboard served by the same runtime as the REST API.

Open:

```text
http://localhost:<api-port>/dashboard
```

Typical local example:

```text
http://localhost:18080/dashboard
```

If SpaghettiChef runs on another machine, replace `localhost` with that machine name or IP address:

```text
http://192.168.178.39:18080/dashboard
```

The dashboard uses relative API calls, so it follows the port and host you used to open it.

---

## What The Dashboard Is For

Use the dashboard to:

* see all configured printers
* select one printer for detailed work
* watch live printer status
* create and control print jobs
* upload or register G-code files
* manage printer-side SD-card file targets
* inspect job history and execution diagnostics
* configure camera capture
* start and review camera analysis sessions
* tune monitoring, serial transfer, security, and storage settings

The dashboard is an operator tool. It is not meant to expose every internal detail; deeper implementation notes belong in [specification.md](specification.md).

---

## Navigation

The left navigation has two levels.

Farm-level pages:

```text
Farm Home
Jobs
Monitoring
History
Settings
```

Selected-printer pages:

```text
Home
Print
SD Card
Camera
Prepare
Control
Info
History
```

On small laptop screens the navigation scrolls, so all menu entries should remain reachable.

---

## First Use

1. Open `Settings`.
2. Add or check your printer configuration.
3. Enable the printer.
4. Select the printer from the navigation.
5. Open `Home` or `Monitoring` to confirm SpaghettiChef can poll it.

For a simulated printer, use a simulated port/mode. For a real Marlin printer, use the serial port shown by your operating system.

---

## Farm Home

Farm Home is the quick overview.

It shows:

* configured printers
* enabled/disabled state
* current runtime state
* hotend and bed temperatures
* last response
* current error
* last update time

Use this page to choose the printer you want to operate.

---

## Jobs

The Jobs page shows recent jobs across the local SpaghettiChef runtime.

Common actions:

* create a job
* start a job
* pause or resume a running print-file job
* cancel a job
* restart a terminal print-file job
* delete old jobs
* expand job events
* expand execution steps

Typical states:

```text
CREATED
QUEUED
RUNNING
PAUSED
COMPLETED
FAILED
CANCELLED
```

If a job fails, open the execution diagnostics. They show the workflow step, command, response, and failure detail.

---

## Monitoring

Monitoring is the cross-printer live view.

It is useful when you want to know:

* which printers are active
* which jobs are running
* whether an SD upload is active
* whether upload recovery or adaptive batching is happening
* where to jump next

Use follow actions to jump from a global item to the selected-printer page that owns it.

---

## Settings

Settings contains configuration that affects the local runtime.

Available settings include:

* monitoring rules
* serial transfer settings
* print-file storage directory
* security settings and roles
* printer create/update/delete
* printer enable/disable

The app version is shown in Settings so you can confirm which release is running after a remote update.

---

## Selected Printer: Home

Home is the selected printer summary.

Use it to confirm:

* the selected printer is correct
* SpaghettiChef can poll the printer
* temperatures and state are updating
* recent events look normal

---

## Selected Printer: Print

Print is focused on print-file jobs for the selected printer.

Use this page to:

* see jobs for the selected printer
* create a print workflow
* start, pause, resume, cancel, or restart jobs
* inspect job state and recent history

---

## Selected Printer: SD Card

The SD Card page is for printer-side SD file management.

Use it to:

* list files from the printer firmware
* register a known printer-side file
* enable or disable a registered target
* upload a host-side print file to the printer SD card
* watch upload progress and transfer diagnostics
* close an interrupted upload session when recovery is needed

The upload controls are designed to use SpaghettiChef's controlled command flow. They are not raw serial access.

---

## Selected Printer: Camera

The Camera page is for snapshots and visual analysis.

It contains:

* camera status
* latest snapshot preview
* camera settings
* recent camera events
* camera analysis sessions
* camera analysis sample table

### Camera Settings

Important fields:

| Field | Meaning | Example |
| --- | --- | --- |
| Enable camera monitoring | Allows capture for this printer | checked |
| Source type | Camera backend | `ffmpeg` |
| Source value | Backend-specific input | `video=PC-LM1E Camera` |
| Storage directory | Base folder for snapshots | `camera` or `C:\spaghettichef\data\camera` |
| ffmpeg command | ffmpeg executable | `ffmpeg` |
| ffmpeg input format | OS/backend input type | `dshow` on Windows, `v4l2` on Linux |
| ffmpeg video size | Capture size | `640x480` |
| ffmpeg timeout ms | Capture timeout | `5000` |
| ffmpeg JPEG quality | ffmpeg JPEG quality | `3` |

The storage directory is a base directory. SpaghettiChef adds the printer id automatically.

Example with Windows package database:

```text
database file:       C:\spaghettichef\data\spaghettichef.db
storage setting:    camera
printer id:         p1
actual image folder: C:\spaghettichef\data\camera\p1
```

Example explicit Windows storage:

```text
C:\spaghettichef\data\camera
```

SpaghettiChef still adds the printer id:

```text
C:\spaghettichef\data\camera\p1
```

### Windows ffmpeg Camera Example

List Windows cameras:

```powershell
ffmpeg -list_devices true -f dshow -i dummy
```

If the listed camera name is:

```text
PC-LM1E Camera
```

Use these dashboard values:

```text
Source type:          ffmpeg
Source value:         video=PC-LM1E Camera
ffmpeg input format:  dshow
ffmpeg video size:    640x480
Storage directory:    camera
```

Do not type extra quotes around the source value.

### Linux ffmpeg Camera Example

Common Linux values:

```text
Source type:          ffmpeg
Source value:         /dev/video0
ffmpeg input format:  v4l2
ffmpeg video size:    640x480
Storage directory:    camera
```

### Capture Now

Use `Capture now` to test the camera.

Expected result:

* latest image updates
* the persisted source snapshot is written under the configured camera root
* `latest.jpg` is refreshed as a volatile preview file
* a camera event is recorded

If capture fails, read the camera event message. It should include the ffmpeg exit detail.

### Analysis Sessions

A camera analysis session records analysis samples independently from print jobs.

Use:

* `Start` to begin a session
* `Sample` to capture one analysis point
* `Stop` to end the session

The sample table shows the values that will later become graph series:

| Column | Meaning |
| --- | --- |
| Captured at | Future graph X axis |
| Analyzed at | When analysis finished |
| State | Good or suspicious |
| Confidence | Spaghetti detector confidence |
| Delta score | Visual delta between previous and latest frame |
| Changed pixels | Ratio of changed pixels |
| Average delta | Average pixel difference |
| Reason codes | Detector/analysis reasons |
| Message | Human-readable result |
| Latest snapshot | Persisted source snapshot used as the newer frame |
| Previous snapshot | Persisted source snapshot used as the older frame |
| Delta snapshot | Persisted generated delta image path |

This table is intentionally plain. It is the debugging view before graph polish.

---

## Selected Printer: Control

Control contains manual command tools and selected-printer operational helpers.

Today it includes:

* read temperature
* read position
* read firmware
* latest command result
* camera analysis session review

The same camera analysis card appears here so you can sample or review camera analysis while staying near printer control actions.

---

## Selected Printer: Info

Info is the read-only technical page for the selected printer.

Use it to inspect printer identity and configuration without changing anything.

---

## Selected Printer: History

History shows selected-printer persisted activity.

Use it for:

* printer events
* job events
* camera events
* safety intervention events
* execution diagnostics

---

## Troubleshooting

### Dashboard Opens But Actions Fail

Check:

* the runtime is still running
* the browser URL uses the correct host and port
* the selected printer exists
* the current role has permission for the action

### Camera Capture Fails On Windows With dshow

If the event says:

```text
Malformed dshow input string
```

check the source value. It usually needs `video=`.

Correct:

```text
video=PC-LM1E Camera
```

Wrong:

```text
PC-LM1E Camera
```

### Camera Files Are Written To The Wrong Folder

Check the camera `Storage directory` field.

Recommended package install value:

```text
camera
```

This resolves relative to the folder containing the configured database file.

### Version Looks Wrong After Update

Open Settings and check the displayed version. If it is not the version you installed, restart the runtime and check the update logs.

---

## Related Documents

* [rest-api.md](rest-api.md) for API endpoints
* [specification.md](specification.md) for implementation architecture
* [camera.md](camera.md) for deeper camera notes and ffmpeg examples
* [install-remote.md](install-remote.md) for remote installation workflow
