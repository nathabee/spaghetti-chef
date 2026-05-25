package spaghettichef.command;

import spaghettichef.OperationMessages;
import spaghettichef.config.PrinterProtocolDefaults;
import spaghettichef.persistence.PrinterEventStore;
import spaghettichef.runtime.PrinterRuntimeNode;

public final class SdCardService {

    private final PrinterEventStore eventStore;
    private final SdCardFileParser parser;

    public SdCardService(PrinterEventStore eventStore) {
        this(eventStore, new SdCardFileParser());
    }

    public SdCardService(PrinterEventStore eventStore, SdCardFileParser parser) {
        if (eventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (parser == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("sdCardFileParser"));
        }

        this.eventStore = eventStore;
        this.parser = parser;
    }

    public SdCardFileList listFiles(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        synchronized (node.printerPort()) {
            try {
                node.printerPort().connect();
                String response = node.printerPort().sendCommand(PrinterProtocolDefaults.COMMAND_LIST_SD_FILES);
                SdCardFileList fileList = new SdCardFileList(node.id(), parser.parse(response), response);

                eventStore.record(
                        node.id(),
                        null,
                        OperationMessages.EVENT_SD_CARD_FILES_LISTED,
                        OperationMessages.sdCardFilesListed(fileList.files().size()));

                return fileList;
            } catch (Exception exception) {
                String detail = OperationMessages.safeDetail(
                        exception.getMessage(),
                        OperationMessages.FAILED_TO_LIST_SD_CARD_FILES);

                try {
                    eventStore.record(
                            node.id(),
                            null,
                            OperationMessages.EVENT_SD_CARD_FILE_LIST_FAILED,
                            OperationMessages.sdCardFileListFailed(detail));
                } catch (Exception persistException) {
                    System.err.println(OperationMessages.failedToPersistEvent(
                            node.id(),
                            OperationMessages.safeDetail(
                                    persistException.getMessage(),
                                    OperationMessages.FAILED_TO_SAVE_PRINTER_EVENT)));
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
                                    OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR)));
                }
            }
        }
    }

    public String deleteFile(PrinterRuntimeNode node, String firmwarePath) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_PATH_MUST_NOT_BE_BLANK);
        }

        String command = PrinterProtocolDefaults.COMMAND_DELETE_SD_FILE + " " + firmwarePath.trim();

        synchronized (node.printerPort()) {
            try {
                node.printerPort().connect();
                String response = node.printerPort().sendCommand(command);

                eventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_FILE_DELETE_REQUESTED",
                        "SD delete requested: " + firmwarePath.trim());

                return response;
            } catch (Exception exception) {
                String detail = OperationMessages.safeDetail(
                        exception.getMessage(),
                        "Failed to delete SD-card file.");

                try {
                    eventStore.record(
                            node.id(),
                            null,
                            "SD_CARD_FILE_DELETE_FAILED",
                            "SD delete failed for " + firmwarePath.trim() + ": " + detail);
                } catch (Exception persistException) {
                    System.err.println(OperationMessages.failedToPersistEvent(
                            node.id(),
                            OperationMessages.safeDetail(
                                    persistException.getMessage(),
                                    OperationMessages.FAILED_TO_SAVE_PRINTER_EVENT)));
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
                                    OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR)));
                }
            }
        }
    }
}
