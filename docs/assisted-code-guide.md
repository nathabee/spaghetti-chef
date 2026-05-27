
## 
- open vscode on develop not on many branche
- show tree and explain which file to be used

##
RuntimeDefaults.java       -> numeric/default runtime values
OperationMessages.java     -> event names, error keys, fixed message vocabulary
other java like :
CameraCaptureService.java  -> orchestration only, no duplicated event constants



## requirements purge 


Below is a cleaned requirements list you can put in a file and later compare against the roadmap/TODO to detect drift.

````markdown
# Camera Data Purge and Deletion Requirements

## 1. Scope

The camera data cleanup feature must distinguish between three related but separate concepts:

1. **Purge snapshots**
2. **Delete camera jobs**
3. **Delete delta files**

These concepts may be exposed separately in the dashboard, but concept 1 and concept 3 can also be offered as sub-actions inside concept 2.

---

## 2. Concept 1 — Purge Snapshots

### 2.1 Goal

Snapshot purge removes only selected physical snapshot files from the filesystem.

It must not delete the snapshot rows from the database.

Instead, database rows must remain and be updated to indicate that the physical file was deleted.

### 2.2 Purge Modes

Snapshot purge must support two modes:

#### Manual purge

Manual purge is triggered on demand by the user.

Possible dashboard location:

```text
Picture admin menu
````

Manual purge may allow editable parameters that temporarily override the stored camera settings for that purge operation only.

#### Automatic purge

Automatic purge is executed while the camera job is running, at the end of the camera job, or during scheduled capture after safe thresholds.

Automatic purge uses the default purge parameters from camera settings.

### 2.3 Default Purge Settings

The camera settings must contain these default values:

```text
purgeAutomatically = false
purgeRetentionFrequency = 5
retentionSnapshotCount = 20
```

Meaning:

* automatic purge is disabled by default
* when purging, keep every 5th snapshot as a timeline sample
* always keep the last 20 snapshots

### 2.4 Retention Rule

For a camera job with snapshots `000001` through `001000`, using:

```text
purgeRetentionFrequency = 5
retentionSnapshotCount = 20
```

the purge must keep:

```text
000001, 000006, 000011, ..., 000976, 000981, 000982, ..., 001000
```

The exact interpretation is:

* keep the first snapshot
* keep every Nth snapshot based on the configured retention frequency
* keep the latest configured number of snapshots
* delete only the physical files of all other snapshots

### 2.5 Filesystem Impact

Snapshot purge must remove physical snapshot files from disk for non-kept snapshots.

It must not remove delta files.

It must not remove job data.

It must not remove events.

It must not remove calculation results.

### 2.6 Database Impact

Snapshot purge must keep all snapshot database rows.

For every purged physical snapshot file, the corresponding database row must be updated with deletion metadata, for example:

```text
fileDeleted = true
deletedAt = <timestamp>
deletionReason = <reason>
```

The exact column names may follow the existing project naming style, but the semantic requirement is mandatory.

### 2.7 Dashboard Impact

Deleted snapshot files must no longer be offered as viewable/replayable physical images.

In particular:

```text
Replay time card must not offer deleted snapshots for viewing.
```

The dashboard may still show that a snapshot existed historically, but it must not try to display a deleted physical file.

---

## 3. Concept 2 — Delete Camera Jobs

### 3.1 Goal

Deleting a camera job is a stronger cleanup operation than snapshot purge.

It may remove physical files and database data associated with a selected camera job.

### 3.2 Dashboard Location

Job deletion should be available on demand in the picture admin menu.

The user must be able to select what should be removed.

### 3.3 Selectable Removal Options

When deleting or cleaning a camera job, the dashboard must allow selectable removal options such as:

* purge snapshots only
* remove all snapshot files
* remove all delta files
* remove selected delta files by delta id
* delete calculations associated with the job id
* delete calculations associated with selected delta ids
* delete events associated with the job
* delete database entries associated with the selected cleanup scope
* delete all data associated with the job

### 3.4 Database Impact

Unlike snapshot purge, job deletion may remove database entries.

If the user selects full job deletion, all data associated with the job should be removed from the database.

This can include:

* camera job metadata
* snapshot metadata
* delta metadata
* calculation results
* events
* any job-linked analysis records

### 3.5 Filesystem Impact

Depending on selected options, job deletion may remove:

* all source snapshot files for the job
* all delta files for the job
* selected delta files for the job
* other generated files associated with the job

### 3.6 Relationship to Snapshot Purge

Snapshot purge can be offered as a sub-action of delete job.

Example:

```text
Delete job
  - only purge snapshot files
  - remove all snapshot files
  - remove all delta files
  - remove all database data
