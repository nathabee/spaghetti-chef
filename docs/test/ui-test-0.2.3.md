# Dashboard test


 

## Test steps (step H)


For a safe real-printer test file, use something with no heating and no extrusion, for example a short dwell/status file uploaded to SD:

```gcode
; PrinterHub Step H dashboard test
M117 PrinterHub test
G4 S60
M117 PrinterHub done
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

