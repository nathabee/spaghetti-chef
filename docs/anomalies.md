# PrinterHub Dashboard Test Ledger

This file tracks real-dashboard observations for the `0.2.3` work.

Legend:

* `OK` means the behavior was verified or is considered acceptable.
* `KO` means an anomaly, missing UX behavior, or firmware-specific issue still needs follow-up.
* `Step I` means the item has been moved into the roadmap as planned work.

---

## Step H Real-Printer Test Baseline

Use a small SD-card file with no heating and no extrusion for start/completion testing:

```gcode
; PrinterHub Step H dashboard test
M117 PrinterHub test
G4 S60
M117 PrinterHub done
```

Important:

* This file is acceptable for verifying autonomous start and completion.
* This file is not ideal for cancel testing, because `G4 S60` can keep Marlin in `busy: processing`.
* Cancel behavior remains a Step I area because the printer may require either repeated abort attempts or printer-side stop confirmation.

---

## OK Cases

### OK-1: Printer Visibility

Dashboard path:

```text
Dashboard -> select real printer -> Home
```

Expected / observed:

* printer is visible
* real mode is visible
* enabled/disabled state is visible
* monitoring updates printer state
* temperature and last response can update
* printer history can load from the selected-printer History page

Status: `OK`

---

### OK-2: SD Card Readiness

Dashboard path:

```text
Selected printer -> SD Card -> Refresh files
```

Expected / observed:

* printer-side SD files can be refreshed
* discovered printer-side files can be registered
* registered files can be enabled
* disabled or deleted targets should not appear in normal `PRINT_FILE` creation

Status: `OK`

Follow-up:

* Add filtering to the registered targets table by enabled/disabled/deleted/linked/unlinked status.

Roadmap: `0.2.3 Step I`

---

### OK-3: Create PRINT_FILE Job

Dashboard path:

```text
Selected printer -> Print -> Create job -> PRINT_FILE
```

Expected / observed:

* job can be created from an enabled registered printer-side SD target
* job appears as `ASSIGNED`
* Start button is available for assigned jobs

Status: `OK`

---

### OK-4: Start Autonomous Print

Dashboard path:

```text
Selected printer -> Print -> Start
```

Expected / observed:

* dashboard returns quickly after Start
* job moves to `RUNNING`
* workflow records file selection and `M24`
* monitoring continues while the printer owns the autonomous SD print
* execution diagnostics show command and response evidence

Status: `OK`

---

### OK-5: Completion

Dashboard path:

```text
Selected printer -> Print -> start small SD file -> let it finish
```

Expected / observed:

* job eventually reaches `COMPLETED` when firmware/monitoring makes completion observable
* completion evidence is reviewable in job history/diagnostics
* dashboard no longer treats the job as active

Status: `OK`

---

### OK-6: Printer Administration

Dashboard path:

```text
Settings / Printers
```

Expected / observed:

* simulated printer can be created
* simulated printer can be enabled
* monitoring works for simulated printer
* simulated printer can be disabled
* simulated printer can be deleted
* same administration flow works for a real printer

Status: `OK`

---

## KO / Current Anomalies

### KO-1: Dashboard Date/Time Is Not Operator-Friendly

Observed value:

```text
2026-05-08T05:40:35.861517049Z
```

Problem:

* raw ISO timestamps are accurate but hard to read in the dashboard
* dashboard should render a local, human-readable date/time

Expected:

* show concise local date/time in cards, history, and diagnostics
* keep full timestamp available only where useful

Roadmap: `0.2.3 Step I`

---

### KO-2: Print Page Job Card History/Diagnostics Loading

Observed:

* job result/response is visible from the selected-printer History page
* Print page job-card `Load history` / `Load diagnostics` did not reliably show the same information
* job-card delete control was shown but did not work in the observed flow

Expected:

* job cards in Print should load the same job events and execution diagnostics as History
* response, command, outcome, and failure detail should be visible directly from the Print job card
* delete should either work or not be shown