```

---

## 4. Concept 3 — Delete Delta Files

### 4.1 Goal

Delta deletion removes delta files and their associated database entries.

This operation is based on:

```text
jobId
deltaId
```

### 4.2 Filesystem Impact

Deleting delta files must remove the selected delta files from disk.

### 4.3 Database Impact

Deleting delta files must remove or mark deleted the associated delta entries in the database.

For this concept, the intended behavior is stronger than snapshot purge:

```text
all files on disk and associated database entries are deleted
```

### 4.4 Relationship to Delete Job

Delta deletion may be implemented as a standalone admin action.

It may also be implemented as a sub-choice inside the delete job menu.

Example:

```text
Delete job
  - delete selected delta ids
  - delete all delta files for this job
```

---

## 5. Manual Purge Example

Settings:

```text
purgeAutomatically = false
purgeRetentionFrequency = 5
retentionSnapshotCount = 20
```

Camera job creates snapshots:

```text
000001 through 001000
```

Before manual purge:

```text
1000 physical source snapshots
999 delta frames, if generated
```

After admin runs purge for that camera job:

```text
kept snapshots:
  000001, 000006, 000011, ..., 000976, 000981, 000982, ..., 001000

deleted physical snapshots:
  all non-kept snapshots

delta files:
  unchanged

camera_snapshot_entries:
  rows remain
  deleted files are marked as fileDeleted/deletedAt/deletionReason
```

---

## 6. Automatic Purge Example

Settings:

```text
purgeAutomatically = true
purgeRetentionFrequency = 5
retentionSnapshotCount = 20
```

Camera job creates snapshots:

```text
000001 through 001000
```

At the end of the job, or during scheduled capture after safe thresholds, automatic purge must preserve the same retention rule as manual purge.

Kept snapshots:

```text
000001, 000006, 000011, ..., 000976, 000981, 000982, ..., 001000
```

The implementation may run automatic purge incrementally, but it must preserve the same final retention behavior.

---

## 7. Non-Drift Rules

The roadmap/TODO must not confuse the three cleanup concepts.

### Snapshot purge is not job deletion

Snapshot purge:

* deletes only selected physical snapshot files
* keeps database rows
* marks snapshot files as deleted
* does not touch delta files
* does not delete calculations
* does not delete job events

### Job deletion is stronger than snapshot purge

Job deletion:

* can delete physical files
* can delete database entries
* can delete job-linked calculations
* can delete job-linked events
* should be selectable by cleanup scope

### Delta deletion is not snapshot purge

Delta deletion:

* operates on job id and delta id
* deletes delta files from disk
* deletes associated delta database entries
* may be standalone or part of job deletion

---

## 8. Dashboard Requirements

The picture admin menu should provide cleanup actions with clear distinction between:

```text
Purge snapshot files
Delete delta files
Delete camera job data
```

The UI must make destructive scope explicit before execution.

The replay/time-card UI must not offer deleted physical snapshot files as viewable images.

---

## 9. Safety Requirements

Destructive operations must be explicit.

The dashboard should avoid ambiguous labels like:

```text
Delete
Clean
Remove old data
```

without explaining the exact scope.

Preferred action labels:

```text
Purge snapshot files only
Delete selected delta files
Delete all delta files for this job
Delete camera job and associated data
Delete job events
Delete job calculations
```

---

## 10. Implementation Acceptance Criteria

The feature is acceptable only if the following are true:

* Manual snapshot purge keeps the correct retention set.
* Automatic snapshot purge keeps the same retention set as manual purge.
* Snapshot purge does not delete snapshot database rows.
* Snapshot purge marks deleted physical files in the database.
* Snapshot purge does not delete delta files.
* Snapshot purge does not delete job events.
* Snapshot purge does not delete calculation results.
* Deleted snapshot files are not offered for replay/viewing in the dashboard.
* Delete job allows selectable cleanup scope.
* Full job deletion removes all selected filesystem and database data associated with the job.
* Delta deletion can target a job id and delta id.
* Delta deletion removes selected delta files from disk.
* Delta deletion removes associated delta database entries.

```

The important correction is this: **purge** and **delete** must not be used as synonyms in the roadmap. Purge is a partial filesystem cleanup with database traceability. Delete is a destructive cleanup of selected job/delta data, potentially including database rows.
```

