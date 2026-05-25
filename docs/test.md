# Test

This document describes manual verification for the `0.1.x` local runtime architecture.

> SpaghettiChef is currently in runtime migration.
> The focus is runtime startup, API responsiveness, background monitoring, dashboard configuration, and SQLite persistence.

---

## Build verification

```bash
mvn test
mvn clean verify
mvn clean package
```

Expected result:

```text
BUILD SUCCESS
```

---

## JUNIT Test


unit test are dexplained in test-unit.md


---

## Start local runtime

Recommended test port:

```bash
mvn exec:java \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.api.port=18081
```

Optional custom database file:

```bash
mvn exec:java \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.api.port=18081 \
  -Dspaghettichef.databaseFile=spaghettichef-test.db
```

Keep the runtime terminal open while running the checks below.

---

## API health check

```bash
curl -s http://localhost:18081/health
```

Expected result:

```json
{"status":"ok"}
```

---

## Printer list check

```bash
curl -s http://localhost:18081/printers | jq
```

Expected result:

```text
Configured printers are returned.
Each printer has an id, display name, port, mode, enabled flag, state, and updatedAt value.
```

Note:

```text
If the database is empty, no printers are returned until one is added through the API or dashboard.
```

---

## Add simulated printer

```bash
curl -s -X POST http://localhost:18081/printers \
  -H "Content-Type: application/json" \
  -d '{
    "id": "printer-4",
    "displayName": "Simulated Printer 4",
    "portName": "SIM_PORT_4",
    "mode": "simulated",
    "enabled": true
  }' | jq
```

Expected result:

```text
printer-4 is created.
The printer appears in GET /printers.
Monitoring starts automatically.
```

---

## Add real printer

Use the real USB serial port, for example:

```bash
curl -s -X POST http://localhost:18081/printers \
  -H "Content-Type: application/json" \
  -d '{
    "id": "real-1",
    "displayName": "Ender-3 V2 Neo",
    "portName": "/dev/ttyUSB0",
    "mode": "real",
    "enabled": true
  }' | jq
```

Expected result:

```text
real-1 is created.
If the printer is reachable, monitoring reads live M105 data.
If the printer is not reachable, only this printer enters ERROR state.
The API remains responsive.
```

---

## Background monitoring check

```bash
watch -n 2 'curl -s http://localhost:18081/printers | jq'
```

Expected result:

```text
updatedAt changes regularly for enabled printers.
Simulated printers show parsed hotend and bed temperature values.
Disabled printers stop refreshing.
One failing printer does not block other printers.
```

---

## Runtime configuration actions

Disable:

```bash
curl -s -X POST http://localhost:18081/printers/printer-4/disable | jq
```

Enable:

```bash
curl -s -X POST http://localhost:18081/printers/printer-4/enable | jq
```

Update:

```bash
curl -s -X PUT http://localhost:18081/printers/printer-4 \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Updated Simulated Printer 4",
    "portName": "SIM_PORT_4",
    "mode": "sim-error",
    "enabled": true
  }' | jq
```

Status:

```bash
curl -s http://localhost:18081/printers/printer-4/status | jq
```

Delete:

```bash
curl -s -X DELETE http://localhost:18081/printers/printer-4 | jq
```

Expected result:

```text
Configuration changes are reflected immediately in the runtime.
Updated printers are re-monitored with the new mode/port.
Deleted printers disappear from GET /printers.
```

---

## Dashboard check

Open:

```text
http://localhost:18081/dashboard
```

Expected result:

```text
Printer cards are visible.
The dashboard reads printer state from the API.
Adding, editing, enabling, disabling, and deleting printers works through the runtime API.
Normal dashboard reads do not poll printers directly.
```

---

## Persistence check

Check tables:

```bash
sqlite3 spaghettichef.db '.tables'
```

Expected tables include:

```text
configured_printers
printer_snapshots
printer_events
print_jobs
monitoring_rules
```

Check persisted snapshots:

```bash
sqlite3 spaghettichef.db \
  'select printer_id,state,created_at from printer_snapshots order by id desc limit 10;'
```

Check persisted events:

```bash
sqlite3 spaghettichef.db \
  'select printer_id,event_type,message,created_at from printer_events order by id desc limit 10;'
```

Check persisted configuration:

```bash
sqlite3 spaghettichef.db \
  'select id,name,port_name,mode,enabled from configured_printers order by id;'
```

Expected result:

```text
Polling snapshots are stored.
Printer events are stored.
Configured printers are stored.
```

---

## Restart persistence check

Stop runtime:

```text
Ctrl+C
```

Start again:

```bash
mvn exec:java \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.api.port=18081
```

Then:

```bash
curl -s http://localhost:18081/printers | jq
```

Expected result:

```text
Previously configured printers are loaded from SQLite.
No startup sim/real printer parameter is required.
Enabled printers resume monitoring automatically.
```

---

## API responsiveness check

Run while monitoring is active:

```bash
for i in {1..10}; do
  curl -s http://localhost:18081/health
  echo
  sleep 1
done
```

Expected result:

```text
The API returns {"status":"ok"} every time.
```

---

## Threading check

Find the Java process:

```bash
jps -l
```

Inspect Java threads:

```bash
jstack <PID> | grep -E "pool|HTTP|Scheduled" -n
```

Alternative:

```bash
ps -T -p <PID>
```

Expected result:

```text
Multiple Java threads are visible.
At least one thread pool belongs to the HTTP server.
At least one scheduled executor thread belongs to monitoring.
```

---

## Stop local runtime

Stop with:

```text
Ctrl+C
```

Optional port check:

```bash
ss -ltnp | grep 18081
```

Expected result:

```text
No process is listening on port 18081.
```

---

## Jenkins verification

Jenkins verification is restored in the runtime verification phase.

Expected later checks:

```text
branch checkout
Java and Maven environment
mvn clean verify
runtime startup
GET /health
GET /printers
dashboard resource check
background state refresh
SQLite persistence check
archived smoke-test outputs
```
 