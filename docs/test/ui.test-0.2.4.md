# performance


## Analyse file upload (step B)

goal test impact default settings :

**Per-mode sleep strategies:**

| Mode | Activity Sleep | Idle Sleep | Quiet Period | Timeout | Logic |
|------|---|---|---|---|---|
| COMMAND_RESPONSE | 50ms | 25ms | 200ms | 60s | Tolerant—response may be slow |
| FILE_STREAMING | 1ms | 1ms | 10ms | 5s | Aggressive—response is fast |
 

* file 6 millions bytes:

### upload : FILE_STREAMING | activity 5ms | idle 5ms | quiet 50ms | timeout 5s | 


**Per-mode sleep strategies:**

| Mode | Activity Sleep | Idle Sleep | Quiet Period | Timeout | Logic |
|------|---|---|---|---|---|
 
| FILE_STREAMING | 1ms | 1ms | 10ms | 5s | Aggressive—response is fast |
 



SD Card
Files on creality enders 2 neo V3
Printer-side files reported by the firmware, plus the registered printable targets used by print jobs.

Refresh files
Upload in progress
Uploading smallbox.gcode: 52130/198626 confirmed lines (26%).

RUNNING
Progress
52130/198626 confirmed lines

Performance
2526 bytes/sec
 
50.5 lines/sec
 
21% efficiency
Time remaining
48m 20s
Transfer quality
100%


result: 

=> 6 mega bytes in 68 minutes

### Throughput is 20% of the baurate expected throughput

* the next improvements are:

- Use a transfer mode that allows multiple lines in flight
- Reduce ack frequency on the printer side
- Increase baud rate if printer supports it
- Use a firmware upload command optimized for SD transfer
- Use a larger buffer/window instead of strict line-by-line handshaking


* first we will make more trace in step C



## step C : trace to check bottleneck

### first trace to look at bottleneck

activate the trace (can also be done with dashboard)
curl -X PUT http://localhost:8080/settings/monitoring \
  -H "Content-Type: application/json" \
  -d '{"debugWireTracingEnabled": true}'

  Step 2: Upload the Test File
Upload the bottleneck-test.gcode file to a printer. The console output will show:

Wire traffic: Every command sent to the printer and response received
Timing breakdown: Per-line timing showing where time is spent
Step 3: Analyze the Output
Look for patterns in the timing data:

High "send+wait" times → Printer firmware processing bottleneck
Frequent resend requests → Communication reliability issues
Long checksum times → Java-side processing bottleneck
Consistent delays → SD card write laten

### enhanced trace to analyse bottle neck :

New Timing Output Format

[SpaghettiChef] SD upload timing p1 line=2 attempt=1 checksum=0ms send+wait=24ms wall=24ms sleeps=12 total=24ms
What Each Metric Means
checksum (0ms): Time spent calculating the checksum (should be ~0ms)
send+wait (24ms): NanoTime difference from send to response (precise CPU time)
wall (24ms): Wall clock time from send to response (real elapsed time)
sleeps (12): Number of Thread.sleep() calls during the response wait
total (24ms): Total time for this line including checksum
Key Insights This Reveals
If send+wait ≈ wall and sleeps is low (1-3):
Printer is the bottleneck: The printer responds quickly, minimal sleeping needed
If wall > send+wait and sleeps is high (10-20):
Java sleep cycles are the issue: Code is sleeping/waking multiple times waiting for printer
For your 15ms responses, if sleeps=15, it means the code slept 15 times × 1ms each
If sleeps = wall (approximately):
Pure sleep time: The entire wall clock time was spent sleeping, waiting for printer
What This Tells Us About Your Upload
Looking at your current output, the responses are 15-25ms. With 1ms sleep cycles, if the printer truly responds in 15ms, you'd expect:

sleeps ≈ 15 (15 × 1ms = 15ms)
wall ≈ 15ms
send+wait ≈ 15ms
If sleeps is much higher than the response time, it means the polling loop is inefficient.


Result bottleneck test :
[SpaghettiChef] SD upload timing p1 line=21 attempt=1 checksum=0ms send+wait=18ms wall=19ms sleeps=14 total=18ms
[SpaghettiChef] SD upload wire p1 -> N22 M84*47 [attempt 1]
[SpaghettiChef] SD upload wire p1 <- ok
[SpaghettiChef] SD upload timing p1 line=22 attempt=1 checksum=0ms send+wait=16ms wall=16ms sleeps=14 total=16ms
[SpaghettiChef] SD upload wire p1 -> N23 M29*41 [attempt 1]
[SpaghettiChef] SD upload wire p1 <- Done saving file. | ok
[SpaghettiChef] SD upload timing p1 line=23 attempt=1 checksum=0ms send+wait=25ms wall=26ms sleeps=21 total=25ms
[SpaghettiChef] SD upload wire p1 -> N24 M20*39 [attempt 1]
[SpaghettiChef] SD upload wire p1 <- Begin file list | CE3E3V~1.GCO 3900960 | CE3E3V~2.GCO 6517601 | LEFTLE~1.GCO 596930 | CE3E3V~3.GCO 596930 | TEST-023.GCO 52 | CE3E3V2_.GCO 6281784 | SMALLBOX.GCO 6281784 | BOTTLENE.GCO 294 | TEST8SIN.GCO 9 | TEST8EMP.GCO 9 | TEST9.GCO 73 | TESTHEXA.GCO 230 | FLEXIL~1.GCO 7501934 | HORIZO~2.GCO 676556 | /ORIGIN/RABBIT~1.GCO 5137185 | /ORIGIN/BOAT~1.GCO 3759599 | End file list | ok
[SpaghettiChef] SD upload timing p1 line=24 attempt=1 checksum=0ms send+wait=55ms wall=56ms sleeps=39 total=55ms


=> this means , the bottleneck is the printer because the printer must awake 14 to 21 times waiting for a response for the printer, this is where goes the time...


### step C - after bottleneck analyse , we notice the printer is the bottleneck


action to solve the bottleneck :

- Use a transfer mode that allows multiple lines in flight

=> in settings we will handle a new parameter "maxLinesInFlight"