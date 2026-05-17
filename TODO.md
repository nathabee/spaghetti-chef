 
### 0.2.6 — Runtime Recovery and Serial Device Robustness

status: planned

Purpose:

Harden PrinterHub for real USB-connected printer operation after the upload-monitoring and global runtime observability work from `0.2.4` and `0.2.5`.

This step focuses on recovery, serial-port reliability, clearer operator diagnostics, and live job synchronization from the global Monitoring workspace.

Goals:

* improve recovery after real USB disconnect/reconnect
* reduce problems caused by unstable `/dev/ttyUSB*` device names
* make real-printer administration safer
* improve operator visibility for serial-port failures
* add full live job-follow polling from the global Monitoring page
* fix minor dashboard/documentation anomalies found during `0.2.5`

---

#### 0.2.6.A — Serial disconnect classification and recovery behavior

Goal:

Make real-printer failures easier to classify and recover from.

Current problem:

A real printer can fail for different reasons that currently look too similar from the dashboard:

* USB cable unplugged
* printer powered off
* `/dev/ttyUSB0` changed to `/dev/ttyUSB1`
* configured port no longer exists
* serial port exists but cannot be opened
* command timeout during communication
* printer returns malformed or unexpected serial response

Required behavior:

* keep automatic retry behavior for recoverable monitoring failures
* do not permanently disable a printer because of one temporary communication failure
* classify connection failures more explicitly
* expose clear failure detail in API and dashboard

Suggested backend classifications:

