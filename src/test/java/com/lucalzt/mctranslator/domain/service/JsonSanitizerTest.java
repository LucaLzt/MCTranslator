package com.lucalzt.mctranslator.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JsonSanitizer")
class JsonSanitizerTest {

    private final JsonSanitizer sanitizer = new JsonSanitizer();

    @Nested
    @DisplayName("Null input")
    class NullInput {

        @Test
        @DisplayName("should return empty JSON object when input is null")
        void returnsEmptyJsonObject_whenInputIsNull() {
            assertEquals("{}", sanitizer.sanitize(null));
        }
    }

    @Nested
    @DisplayName("Empty or blank input")
    class EmptyOrBlankInput {

        @Test
        @DisplayName("should return empty string when input is empty")
        void returnsEmptyString_whenInputIsEmpty() {
            assertEquals("", sanitizer.sanitize(""));
        }

        @Test
        @DisplayName("should return empty string when input is blank")
        void returnsEmptyString_whenInputIsBlank() {
            assertEquals("", sanitizer.sanitize("   "));
        }
    }

    @Nested
    @DisplayName("Markdown code block removal")
    class MarkdownCodeBlock {

        @Test
        @DisplayName("should extract JSON from ```json ... ``` block")
        void extractsJson_fromJsonMarkdownBlock() {
            String input = "```json\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should extract JSON from generic ``` ... ``` block")
        void extractsJson_fromGenericMarkdownBlock() {
            String input = "```\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should handle ```json without closing ```")
        void returnsInput_whenJsonMarkdownNotClosed() {
            String input = "```json";
            assertEquals("```json", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should handle ``` without closing ```")
        void returnsInput_whenGenericMarkdownNotClosed() {
            String input = "```";
            assertEquals("```", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should prefer ```json over generic ``` when both are present")
        void prefersJsonMarkdown_overGenericBlock() {
            String input = "```\nbefore\n```\n```json\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should extract only the first ```json block when multiple exist")
        void extractsOnlyFirstBlock_whenMultipleJsonBlocks() {
            String input = "```json\n{\"first\": true}\n```\n```json\n{\"second\": true}\n```";
            assertEquals("{\"first\": true}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should discard text after closing markdown")
        void discardsText_afterClosingMarkdown() {
            String input = "```json\n{\"key\": \"value\"}\n```\nsome trailing text";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }
    }

    @Nested
    @DisplayName("Control character removal")
    class ControlCharacters {

        @Test
        @DisplayName("should remove ASCII control characters (0x00-0x08, 0x0B, 0x0C, 0x0E-0x1F)")
        void removesAsciiControlCharacters() {
            String input = "{\"key\"\u0000:\u0001 \"value\"\u001F}";
            String expected = "{\"key\": \"value\"}";
            assertEquals(expected, sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should preserve tab (0x09), newline (0x0A) and carriage return (0x0D)")
        void preservesTabNewlineAndCarriageReturn() {
            String input = "{\"key\": \"line1\\nline2\",\"tab\": \"\\tindented\"}";
            assertEquals(input, sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should return empty string when input contains only control characters")
        void returnsEmpty_whenOnlyControlCharacters() {
            String input = "\u0000\u0001\u0002\u001F";
            assertEquals("", sanitizer.sanitize(input));
        }
    }

    @Nested
    @DisplayName("BOM character removal")
    class BomCharacter {

        @Test
        @DisplayName("should remove BOM character (U+FEFF)")
        void removesBomCharacter() {
            String input = "\uFEFF{\"key\": \"value\"}";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should return empty string when input contains only BOM")
        void returnsEmpty_whenOnlyBom() {
            assertEquals("", sanitizer.sanitize("\uFEFF"));
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should return same string when already clean")
        void returnsSameString_whenAlreadyClean() {
            String input = "{\"key\": \"value\"}";
            assertEquals(input, sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should trim surrounding whitespace")
        void trimsWhitespace() {
            assertEquals("{}", sanitizer.sanitize("  {}  "));
        }

        @Test
        @DisplayName("should handle full pipeline: markdown + control chars + BOM + whitespace")
        void handlesFullPipeline() {
            String input = "  \uFEFF```json\n\u0000{\"key\": \"value\"}\u0001\n```  ";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }
    }
}