Roadmap: `0.2.3 Step I`

---

### KO-3: Cancel Is Real Printer Control, Not Only Job State

Observed before fixes:

* cancelling a running `PRINT_FILE` job marked the PrinterHub job as `CANCELLED`
* printer continued executing because the dashboard/API cancel path did not send the abort command

Current behavior after fixes:

* cancel path sends `M524`
* if printer returns only `busy`, PrinterHub retries briefly
* if printer returns stale output plus `ok`, cancel can be accepted
* if the printer stays busy for the retry window, the job can remain `RUNNING`
* a later cancel can succeed once the printer accepts `M524`, for example after the blocking print command has finished

Remaining Step I issue:

* dashboard needs an intermediate state such as `CANCEL_REQUESTED` or `WAITING_FOR_PRINTER_STOP`
* if firmware requires printer-side confirmation, dashboard should say so clearly
* cancel still needs dedicated Step I dashboard testing
* if cancel fails because the printer stays busy, the dashboard should not leave the operator confused by a plain `RUNNING` state
* Step I should decide whether to keep a visible cancel-request state, show a retry action, or automatically poll until the printer confirms stop/completion

Roadmap: `0.2.3 Step I`

---

### KO-4: SD Upload Recovery After Failed File Connection

Observed:

* failed or interrupted SD upload can leave the printer in an SD file-write session
* recovery may require sending a correctly numbered `M29`

Expected:

* if upload fails after `M28`, PrinterHub should attempt numbered `M29` before reporting failure
* dashboard/API should expose an operator recovery action to close a stuck upload session
* recovery should persist an event/diagnostic entry

Roadmap: `0.2.3 Step I`

---

### KO-5: USB-Only Power Versus Mains Power

Observed:

* some commands, such as `M105`, can work while printer is powered only over USB
* movement, heating, or other state-changing commands may be unsafe or firmware-hostile when mains power is off

Expected:

* detect mains-powered versus USB-only state if firmware exposes enough evidence
* if reliable detection is not possible, show conservative warnings
* gate dangerous commands/jobs when printer power state is not safe
* extend dashboard state beyond simple `IDLE` where needed

Roadmap: `0.2.3 Step I`

---

### KO-6: Fan Control Reports Success But Fan Does Not Change

Observed job: `TURN_FAN_OFF`

```text
turn-fan-off -> M107 | response=ok
```

Observed result:

* dashboard/job history reports success
* fan continues running loudly

Observed job: `SET_FAN_SPEED` with `0`

```text
set-fan-speed -> M106 S0 | response=ok
```

Observed result:

* dashboard/job history reports success
* fan sound does not change

Interpretation to verify:

* `M106` / `M107` may control only the part-cooling fan
* hotend, board, or power-supply fans may not respond to these commands
* PrinterHub currently proves command acceptance, not physical fan state

Expected:

* clarify dashboard wording for fan jobs
* add printer-specific notes/capabilities where possible
* decide whether fan-control follow-up verification is possible

Roadmap: `0.2.3 Step I`

---

### KO-7: Temperature Jobs Need Real-Printer Verification

Observed:

* `SET_NOZZLE_TEMPERATURE` and `SET_BED_TEMPERATURE` are not yet fully verified on the real printer through the dashboard

Expected:

* test both commands with safe target values
* verify command acceptance
* verify monitoring later reflects temperature movement where applicable
* document whether success means command accepted or target physically reached

Roadmap: `0.2.3 Step I`

---

### KO-8: Favicon / Browser Tab Icon Missing

Observed:

* dashboard browser tab does not show the PrinterHub icon

Expected:

* add served dashboard favicon, for example:

```text
src/main/resources/dashboard/favicon.svg
```

* add the link in the dashboard HTML head:

```html
<link rel="icon" href="/dashboard/favicon.svg" type="image/svg+xml">
```

Candidate source asset:

```text
docs/assets/media-src/printerhub-icon.svg
```

Roadmap: `0.2.3 Step I`
