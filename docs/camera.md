## 0.4.x CAMERA 


### overview

Best architecture:

```text
PrinterHub Runtime
  ├─ CameraCaptureService
  ├─ CameraDevice
  ├─ FrameAnalyzer
  ├─ SpaghettiDetectionService
  ├─ PrinterSafetyActionService
  └─ Dashboard JS view
```

For Linux/Windows compatibility, avoid OS-specific camera code at first. Use one abstraction:

```java
public interface CameraDevice {
    Optional<CameraFrame> captureFrame();
    boolean isAvailable();
    void close();
}
```

Then implementations:

```text
OpenCvCameraDevice        // later, real webcam
SnapshotFolderCameraDevice // test/dev mode
SimulatedCameraDevice      // CI tests
```

Why this is good:

* Linux camera: `/dev/video0`
* Windows camera: usually index `0`
* Java code stays the same
* dashboard only consumes REST endpoints
* CI can test without a real webcam

First version should not be “AI” yet. I would do:

```text
0.4.0 Camera monitoring foundation
- detect camera
- capture snapshot
- expose /printers/{id}/camera/snapshot
- show image in dashboard
- persist camera events

0.4.1 Spaghetti heuristic detection
- compare frames
- detect abnormal chaos/motion
- confidence score
- no automatic stop yet

0.4.2 Safety intervention
- if confidence high several times
- pause SD print with M25
- persist SPAGHETTI_DETECTED
```

For Java libraries, I would use **OpenCV Java bindings** or possibly **JavaCV**. But I would hide it behind `CameraDevice`, because native camera libraries are often the pain point on Windows.

Important: do **not** mix this into job upload/streaming. It should be a parallel monitoring task:

```text
job RUNNING
camera task observes
if anomaly → command service sends pause/abort
```

Dashboard JS only needs:

```text
GET /printers/{id}/camera/status
GET /printers/{id}/camera/snapshot
GET /printers/{id}/camera/anomalies
PUT /printers/{id}/camera/settings
```

So: Java backend for capture/detection, JS frontend for display, simulated camera for tests. That fits your current PrinterHub architecture very cleanly.


---


