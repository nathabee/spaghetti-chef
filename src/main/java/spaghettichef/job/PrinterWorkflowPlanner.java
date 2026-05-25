package spaghettichef.job;

import spaghettichef.OperationMessages;
import spaghettichef.config.PrinterProtocolDefaults;

import java.util.List;

public final class PrinterWorkflowPlanner {

    public PrinterWorkflowPlan plan(
            PrinterActionRequest request,
            PrinterActionMapper printerActionMapper
    ) {
        if (request == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("request"));
        }
        if (printerActionMapper == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ACTION_MAPPER_MUST_NOT_BE_NULL);
        }

        String mainWireCommand = printerActionMapper.toWireCommand(request);

        return switch (request.actionType()) {
            case READ_TEMPERATURE -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "read-temperature",
                                    mainWireCommand,
                                    true
                            )
                    )
            );

            case READ_POSITION -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "read-position",
                                    mainWireCommand,
                                    true
                            )
                    )
            );

            case READ_FIRMWARE_INFO -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "read-firmware-info",
                                    mainWireCommand,
                                    true
                            )
                    )
            );

            case HOME_AXES -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "validate-position-before-home",
                                    PrinterProtocolDefaults.COMMAND_READ_POSITION,
                                    true
                            ),
                            new PrinterWorkflowStep(
                                    "home-axes",
                                    mainWireCommand,
                                    true
                            )
                    )
            );

            case SET_NOZZLE_TEMPERATURE -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "validate-firmware-before-set-nozzle-temperature",
                                    PrinterProtocolDefaults.COMMAND_READ_FIRMWARE_INFO,
                                    true
                            ),
                            new PrinterWorkflowStep(
                                    "set-nozzle-temperature",
                                    mainWireCommand,
                                    true
                            )
                    )
            );

            case SET_BED_TEMPERATURE -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "validate-firmware-before-set-bed-temperature",
                                    PrinterProtocolDefaults.COMMAND_READ_FIRMWARE_INFO,
                                    true
                            ),
                            new PrinterWorkflowStep(
                                    "set-bed-temperature",
                                    mainWireCommand,
                                    true
                            )
                    )
            );

            case SET_FAN_SPEED -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "validate-firmware-before-set-fan-speed",
                                    PrinterProtocolDefaults.COMMAND_READ_FIRMWARE_INFO,
                                    true
                            ),
                            new PrinterWorkflowStep(
                                    "set-fan-speed",
                                    mainWireCommand,
                                    true
                            )
                    )
            );

            case TURN_FAN_OFF -> new PrinterWorkflowPlan(
                    request.actionType(),
                    List.of(
                            new PrinterWorkflowStep(
                                    "validate-firmware-before-turn-fan-off",
                                    PrinterProtocolDefaults.COMMAND_READ_FIRMWARE_INFO,
                                    true
                            ),
                            new PrinterWorkflowStep(
                                    "turn-fan-off",
                                    mainWireCommand,
                                    true
                            )
                    )
            );
        };
    }
}