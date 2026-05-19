

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


## test endpoint with real image 


### config the printer

let say aou use printer p1 and you want to put the files in ./data/camera/p1


then firs we need to init :
curl -X PUT http://localhost:18080/printers/p1/camera/settings   -H "Content-Type: application/json"   -d '{
    "enabled": true,
    "sourceType": "snapshot-folder",
    "sourceValue": "data/camera/p1",
    "captureIntervalSeconds": 10,
    "retentionSnapshotCount": 20,
    "analysisEnabled": true
  }'






So the dashboard shows the latest snapshot through:

```text
GET /printers/{printerId}/camera/snapshot
```

and the capture button triggers: (ie click on the button capture image in the dashboard)

```text
POST /printers/{printerId}/camera/snapshot
```

## Where to put the JPEG for testing

For your current setup, use the **snapshot-folder source**.

Put your test picture here:

```text
data/camera/p1/latest.jpg
```

or, if your source folder is somewhere else, put it in the folder configured as the camera `sourceValue`.

Example expected folder:

```text
data/camera/p1/
├── latest.jpg
├── previous.jpg
└── archive/
```

Then configure camera settings for printer `p1` like this:

```bash
curl -X PUT http://localhost:8080/printers/p1/camera/settings \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "sourceType": "snapshot-folder",
    "sourceValue": "data/camera/p1",
    "captureIntervalSeconds": 10,
    "retentionSnapshotCount": 20,
    "analysisEnabled": true
  }'
```

Important: the dashboard form currently does **not** send `analysisEnabled`. Your backend supports it, but `cameraSettingsPayload(...)` only sends:

```js
enabled
sourceType
sourceValue
captureIntervalSeconds
retentionSnapshotCount
```

So for now, enable analysis with `curl`, not from the dashboard.

## How to test with real pictures

Use this sequence:

```bash
# 1. Put first image
cp /path/to/first-test.jpg data/camera/p1/latest.jpg

# 2. Trigger first capture
curl -X POST http://localhost:8080/printers/p1/camera/snapshot

# 3. Put second image
cp /path/to/second-test.jpg data/camera/p1/latest.jpg

# 4. Trigger second capture
curl -X POST http://localhost:8080/printers/p1/camera/snapshot
```

After the second capture, the backend should have enough data for:

```text
previous.jpg
latest.jpg
delta.jpg
```

and it should write camera events like:

```text
CAMERA_FRAME_CAPTURED
CAMERA_ANALYSIS_COMPLETED
SPAGHETTI_SUSPECTED
```

depending on the delta/confidence.

## What you can see in the dashboard now

You can already see:

```text
Latest snapshot
Recent camera activity
```

So if analysis runs, you should see events in the camera event card. The event list already renders `eventType` and `message`. 

But you **cannot yet see `delta.jpg` from the dashboard**, because we have not added:

```text
GET /printers/{printerId}/camera/delta
```

and the dashboard does not yet render a delta image.

## Practical answer

For now:

```text
Put your real JPEG in:
data/camera/p1/latest.jpg
```

Then go to:

```text
Dashboard -> selected printer -> Camera -> Capture now
```

You should see the latest snapshot update.

To test the algorithm, enable `analysisEnabled` with `curl`, then do two captures with two different images. You will see the analysis result in the **camera events**. The next useful step is to add a small `delta.jpg` endpoint and show it beside the latest snapshot.

