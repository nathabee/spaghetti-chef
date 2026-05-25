# Developer Guide

Short developer reference for everyday work on SpaghettiChef `0.1.x`.

Detailed setup and verification are documented in:

- `install.md`
- `test.md`
- `devops.md`

---

## Current architecture

SpaghettiChef `0.1.x` is a local runtime with:

- embedded HTTP API
- runtime-managed printer registry
- monitoring scheduler and monitoring tasks
- runtime state cache
- SQLite persistence
- embedded dashboard resources

The main verification path is simulation mode.  
Real hardware remains optional for manual checks.

---

## Main source areas

### `src/main/java/spaghettichef/Main.java`

Runtime entry point.

Responsibilities:

- read system properties
- create runtime services
- start the local runtime
- keep the process alive
- register shutdown handling

### `src/main/java/spaghettichef/api/`

Embedded REST API layer.

Main class:

- `RemoteApiServer`

Responsibilities:

- expose `/health`
- expose printer administration endpoints
- expose printer status endpoints
- expose dashboard resources
- translate runtime failures into controlled HTTP responses

### `src/main/java/spaghettichef/runtime/`

Runtime backbone.

Main classes:

- `SpaghettiChefRuntime`
- `PrinterRegistry`
- `PrinterRuntimeNode`
- `PrinterRuntimeNodeFactory`
- `PrinterRuntimeStateCache`

Responsibilities:

- load configured printers
- register and remove runtime nodes
- keep current runtime state per printer
- coordinate startup and shutdown

### `src/main/java/spaghettichef/monitoring/`

Monitoring layer.

Main classes:

- `PrinterMonitoringScheduler`
- `PrinterMonitoringTask`
- `MonitoringEventPolicy`

Responsibilities:

- schedule per-printer monitoring
- connect and poll printers
- classify responses into runtime state
- persist snapshots and events
- deduplicate repeated failure events

### `src/main/java/spaghettichef/persistence/`

SQLite persistence layer.

Main classes:

- `DatabaseConfig`
- `Database`
- `DatabaseInitializer`
- `PrinterConfigurationStore`
- `PrinterSnapshotStore`
- `PrinterEventStore`
- `MonitoringRules`

Responsibilities:

- initialize schema
- persist configured printers
- persist snapshots
- persist events
- apply snapshot persistence rules

### `src/main/java/spaghettichef/serial/`

Serial and simulation implementations.

Main classes:

- `SerialPortAdapter`
- `JSerialCommPortAdapter`
- `SimulatedPrinterPort`

### `src/main/java/spaghettichef/SerialConnection.java`

Real serial communication implementation used for `real` mode.

### `src/main/resources/dashboard/`

Embedded dashboard assets:

- `index.html`
- `dashboard.css`
- `dashboard.js`

### `src/test/java/`

Automated tests for:

- runtime
- monitoring
- persistence
- API
- serial communication
- simulation behavior

---

## Runtime model

At the moment, the main runtime flow is:

```text id="lt9i4o"
configured printer
-> runtime node
-> monitoring scheduler
-> monitoring task
-> snapshot/state update
-> API/dashboard exposure
-> persistence of snapshots and events
```

Main printer states:

```text id="mrw1ns"
DISCONNECTED
CONNECTING
IDLE
HEATING
PRINTING
ERROR
UNKNOWN
```

---

## Daily commands

### Compile

```bash id="j3poqf"
mvn clean compile
```

### Run tests

```bash id="v9ac1n"
mvn test
```

### Run full verification

```bash id="6l0a2f"
mvn clean verify
```

### Start local runtime

```bash id="p452q8"
mvn exec:java \
  -Dexec.mainClass="spaghettichef.Main" \
  -Dspaghettichef.api.port=8080 \
  -Dspaghettichef.monitoring.intervalSeconds=1 \
  -Dspaghettichef.databaseFile=spaghettichef.db
```

### Health check

```bash id="8oyuhs"
curl http://localhost:8080/health
```

### List printers

```bash id="c6m2rg"
curl http://localhost:8080/printers
```

### Add simulated printer

```bash id="tt9vjz"
curl -X POST http://localhost:8080/printers \
  -H "Content-Type: application/json" \
  -d '{
    "id": "printer-1",
    "displayName": "Developer Sim Printer",
    "portName": "SIM_PORT_1",
    "mode": "simulated",
    "enabled": true
  }'
```

### Read one printer

```bash id="96e0sh"
curl http://localhost:8080/printers/printer-1
curl http://localhost:8080/printers/printer-1/status
```

### Disable / enable printer

```bash id="dcjavd"
curl -X POST http://localhost:8080/printers/printer-1/disable
curl -X POST http://localhost:8080/printers/printer-1/enable
```

### Open dashboard

```text id="8n4j22"
http://localhost:8080/dashboard
```

---

## Database

Default database file:

```text id="ecvwn0"
spaghettichef.db
```

The schema is initialized automatically on runtime startup.

Current tables:

```text id="e9ex8n"
configured_printers
monitoring_rules
print_jobs
printer_events
printer_snapshots
```

Useful checks:

```bash id="83tpb1"
sqlite3 spaghettichef.db ".tables"
```

```bash id="2r6icy"
sqlite3 spaghettichef.db "select id,name,port_name,mode,enabled from configured_printers order by id;"
```

```bash id="l6seli"
sqlite3 spaghettichef.db "select printer_id,state,created_at from printer_snapshots order by id desc limit 10;"
```

```bash id="ep6bfe"
sqlite3 spaghettichef.db "select printer_id,event_type,message,created_at from printer_events order by id desc limit 10;"
```

Do not commit runtime database files.

---

## Before commit

Run:

```bash id="b4p7r4"
mvn clean verify
```

Do one local runtime smoke check:

```bash id="af1fyy"
curl http://localhost:8080/health
curl http://localhost:8080/printers
```

If working on runtime behavior, also verify:

* simulated printer creation
* monitoring state changes
* disable / enable flow
* dashboard load
* database contents when relevant

---

## Development notes

* use simulation mode for normal development
* use `real` mode only when validating actual serial communication
* prefer API/runtime verification over old one-shot CLI assumptions
* keep runtime state semantics aligned with the documented state machine
* keep persistence-visible behavior testable and deterministic
* keep Jenkins smoke scenarios aligned with the public runtime/API surface
 