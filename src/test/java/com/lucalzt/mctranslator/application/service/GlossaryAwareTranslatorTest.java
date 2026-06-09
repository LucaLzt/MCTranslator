package com.lucalzt.mctranslator.application.service;

import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.infrastructure.outbound.glossary.InMemoryGlossaryAdapter;
import com.lucalzt.mctranslator.ports.outbound.GlossaryPort;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Glossary system tests")
class GlossaryAwareTranslatorTest {

    // -------------------------------------------------------------------------
    // GlossaryContextBuilder
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GlossaryContextBuilder")
    class GlossaryContextBuilderTest {

        private final GlossaryContextBuilder builder = new GlossaryContextBuilder();

        @Test
        @DisplayName("returns empty string when map is null")
        void buildContext_returnsEmpty_whenMapIsNull() {
            assertEquals("", builder.buildContext(null));
        }

        @Test
        @DisplayName("returns empty string when map is empty")
        void buildContext_returnsEmpty_whenMapIsEmpty() {
            assertEquals("", builder.buildContext(Map.of()));
        }

        @Test
        @DisplayName("returns formatted block with header and entries")
        void buildContext_returnsFormattedBlock_whenTermsExist() {
            String result = builder.buildContext(Map.of("Stone", "Piedra", "Iron", "Hierro"));

            assertTrue(result.contains("GLOSARIO APROBADO"));
            assertTrue(result.contains("Stone"));
            assertTrue(result.contains("Piedra"));
            assertTrue(result.contains("Iron"));
            assertTrue(result.contains("Hierro"));
        }

        @Test
        @DisplayName("includes all terms in the returned block")
        void buildContext_includesAllTermsInOrder() {
            Map<String, String> terms = Map.of(
                    "Stone", "Piedra",
                    "Iron", "Hierro",
                    "Diamond", "Diamante"
            );
            String result = builder.buildContext(terms);

            for (Map.Entry<String, String> e : terms.entrySet()) {
                assertTrue(result.contains(e.getKey()));
                assertTrue(result.contains(e.getValue()));
            }
        }
    }

    // -------------------------------------------------------------------------
    // GlossaryAwareTranslator
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GlossaryAwareTranslator")
    class GlossaryAwareTranslatorUnitTest {

        private TranslationEnginePort delegate;
        private GlossaryPort glossaryPort;
        private GlossaryContextBuilder contextBuilder;
        private GlossaryAwareTranslator translator;

        @BeforeEach
        void setUp() {
            delegate = mock(TranslationEnginePort.class);
            glossaryPort = mock(GlossaryPort.class);
            contextBuilder = mock(GlossaryContextBuilder.class);
            translator = new GlossaryAwareTranslator(delegate, glossaryPort, contextBuilder);
        }

        // -- extractCandidates (static, package-private) --

        @Test
        @DisplayName("extractCandidates returns empty set when map is null")
        void extractCandidates_returnsEmpty_whenMapIsNull() {
            assertTrue(GlossaryAwareTranslator.extractCandidates(null).isEmpty());
        }

        @Test
        @DisplayName("extractCandidates returns empty set when map is empty")
        void extractCandidates_returnsEmpty_whenMapIsEmpty() {
            assertTrue(GlossaryAwareTranslator.extractCandidates(Map.of()).isEmpty());
        }

