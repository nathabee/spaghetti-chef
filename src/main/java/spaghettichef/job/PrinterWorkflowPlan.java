package spaghettichef.job;

import spaghettichef.OperationMessages;

import java.util.List;

public final class PrinterWorkflowPlan {

    private final PrinterActionType actionType;
    private final java.util.List<PrinterWorkflowStep> steps;

    public PrinterWorkflowPlan(
            PrinterActionType actionType,
            List<PrinterWorkflowStep> steps
    ) {
        if (actionType == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("actionType"));
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("steps"));
        }

        this.actionType = actionType;
        this.steps = List.copyOf(steps);
    }

    public PrinterActionType actionType() {
        return actionType;
    }

    public List<PrinterWorkflowStep> steps() {
        return steps;
    }
}