```text
DEVICE_PATH_NOT_FOUND
DEVICE_PERMISSION_DENIED
DEVICE_BUSY
DEVICE_DISCONNECTED
CONNECT_TIMEOUT
READ_TIMEOUT
WRITE_FAILURE
PROTOCOL_ERROR
TEMPORARY_COMMUNICATION_FAILURE
UNKNOWN_SERIAL_FAILURE
````

Expected result:

* operators can distinguish between cable/power problems, invalid path configuration, and temporary printer communication failure
* monitoring can continue retrying recoverable failures
* dashboard and event history become more useful during real hardware debugging

Likely impacted files:

```text
src/main/java/printerhub/SerialConnection.java
src/main/java/printerhub/PrinterPort.java
src/main/java/printerhub/monitoring/PrinterMonitoringTask.java
src/main/java/printerhub/runtime/PrinterRuntimeStateCache.java
src/main/java/printerhub/PrinterSnapshot.java
src/main/java/printerhub/OperationMessages.java
src/main/java/printerhub/persistence/PrinterEventStore.java
src/main/java/printerhub/api/RemoteApiServer.java
```

Tests:

```text
src/test/java/printerhub/SerialConnectionTest.java
src/test/java/printerhub/monitoring/PrinterMonitoringTaskTest.java
src/test/java/printerhub/api/RemoteApiServerTest.java
```

---

#### 0.2.6.B — Stable serial path support and operator guidance

Goal:

Make real-printer configuration less fragile than `/dev/ttyUSB0`.

Problem:

Linux USB serial names such as:

```text
/dev/ttyUSB0
/dev/ttyUSB1
```

note in windows it is somethink like "COM3"

can change after unplug/replug, reboot, or connecting another USB serial device.

Preferred stable path style:

```text
/dev/serial/by-id/...
```

Required behavior:

* allow configured printer port names to use stable `/dev/serial/by-id/...` paths
* validate and display the configured path clearly
* document how operators should find and use stable serial paths
* optionally show a warning when a real printer uses an unstable `/dev/ttyUSB*` path

Dashboard/API behavior:

* keep the configured port visible
* show a clear warning for unstable paths
* show a clearer error when the configured path does not exist
* do not silently rewrite the configured path

Expected result:

* operators can configure real printers using stable serial paths
* reconnect/reboot scenarios become more predictable
* support/debug work becomes easier because port identity is explicit

Likely impacted files:

```text
src/main/java/printerhub/runtime/PrinterRuntimeNodeFactory.java
src/main/java/printerhub/persistence/PrinterConfigurationStore.java
src/main/java/printerhub/api/RemoteApiServer.java
src/main/resources/dashboard/views/settings.js
src/main/resources/dashboard/views/printer-info.js
src/main/resources/dashboard/views/printer-home.js
src/main/resources/dashboard/dashboard.css
docs/install.md
docs/quickstart.md
docs/dashboard.md
```

Tests:

```text
src/test/java/printerhub/api/RemoteApiServerTest.java
src/test/java/printerhub/persistence/PrinterConfigurationStoreTest.java
```

---

#### 0.2.6.C — Safer printer update behavior

Goal:

Fix unintended dashboard behavior when editing disabled printers.

Current anomaly:

When editing a disabled printer in the dashboard, the frontend can unintentionally send:

```javascript
enabled: true
```

or otherwise restore the printer to enabled state during update.

Required behavior:

* editing printer identity, display name, mode, or port must preserve the existing enabled/disabled state
* enabling/disabling remains an explicit operator action
* update behavior must not surprise the operator

Expected result:

* disabled printers stay disabled after edit
* printer update is safer during hardware troubleshooting

Likely impacted files:

```text
src/main/resources/dashboard/dashboard.js
src/main/resources/dashboard/views/settings.js
src/main/java/printerhub/api/RemoteApiServer.java
```

Tests:

```text
src/test/java/printerhub/api/RemoteApiServerTest.java
```

Manual verification:

```text
1. create printer
2. disable printer
3. edit printer name or port
4. save
5. verify printer remains disabled
```

---

#### 0.2.6.D — Full live job synchronization from Monitoring

Goal:

Complete the job-follow behavior introduced in `0.2.5`.

Current behavior:

The Monitoring page can follow a job by jumping to the selected printer Print page, but it does not yet start a dedicated live job-status poller.

Required behavior:

* add a job synchronization mode similar to upload synchronization
* allow Monitoring → job follow to:

  * select the printer
  * open selected printer / Print
  * start live polling for that job
* refresh job state, events, and execution steps while synchronization is active
* provide a Stop sync control
* preserve expanded diagnostics/history panels where possible

Suggested polling endpoints:

```text
GET /jobs/{id}
GET /jobs/{id}/events
GET /jobs/{id}/execution-steps
```

or, if simpler for now:

```text
GET /jobs
GET /jobs/{id}/events
GET /jobs/{id}/execution-steps
```

Recommended first implementation:

* poll every 2–3 seconds (nothing hard coded we have config files, mostly manage in settinsg frontend dashboard also)
* stop automatically when job reaches a terminal state:

```text
COMPLETED
FAILED
CANCELLED
```

* keep manual refresh available
* do not refresh unrelated full dashboard state more than necessary

Expected result:

* from the global Monitoring page, an operator can follow a running job live
* selected-printer Print becomes a real job observation page, not only a static job list
* job follow reaches parity with upload follow

Likely impacted files:

```text
src/main/resources/dashboard/dashboard.js
src/main/resources/dashboard/state.js
src/main/resources/dashboard/views/monitoring.js
src/main/resources/dashboard/views/printer-print.js
src/main/resources/dashboard/components/job-card.js
src/main/resources/dashboard/api.js
src/main/resources/dashboard/dashboard.css
```

Possible backend impact:

```text
src/main/java/printerhub/api/RemoteApiServer.java
```

Only needed if `GET /jobs/{id}` is missing, incomplete, or does not return enough detail.

Tests:

```text
src/test/java/printerhub/api/RemoteApiServerTest.java
```

Manual verification:

```text
1. create or select a running job
2. open Monitoring
3. click follow/synchronize for that job
4. verify dashboard opens selected printer / Print
5. verify job state updates without pressing Refresh now
6. verify events/diagnostics update while sync is active
7. verify sync stops or becomes inactive after terminal job state
```

---

#### 0.2.6.E — Documentation and media path cleanup

Goal:

Clean minor documentation inconsistencies that became visible during dashboard updates.

Known anomalies:

* README banner and screenshot paths should point to final published media locations, not temporary source-media paths
* dashboard screenshots should not duplicate the same image for different sections
* dashboard documentation should mention Monitoring and synchronization behavior consistently

Required behavior:

* keep README global and concise
* keep detailed dashboard behavior in `docs/dashboard.md`
* keep version and roadmap details in `docs/version.md` and `docs/roadmap.md`
* avoid turning README into another roadmap/status file

Likely impacted files:

```text
README.md
docs/dashboard.md
docs/install.md
docs/quickstart.md
docs/assets/...
```

Expected result:

* README remains a stable project overview
* dashboard documentation reflects the current UI structure
* media paths are consistent and publishable

---

## Expected result for 0.2.6

After this step, PrinterHub should be more reliable for real hardware operation.

Expected improvements:

* real printers recover more predictably after USB reconnect scenarios
* operators can understand whether a failure is caused by cable disconnect, changed port path, invalid configuration, permission problem, timeout, or printer response problem
* stable serial paths such as `/dev/serial/by-id/...` are supported and documented
* disabled printers are not accidentally re-enabled by editing their configuration
* global Monitoring can follow both uploads and jobs live
* dashboard documentation and media paths are cleaned up without turning README into a status log

Non-goals:

* no central/VPS orchestration yet
* no slicer integration
* no full production print supervision
* no automatic remapping from `/dev/ttyUSB*` to `/dev/serial/by-id/...`
* no automatic persistent tuning of runtime batch size back into operator settings
 