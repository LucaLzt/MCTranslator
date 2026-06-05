package com.lucalzt.mctranslator.infrastructure.outbound.ai;

import com.lucalzt.mctranslator.domain.exception.SessionFatalException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Componente de infraestructura encargado de gestionar un pool rotatorio de llaves de API (API Keys).
 * * Es completamente agnóstico al proveedor de IA (Groq, Gemini, etc.).
 * * Implementa seguridad de hilos (Thread-Safety) para dar soporte a llamadas paralelas de hilos virtuales.
 * * Maneja lógicas de tolerancia a fallos, descarte de llaves inválidas y rotación circular.
 */
public class ApiKeyPoolManager {

    private static final System.Logger LOGGER = System.getLogger(ApiKeyPoolManager.class.getName());
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final List<ApiKey> keys;
    private int currentIndex = 0;

    /**
     * Construye el administrador a partir de una lista cruda de llaves en texto plano.
     *
     * @param rawKeys Lista de cadenas de llaves inyectadas desde la configuración.
     * @throws IllegalArgumentException Si la lista proveída está vacía o es nula.
     */
    public ApiKeyPoolManager(List<String> rawKeys) {
        Objects.requireNonNull(rawKeys, "La lista de llaves de API de entrada no puede ser nula");
        if (rawKeys.isEmpty()) {
            throw new IllegalArgumentException("No se puede inicializar el ApiKeyPoolManager con una lista de llaves vacía");
        }

        this.keys = new ArrayList<>();
        for (String rawKey : rawKeys) {
            if (rawKey != null && !rawKey.isBlank()) {
                this.keys.add(new ApiKey(rawKey.trim()));
            }
        }

        if (this.keys.isEmpty()) {
            throw new IllegalArgumentException("Ninguna de las llaves proveídas en la lista es válida o almacena texto discernible");
        }
    }

    /**
     * Recupera de forma sincronizada la siguiente llave disponible en el pool siguiendo un orden circular.
     * Salta automáticamente las llaves marcadas como inválidas.
     *
     * @return Una cadena de texto correspondiente a una API Key activa.
     * @throws SessionFatalException Si todas las llaves del pool fueron dadas de baja por fallos.
     */
    public synchronized String next() {
        int totalPoolSize = keys.size();

        // Escaneo circular limitado a una vuelta completa para mitigar bucles infinitos en fallos masivos
        for (int i = 0; i < totalPoolSize; i++) {
            ApiKey currentKey = keys.get(currentIndex);

            // Avanzo el puntero circular para la próxima invocación
            currentIndex = (currentIndex + 1) % totalPoolSize;

            if (!currentKey.isInvalid()) {
                return currentKey.getValue();
            }
        }

        LOGGER.log(System.Logger.Level.ERROR, "Fallo catastrófico: Se ha intentado recuperar una llave pero todas las credenciales del pool están agotadas.");
        throw new SessionFatalException("Todas las llaves de API provistas en el pool han sido marcadas como inválidas debido a errores recurrentes");
    }

    /**
     * Invalida de forma permanente una llave del pool (por ejemplo, ante errores HTTP 401 o 403).
     *
     * @param keyValue La cadena de la llave a dar de baja.
     */
    public synchronized void markAsInvalid(String keyValue) {
        if (keyValue == null) return;

        for (ApiKey key : keys) {
            if (key.getValue().equals(keyValue)) {
                if (!key.isInvalid()) {
                    key.invalidate();
                    LOGGER.log(System.Logger.Level.WARNING, "Llave de API detectada como revocada o sin permisos. Marcada como INVÁLIDA permanentemente en el pool.");
                }
                return;
            }
        }
    }

    /**
     * Registra un fallo de ejecución para una llave específica (por ejemplo, ante errores HTTP 429 o timeouts).
     * Si la llave acumula el límite máximo de fallos consecutivos, es dada de baja automáticamente.
     *
     * @param keyValue La cadena de la llave que sufrió el desperfecto de infraestructura.
     */
    public synchronized void recordFailure(String keyValue) {
        if (keyValue == null) return;

        for (ApiKey key : keys) {
            if (key.getValue().equals(keyValue)) {
                key.incrementFailures();
                LOGGER.log(System.Logger.Level.DEBUG, "Fallo registrado para la llave de API. Historial consecutivo: {0}/{1}",
                        key.getConsecutiveFailures(), MAX_CONSECUTIVE_FAILURES);

                if (key.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
                    key.invalidate();
                    LOGGER.log(System.Logger.Level.WARNING, "La llave de API alcanzó el límite de {0} fallos consecutivos. Descartada del pool.",
                            MAX_CONSECUTIVE_FAILURES);
                }
                return;
            }
        }
    }

    /**
     * Resetea el contador de fallos consecutivos de una llave al registrarse una operación exitosa en red.
     * Permite amortizar errores de conexión intermitentes sin penalizar la credencial a largo plazo.
     *
     * @param keyValue La cadena de la llave que operó con éxito.
     */
    public synchronized void resetFailures(String keyValue) {
        if (keyValue == null) return;

        for (ApiKey key : keys) {
            if (key.getValue().equals(keyValue)) {
                key.resetFailures();
                return;
            }
        }
    }

    /**
     * Estructura de datos mutable interna encargada de monitorizar el ciclo de vida e historial
     * de una credencial específica dentro del pool de infraestructura.
     */
    private static class ApiKey {
        private final String value;
        private boolean invalid;
        private int consecutiveFailures;

        public ApiKey(String value) {
            this.value = value;
            this.invalid = false;
            this.consecutiveFailures = 0;
        }

        public String getValue() {
            return value;
        }

        public boolean isInvalid() {
            return invalid;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public void invalidate() {
            this.invalid = true;
        }

        public void incrementFailures() {
            this.consecutiveFailures++;
        }

        public void resetFailures() {
            this.consecutiveFailures = 0;
        }
    }
}
