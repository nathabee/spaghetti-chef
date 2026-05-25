package spaghettichef.job;

import spaghettichef.OperationMessages;

public final class PrinterWorkflowStep {

    private final String name;
    private final String wireCommand;
    private final boolean required;

    public PrinterWorkflowStep(
            String name,
            String wireCommand,
            boolean required
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("name"));
        }
        if (wireCommand == null || wireCommand.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("wireCommand"));
        }

        this.name = name.trim();
        this.wireCommand = wireCommand.trim();
        this.required = required;
    }

    public String name() {
        return name;
    }

    public String wireCommand() {
        return wireCommand;
    }

    public boolean required() {
        return required;
    }
}