        @Test
        @DisplayName("extractCandidates ignores words shorter than 3 characters")
        void extractCandidates_ignoresShortWords() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "Ox  Hi A"));
            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("extractCandidates ignores fully lowercase words")
        void extractCandidates_ignoresLowercaseWords() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "hello world test"));
            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("extractCandidates returns uppercase words of min length 3")
        void extractCandidates_extractsUpperCaseWordsOfMinLength() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "Stone Iron Ox Abc"));
            assertTrue(candidates.contains("Stone"));
            assertTrue(candidates.contains("Iron"));
            assertTrue(candidates.contains("Abc"));
            assertEquals(3, candidates.size());
        }

        @Test
        @DisplayName("extractCandidates splits on underscores and slashes")
        void extractCandidates_splitsOnUnderscoresAndSlashes() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "Stone_Sword/Iron_Shield"));
            assertTrue(candidates.contains("Stone"));
            assertTrue(candidates.contains("Sword"));
            assertTrue(candidates.contains("Iron"));
            assertTrue(candidates.contains("Shield"));
        }

        @Test
        @DisplayName("extractCandidates extracts from both keys and values")
        void extractCandidates_extractsFromBothKeysAndValues() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("KeyStone", "Hello World"));
            assertTrue(candidates.contains("KeyStone"));
        }

        @Test
        @DisplayName("extractCandidates excludes stopwords")
        void extractCandidates_excludesStopwords() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "When Always Only"));
            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("extractCandidates excludes ALL_CAPS words")
        void extractCandidates_excludesAllCaps() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "SHIFT CTRL ALT"));
            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("extractCandidates rejects words with trailing punctuation")
        void extractCandidates_rejectsWordsWithTrailingPunctuation() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "Testing... Valid: Search... Checking... Mode: Wait:"));
            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("extractCandidates applies all filters leaving only real mod terms")
        void extractCandidates_appliesAllFilters() {
            Set<String> candidates = GlossaryAwareTranslator.extractCandidates(
                    Map.of("key", "Stone When SHIFT Checking..."));
            assertEquals(1, candidates.size());
            assertTrue(candidates.contains("Stone"));
        }

        // -- translate flow --

        @Test
        @DisplayName("delegates to engine unchanged when no relevant terms")
        void delegatesToEngine_whenNoRelevantTerms() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "hello"));
            TranslationResult expected = new TranslationResult(0, Map.of("item.1", "hola"), Instant.now());

            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(chunk)).thenReturn(expected);

            TranslationResult result = translator.translate(chunk);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("enriches chunk with glossary context when relevant terms exist")
        void enrichesChunkWithGlossaryContext_whenTermsExist() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "Stone Sword"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of("Stone", "Piedra"));
            when(contextBuilder.buildContext(Map.of("Stone", "Piedra"))).thenReturn("GLOSARIO APROBADO\n  Stone -> Piedra\n");
            when(delegate.translate(argThat(c ->
                    c.translationsToTranslate().containsKey("__glossary_context__")
            ))).thenReturn(new TranslationResult(0, Map.of("item.1", "Espada de Piedra"), Instant.now()));

            TranslationResult result = translator.translate(chunk);

            assertEquals("Espada de Piedra", result.translatedTranslations().get("item.1"));
        }

        @Test
        @DisplayName("strips __glossary_context__ key from the final result")
        void stripsGlossaryContextKey_fromResult() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "Stone"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Piedra", "__glossary_context__", "should be removed"), Instant.now()));

            TranslationResult result = translator.translate(chunk);

            assertEquals(1, result.translatedTranslations().size());
            assertEquals("Piedra", result.translatedTranslations().get("item.1"));
        }

        @Test
        @DisplayName("saves new term when original value has exactly one capitalized word")
        void savesNewTerm_whenSingleCapitalizedWord() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "Stone"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Piedra"), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort).save("Stone", "Piedra");
        }

        @Test
        @DisplayName("does not save when original value has multiple capitalized words")
        void doesNotSave_whenMultipleCapitalizedWords() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "Stone Sword"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Espada de Piedra"), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort, never()).save(any(), any());
        }

        @Test
        @DisplayName("does not save when original value has only lowercase words")
        void doesNotSave_whenAllLowercase() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "hello world"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "hola mundo"), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort, never()).save(any(), any());
        }

        @Test
        @DisplayName("does not save when term is a stopword")
        void doesNotSave_whenTermIsStopword() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "When"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Cuando"), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort, never()).save(any(), any());
        }

        @Test
        @DisplayName("does not save when term is ALL_CAPS")
        void doesNotSave_whenTermIsAllCaps() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "SHIFT"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Mayús"), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort, never()).save(any(), any());
        }

        @Test
        @DisplayName("does not save when term has trailing punctuation")
        void doesNotSave_whenTermHasTrailingPunctuation() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "Checking..."));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Comprobando..."), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort, never()).save(any(), any());
        }

        @Test
        @DisplayName("does not save __glossary_context__ key as a glossary entry")
        void doesNotSave_glossaryContextKey() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "Stone"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of("Stone", "Piedra"));
            when(contextBuilder.buildContext(Map.of("Stone", "Piedra"))).thenReturn("context");
            when(delegate.translate(argThat(c ->
                    c.translationsToTranslate().containsKey("__glossary_context__")
            ))).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Piedra"), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort).save("Stone", "Piedra");
        }

        @Test
        @DisplayName("calls findRelevantTerms with extracted candidates")
        void callsFindRelevantTerms_withExtractedCandidates() {
            TranslationChunk chunk = new TranslationChunk(0, Map.of("item.1", "Stone Sword"));
            when(glossaryPort.findRelevantTerms(any())).thenReturn(Map.of());
            when(contextBuilder.buildContext(Map.of())).thenReturn("");
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Espada de Piedra"), Instant.now()));

            translator.translate(chunk);

            verify(glossaryPort).findRelevantTerms(argThat(candidates ->
                    candidates.contains("Stone") && candidates.contains("Sword")
            ));
        }
    }

    // -------------------------------------------------------------------------
    // Consistency - same term across chunks
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Consistency - mismo término en distintos chunks")
    class ConsistencyTest {

        private InMemoryGlossaryAdapter glossary;
        private GlossaryContextBuilder contextBuilder;
        private TranslationEnginePort delegate;
        private GlossaryAwareTranslator translator;

        @BeforeEach
        void setUp() {
            glossary = new InMemoryGlossaryAdapter();
            contextBuilder = new GlossaryContextBuilder();
            delegate = mock(TranslationEnginePort.class);
            translator = new GlossaryAwareTranslator(delegate, glossary, contextBuilder);
        }

        @Test
        @DisplayName("saves term from first chunk and makes it available for lookup in subsequent chunks")
        void savesTermFromFirstChunk_andMakesAvailableForLookup() {
            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Piedra"), Instant.now()));

            translator.translate(new TranslationChunk(0, Map.of("item.1", "Stone")));

            assertTrue(glossary.lookup("Stone").isPresent());
            assertEquals("Piedra", glossary.lookup("Stone").get());
        }

        @Test
        @DisplayName("preserves existing glossary entries across multiple chunks")
        void preservesEntriesAcrossMultipleChunks() {
            glossary.save("Stone", "Piedra");

            when(delegate.translate(any())).thenReturn(
                    new TranslationResult(0, Map.of("item.1", "Piedra"), Instant.now()));

            translator.translate(new TranslationChunk(0, Map.of("item.1", "Stone")));

            assertEquals("Piedra", glossary.lookup("Stone").get());
        }

        @Test
        @DisplayName("accumulates glossary entries from sequential chunks")
        void accumulatesEntriesFromSequentialChunks() {
            when(delegate.translate(any()))
                    .thenReturn(new TranslationResult(0, Map.of("item.1", "Piedra"), Instant.now()))
                    .thenReturn(new TranslationResult(1, Map.of("item.2", "Hierro"), Instant.now()));

            translator.translate(new TranslationChunk(0, Map.of("item.1", "Stone")));
            translator.translate(new TranslationChunk(1, Map.of("item.2", "Iron")));

            assertEquals("Piedra", glossary.lookup("Stone").get());
            assertEquals("Hierro", glossary.lookup("Iron").get());
        }

        @Test
        @DisplayName("uses glossary context from previously saved terms on subsequent chunks")
        void usesGlossaryContext_fromPreviouslySavedTerms() {
            glossary.save("Stone", "Piedra");

            when(delegate.translate(argThat(c ->
                    c.translationsToTranslate().containsKey("__glossary_context__")
            ))).thenReturn(new TranslationResult(0, Map.of("item.1", "Espada de Piedra"), Instant.now()));

            TranslationResult result = translator.translate(new TranslationChunk(0, Map.of("item.1", "Stone Sword")));

            assertEquals("Espada de Piedra", result.translatedTranslations().get("item.1"));
        }
    }
}
