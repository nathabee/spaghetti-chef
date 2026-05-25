# Dashboard test


 

## Test steps (step H)


For a safe real-printer test file, use something with no heating and no extrusion, for example a short dwell/status file uploaded to SD:

```gcode
; SpaghettiChef Step H dashboard test
M117 SpaghettiChef test
G4 S60
M117 SpaghettiChef done
```
 
 


1. **Preflight: Printer Visibility**
   - Open dashboard.
   - Select the real printer.
   - Check Home shows:
     - printer enabled
     - real mode
     - live state changing from monitoring
     - temperature / last response updating
   - Go to History and confirm events can load.

2. **SD Card File Readiness**
   - Go to selected printer → **SD Card**.
   - Click **Refresh files**.
   - Confirm printer-side files appear.
   - Confirm one target is registered and **enabled**.
   - Disabled or deleted files should not appear later in the Print job file dropdown.

3. **Create PRINT_FILE Job**
   - Go to selected printer → **Print**.
   - Create a job with type `PRINT_FILE`.
   - Select the enabled printer-side SD file.
   - Expected:
     - job appears as `ASSIGNED`
     - Start button is enabled
     - Cancel button is enabled before terminal state

4. **Start Autonomous Print**
   - Click **Start**.
   - Expected:
     - dashboard returns quickly
     - job moves to `RUNNING`
     - Start becomes disabled
     - monitoring continues updating printer state
     - diagnostics/history can be loaded

5. **Cancel Running Print**
   - While job is `RUNNING`, click **Cancel**.
   - Expected:
     - job becomes `CANCELLED`
     - finished time appears
     - Cancel button becomes disabled
     - printer should stop the SD print
     - diagnostics should show the cancel/abort command path, likely `M524`
     - diagnostics should also show SD-print status verification, likely `M27`
     - if `M27` still reports SD printing, the job should not be marked `CANCELLED`

6. **History / Diagnostics Review**
   - On the job card, click **Load history**.
   - Click **Load diagnostics**.
   - Confirm you can see:
     - start workflow events
     - command / response evidence
     - cancel evidence
     - final `CANCELLED` state
     - no vague “no response” error if the printer actually responded

7. **Completion Test**
   - Start a very small safe SD file and let it finish.
   - Expected:
     - job eventually becomes `COMPLETED`
     - history shows terminal completion evidence
     - dashboard no longer treats it as active


## Test steps (step I)

1. **Job Controls By State**
   - Open selected printer → **Print**.
   - Confirm:
     - `ASSIGNED` jobs show Start and Cancel.
     - `RUNNING PRINT_FILE` jobs show Pause and Cancel.
     - `PAUSED PRINT_FILE` jobs show Resume and Cancel.
     - `COMPLETED`, `FAILED`, and `CANCELLED` jobs do not allow Cancel.
     - terminal `PRINT_FILE` jobs show Restart when the SD target still exists and is enabled.

2. **Pause / Resume**
   - Start a safe autonomous SD print.
   - Click **Pause** while it is `RUNNING`.
   - Expected:
     - job moves to `PAUSED`
     - history/diagnostics show `M25`
   - Click **Resume**.
   - Expected:
     - job returns to `RUNNING`
     - history/diagnostics show `M24`

3. **Verified Cancel**
   - Click **Cancel** while a print is `RUNNING` or `PAUSED`.
   - Expected:
     - diagnostics show `M524`
     - diagnostics also show `M27` status verification
     - job becomes `CANCELLED` only when the printer reports SD printing has stopped
     - if the printer still reports `SD printing`, the job remains non-terminal

4. **Terminal Restart**
   - Let a `PRINT_FILE` job complete or use a failed/cancelled print-file job.
   - Click **Restart**.
   - Expected:
     - a new `ASSIGNED` print-file job is created
     - the original terminal job remains terminal
     - history shows the restart relationship

5. **SD Target Filters**
   - Go to selected printer → **SD Card**.
   - Use the registered target filters:
     - availability: all / available / deleted
     - enabled: all / enabled / disabled
     - host link: all / linked / unlinked
   - Expected:
     - the registered target table changes without losing registrations
     - deleted or disabled targets remain reviewable when filters include them

6. **Upload Recovery**
   - After an interrupted SD upload session, use **Close upload session**.
   - Expected:
     - dashboard sends a numbered/checksummed `M29`
     - upload recovery result is visible in the SD Card upload status area

7. **Long Upload Progress / Locking CR**
   - Start a larger `.gcode` SD upload.
   - Expected:
     - dashboard shows upload progress based on counted lines and/or bytes
     - same-printer upload actions are disabled while upload is active
     - same-printer print-job start/control actions are disabled while upload is active
     - actions for other printers remain usable
     - success, failure, resend/retry exhaustion, and recovery-close evidence are visible in events or diagnostics

8. **Dashboard Polish**
   - Confirm dashboard date/time values are readable instead of raw ISO timestamps.
   - Confirm the browser tab shows the SpaghettiChef favicon.
