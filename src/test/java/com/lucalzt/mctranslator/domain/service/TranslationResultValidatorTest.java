package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.exception.ChunkRetryableException;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("TranslationResultValidator")
class TranslationResultValidatorTest {

    private static final int CHUNK_ID = 42;
    private static final String KEY_FOO = "key.foo";
    private static final String KEY_BAR = "key.bar";
    private static final String KEY_BAZ = "key.baz";

    private TranslationResultValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TranslationResultValidator();
    }

    private static TranslationChunk chunk(Map<String, String> translations) {
        return new TranslationChunk(CHUNK_ID, translations);
    }

    private static TranslationResult result(Map<String, String> translations) {
        return new TranslationResult(CHUNK_ID, translations, Instant.now());
    }

    @Nested
    @DisplayName("Null arguments")
    class NullArguments {

        @Test
        @DisplayName("throws NullPointerException when chunk is null")
        void chunkIsNull_throwsNullPointerException() {
            var result = result(Map.of(KEY_FOO, "valor"));

            assertThrows(NullPointerException.class, () -> validator.validate(null, result));
        }

        @Test
        @DisplayName("throws NullPointerException when result is null")
        void resultIsNull_throwsNullPointerException() {
            var chunk = chunk(Map.of(KEY_FOO, "valor"));

            assertThrows(NullPointerException.class, () -> validator.validate(chunk, null));
        }
    }

    @Nested
    @DisplayName("Missing keys")
    class MissingKeys {

        @Test
        @DisplayName("throws ChunkRetryableException when a required key is missing")
        void keyMissingInResult_throwsChunkRetryableException() {
            var chunk = chunk(Map.of(KEY_FOO, "valor1", KEY_BAR, "valor2"));
            var result = result(Map.of(KEY_FOO, "valor1"));

            assertThrows(ChunkRetryableException.class, () -> validator.validate(chunk, result));
        }

        @Test
        @DisplayName("throws ChunkRetryableException when multiple keys are missing")
        void multipleKeysMissing_throwsOnFirstMissing() {
            var chunk = chunk(Map.of(KEY_FOO, "v1", KEY_BAR, "v2", KEY_BAZ, "v3"));
            var result = result(Map.of(KEY_BAZ, "v3"));

            assertThrows(ChunkRetryableException.class, () -> validator.validate(chunk, result));
        }
    }

    @Nested
    @DisplayName("Invalid translated values")
    class InvalidTranslatedValues {

        @Test
        @DisplayName("throws ChunkRetryableException when translated value is empty")
        void emptyStringValue_throwsChunkRetryableException() {
            var chunk = chunk(Map.of(KEY_FOO, "valor"));
            var result = result(Map.of(KEY_FOO, ""));

            assertThrows(ChunkRetryableException.class, () -> validator.validate(chunk, result));
        }

        @Test
        @DisplayName("throws ChunkRetryableException when translated value is blank")
        void blankValue_throwsChunkRetryableException() {
            var chunk = chunk(Map.of(KEY_FOO, "valor"));
            var result = result(Map.of(KEY_FOO, "   "));

            assertThrows(ChunkRetryableException.class, () -> validator.validate(chunk, result));
        }

        @Test
        @DisplayName("throws ChunkRetryableException when one of multiple values is invalid")
        void firstInvalidAmongMultiple_throwsChunkRetryableException() {
            var chunk = chunk(Map.of(KEY_FOO, "v1", KEY_BAR, "v2"));
            var result = result(Map.of(KEY_FOO, "v1", KEY_BAR, ""));

            assertThrows(ChunkRetryableException.class, () -> validator.validate(chunk, result));
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("does not throw when all keys are present and values are valid")
        void allKeysPresentAndValid_noException() {
            var chunk = chunk(Map.of(KEY_FOO, "valor1", KEY_BAR, "valor2"));
            var result = result(Map.of(KEY_FOO, "traducción1", KEY_BAR, "traducción2"));

            assertDoesNotThrow(() -> validator.validate(chunk, result));
        }

        @Test
        @DisplayName("tolerates extra keys in result not present in original chunk")
        void extraKeysInResult_noException() {
            var chunk = chunk(Map.of(KEY_FOO, "valor1"));
            var result = result(Map.of(KEY_FOO, "traducción1", KEY_BAR, "extra"));

            assertDoesNotThrow(() -> validator.validate(chunk, result));
        }

        @Test
        @DisplayName("does not throw with a single key-value pair")
        void singleEntry_noException() {
            var chunk = chunk(Map.of(KEY_FOO, "valor1"));
            var result = result(Map.of(KEY_FOO, "traducción1"));

            assertDoesNotThrow(() -> validator.validate(chunk, result));
        }
    }
}
