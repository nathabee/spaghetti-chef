package spaghettichef.camera;

import java.util.Optional;

public interface CameraDevice extends AutoCloseable {

    Optional<CameraFrame> captureFrame();

    boolean isAvailable();

    String describe();

    @Override
    void close();
}