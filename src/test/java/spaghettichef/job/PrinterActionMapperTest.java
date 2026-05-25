package spaghettichef.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrinterActionMapperTest {

    private final PrinterActionMapper mapper = new PrinterActionMapper();

    @Test
    void mapsReadTemperature() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.READ_TEMPERATURE, null, null)
        );

        assertEquals("M105", wireCommand);
    }

    @Test
    void mapsReadPosition() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.READ_POSITION, null, null)
        );

        assertEquals("M114", wireCommand);
    }

    @Test
    void mapsReadFirmwareInfo() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.READ_FIRMWARE_INFO, null, null)
        );

        assertEquals("M115", wireCommand);
    }

    @Test
    void mapsHomeAxes() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.HOME_AXES, null, null)
        );

        assertEquals("G28", wireCommand);
    }

    @Test
    void mapsSetNozzleTemperature() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.SET_NOZZLE_TEMPERATURE, 200.0, null)
        );

        assertEquals("M104 S200", wireCommand);
    }

    @Test
    void mapsSetBedTemperature() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.SET_BED_TEMPERATURE, 60.0, null)
        );

        assertEquals("M140 S60", wireCommand);
    }

    @Test
    void mapsSetFanSpeed() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.SET_FAN_SPEED, null, 255)
        );

        assertEquals("M106 S255", wireCommand);
    }

    @Test
    void mapsTurnFanOff() {
        String wireCommand = mapper.toWireCommand(
                new PrinterActionRequest(PrinterActionType.TURN_FAN_OFF, null, null)
        );

        assertEquals("M107", wireCommand);
    }

    @Test
    void rejectsMissingTemperatureForNozzleCommand() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toWireCommand(
                        new PrinterActionRequest(PrinterActionType.SET_NOZZLE_TEMPERATURE, null, null)
                )
        );

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsNegativeTemperature() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toWireCommand(
                        new PrinterActionRequest(PrinterActionType.SET_BED_TEMPERATURE, -5.0, null)
                )
        );

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsMissingFanSpeed() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toWireCommand(
                        new PrinterActionRequest(PrinterActionType.SET_FAN_SPEED, null, null)
                )
        );

        assertNotNull(exception.getMessage());
    }
}