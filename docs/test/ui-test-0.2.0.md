## Test steps


mvn exec:java   -Dspaghettichef.databaseFile="spaghettichef.db"   -Dspaghettichef.api.port=18080   -Dexec.mainClass="spaghettichef.Main"
http://localhost:18080/dashboard



### 1. Dashboard loads

- open `/dashboard`
- verify printer cards load
- verify configured printer list loads
- verify monitoring rules load
- verify no `404` is shown for monitoring rules

### 2. Default monitoring values

If no custom values were saved before, verify:

- poll interval seconds = `5`
- snapshot minimum interval seconds = `30`
- temperature delta threshold = `1`
- event deduplication window seconds = `60`
- error persistence behavior = `DEDUPLICATED`

### 3. Add printers

- add one simulated printer in `sim` mode
- add one simulated printer in `sim-error` or `sim-timeout` mode
- add one real printer

Verify:

- all created printers appear in configured printer list
- all created printers appear on dashboard cards
- mode is shown correctly
- enabled state is shown correctly

### 4. Edit printer

- edit an existing printer
- change display name and/or port and/or mode

Verify:

- updated values are shown after save
- no duplicate printer is created
- same printer id is kept

### 5. Enable / disable printer

- disable one printer
- verify card stays visible
- verify disabled state is visually distinct from runtime failure
- enable the same printer again

Verify:

- state returns to active monitoring
- printer remains in configured list during the whole flow

### 6. Delete printer

- delete one printer

Verify:

- printer disappears from configured list
- printer disappears from dashboard cards

### 7. Card clarity

Verify each card clearly shows:

- printer name
- printer id
- port
- mode
- enabled / disabled
- state
- updated time
- last response
- error message when relevant

Verify visual distinction between:

- disabled printer
- failing/disconnected printer
- real printer
- simulated printer

### 8. Simulated runtime states

Check with simulated printers:

- `sim` -> should become `IDLE`
- `sim-error` -> should become `ERROR`
- `sim-timeout` -> should become failure state
- `sim-disconnected` -> should become failure state

Verify:

- failure cards show meaningful error information
- one bad printer does not block good printers

### 9. Monitoring rules save

Change monitoring rules and save them.

Verify after save:

- values remain visible in the form
- page refresh keeps the saved values
- runtime restart keeps the saved values

### 10. Poll interval effect

Change poll interval seconds to a clearly different value.

Example:

- set from `5` to `2`
- later set from `2` to `10`

Verify:

- `updatedAt` changes faster with lower interval
- `updatedAt` changes slower with higher interval

### 11. Real printer invalid port

Configure a real printer with an invalid or unavailable port.

Verify:

- card shows `ERROR`
- error message is meaningful
- other printers continue monitoring normally
 