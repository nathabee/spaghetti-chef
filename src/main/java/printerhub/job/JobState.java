package printerhub.job;

public enum JobState {
    CREATED,
    QUEUED,
    ASSIGNED,
    RUNNING,
    PAUSED,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED
}