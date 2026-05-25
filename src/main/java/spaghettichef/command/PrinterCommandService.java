package spaghettichef.command;

import spaghettichef.OperationMessages;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.persistence.PrinterEventStore;

public final class PrinterCommandService {

    private final PrinterEventStore eventStore;

    public PrinterCommandService(PrinterEventStore eventStore) {
        if (eventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }

        this.eventStore = eventStore;
    }

    public CommandExecutionResult execute(
            PrinterRuntimeNode node,
            String commandValue,
            Double targetTemperature
    ) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        AllowedPrinterCommand command = AllowedPrinterCommand.parse(commandValue);
        String wireCommand = command.buildWireCommand(targetTemperature);

        synchronized (node.printerPort()) {
            try {
                node.printerPort().connect();
                String response = node.printerPort().sendCommand(wireCommand);

                eventStore.record(
                        node.id(),
                        null,
                        OperationMessages.EVENT_COMMAND_EXECUTED,
                        OperationMessages.commandExecuted(wireCommand)
                );

                return new CommandExecutionResult(
                        node.id(),
                        command.name(),
                        wireCommand,
                        response
                );
            } catch (Exception exception) {
                String detail = OperationMessages.safeDetail(
                        exception.getMessage(),
                        OperationMessages.UNKNOWN_COMMAND_EXECUTION_ERROR
                );

                try {
                    eventStore.record(
                            node.id(),
                            null,
                            OperationMessages.EVENT_COMMAND_FAILED,
                            OperationMessages.commandFailed(wireCommand, detail)
                    );
                } catch (Exception persistException) {
                    System.err.println(OperationMessages.failedToPersistEvent(
                            node.id(),
                            OperationMessages.safeDetail(
                                    persistException.getMessage(),
                                    OperationMessages.FAILED_TO_SAVE_PRINTER_EVENT
                            )
                    ));
                }

                throw exception;
            } finally {
                try {
                    node.printerPort().disconnect();
                } catch (Exception exception) {
                    System.err.println(OperationMessages.failedToDisconnectPrinterNode(
                            node.id(),
                            OperationMessages.safeDetail(
                                    exception.getMessage(),
                                    OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR
                            )
                    ));
                }
            }
        }
    }

    public record CommandExecutionResult(
            String printerId,
            String command,
            String sentCommand,
            String response
    ) {
    }
}