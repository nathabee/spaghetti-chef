

## start

mvn exec:java   -Dprinterhub.databaseFile="printerhub-real.db"   -Dprinterhub.api.port=18080   -Dexec.mainClass="printerhub.Main"
[INFO] Scanning for projects...
[INFO] 
[INFO] -----------------------< printerhub:printer-hub >-----------------------
[INFO] Building printer-hub 0.2.6
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- exec-maven-plugin:3.1.0:java (default-cli) @ printer-hub ---
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
[PrinterHub] Database initialized: printerhub-real.db
[PrinterHub] API server started on port 18080
[PrinterHub] Local runtime started
[PrinterHub] Health:   http://localhost:18080/health
[PrinterHub] Printers: http://localhost:18080/printers
[PrinterHub] Settings: http://localhost:18080/settings/monitoring


## tests


curl -s http://localhost:18080/health
curl -s http://localhost:18080/printers

curl -s http://localhost:18080/printers/p1/camera/settings
curl -s -X PUT http://localhost:18080/printers/p1/camera/settings \
  -H "Content-Type: application/json" \
  -d '{"enabled":true,"sourceType":"simulated","sourceValue":"default","captureIntervalSeconds":10,"retentionSnapshotCount":20}'

curl -s http://localhost:18080/printers/p1/camera/status
curl -s -X POST http://localhost:18080/printers/p1/camera/snapshot
curl -o camera-latest.jpg http://localhost:18080/printers/p1/camera/snapshot
curl -s http://localhost:18080/printers/p1/camera/events


## endpoints to be tested

GET  /printers/{id}/camera/status
GET  /printers/{id}/camera/settings
PUT  /printers/{id}/camera/settings
POST /printers/{id}/camera/snapshot
GET  /printers/{id}/camera/snapshot
GET  /printers/{id}/camera/events



GET  /printers/{id}/camera/settings
PUT  /printers/{id}/camera/settings
GET  /printers/{id}/camera/status
POST /printers/{id}/camera/snapshot
GET  /printers/{id}/camera/snapshot
GET  /printers/{id}/camera/events
404 for missing printer
404 for missing snapshot
