package com.lucalzt.mctranslator.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SnbtSanitizer")
class SnbtSanitizerTest {

    private final SnbtSanitizer sanitizer = new SnbtSanitizer();

    @Nested
    @DisplayName("Null input")
    class NullInput {

        @Test
        @DisplayName("should return empty SNBT object when input is null")
        void returnsEmptySnbtObject_whenInputIsNull() {
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
        @DisplayName("should extract from ```snbt ... ``` block")
        void extractsFromSnbtMarkdownBlock() {
            String input = "```snbt\n{\n  \"key\": \"value\"\n}\n```";
            assertEquals("{\n  \"key\": \"value\"\n}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should extract from generic ``` ... ``` block")
        void extractsFromGenericMarkdownBlock() {
            String input = "```\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should handle ```snbt without closing ```")
        void returnsInput_whenSnbtMarkdownNotClosed() {
            String input = "```snbt";
            assertEquals("```snbt", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should handle ``` without closing ```")
        void returnsInput_whenGenericMarkdownNotClosed() {
            String input = "```";
            assertEquals("```", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should prefer ```snbt over generic ``` when both are present")
        void prefersSnbtMarkdown_overGenericBlock() {
            String input = "```\nbefore\n```\n```snbt\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should extract only the first ```snbt block when multiple exist")
        void extractsOnlyFirstBlock_whenMultipleSnbtBlocks() {
            String input = "```snbt\n{\"first\": true}\n```\n```snbt\n{\"second\": true}\n```";
            assertEquals("{\"first\": true}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should discard text after closing markdown")
        void discardsText_afterClosingMarkdown() {
            String input = "```snbt\n{\"key\": \"value\"}\n```\nsome trailing text";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should not strip SNBT when it looks like inline code")
        void preservesSnbt_whenWrappedInBackticks() {
            String input = "```snbt\n{\n  chapter.1.title: \"Hello\"\n  quest.2.desc: [\"line1\", \"line2\"]\n}\n```";
            String result = sanitizer.sanitize(input);
            assertEquals("{\n  chapter.1.title: \"Hello\"\n  quest.2.desc: [\"line1\", \"line2\"]\n}", result);
        }
    }

    @Nested
    @DisplayName("Control character removal")
    class ControlCharacters {

        @Test
        @DisplayName("should remove ASCII control characters")
        void removesAsciiControlCharacters() {
            String input = "{\u0000key\u0001:\u0002 \"value\"\u001F}";
            assertEquals("{key: \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should preserve tab, newline and carriage return")
        void preservesTabNewlineAndCarriageReturn() {
            String input = "{\"key\": \"line1\\nline2\"}";
            assertEquals(input, sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should return empty when only control characters")
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
        @DisplayName("should return empty when only BOM")
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
            String input = "{\n  chapter.1.title: \"Hello\"\n}";
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
            String input = "  \uFEFF```snbt\n\u0000{\"key\": \"value\"}\u0001\n```  ";
            assertEquals("{\"key\": \"value\"}", sanitizer.sanitize(input));
        }

        @Test
        @DisplayName("should preserve SNBT features: unquoted keys with dots, arrays, optional commas")
        void preservesSnbtFeatures() {
            String input = "{\n  chapter.1.title: \"Chapter Title\"\n  quest.2.desc: [\"line1\", \"line2\"]\n  quest.3.subtitle: \"Subtitle\"\n}";
            assertEquals(input, sanitizer.sanitize(input));
        }
    }
}
