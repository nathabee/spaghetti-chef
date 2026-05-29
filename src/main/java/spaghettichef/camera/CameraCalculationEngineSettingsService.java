package spaghettichef.camera;

import java.util.List;
import java.util.Optional;

import spaghettichef.persistence.CameraCalculationEngineSettings;
import spaghettichef.persistence.CameraCalculationEngineSettingsStore;

public final class CameraCalculationEngineSettingsService {

    private final CameraCalculationEngineSettingsStore store;
    private volatile List<CameraCalculationEngineSettings> cache;

    public CameraCalculationEngineSettingsService() {
        this(new CameraCalculationEngineSettingsStore());
    }

    public CameraCalculationEngineSettingsService(CameraCalculationEngineSettingsStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    public List<CameraCalculationEngineSettings> list() {
        List<CameraCalculationEngineSettings> current = cache;
        if (current == null) {
            current = refresh();
        }
        return current;
    }

    public List<CameraCalculationEngineSettings> refresh() {
        List<CameraCalculationEngineSettings> loaded = List.copyOf(store.findAll());
        cache = loaded;
        return loaded;
    }

    public Optional<CameraCalculationEngineSettings> findByEngineName(String engineName) {
        return list().stream()
                .filter(settings -> settings.engineName().equals(engineName))
                .findFirst();
    }

    public CameraCalculationEngineSettings save(CameraCalculationEngineSettings settings) {
        CameraCalculationEngineSettings saved = store.save(settings);
        refresh();
        return saved;
    }
}
