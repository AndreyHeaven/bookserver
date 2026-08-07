package com.example.bookserver.imports.fb2;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Fb2ParserTest {

    private final Fb2Parser parser = new Fb2Parser();

    @Test
    void returns_metadata_and_marks_warning_when_body_is_malformed_after_completed_title_info() throws Exception {
        Fb2Metadata metadata = parser.parse(fb2("""
                <body><section><p>Broken body</p></body>
                </FictionBook>
                """));

        assertThat(metadata.title()).isEqualTo("Повреждённая книга");
        assertThat(metadata.authors()).singleElement().satisfies(author -> {
            assertThat(author.lastName()).isEqualTo("Иванов");
            assertThat(author.firstName()).isEqualTo("Иван");
        });
        assertThat(metadata.genres()).containsExactly("sf");
        assertThat(metadata.lang()).isEqualTo("ru");
        assertThat(metadata.year()).isEqualTo(2024);
        assertThat(metadata.bodyMalformed()).isTrue();
    }

    @Test
    void does_not_mark_valid_document_as_malformed() throws Exception {
        Fb2Metadata metadata = parser.parse(fb2("""
                <body><section><p>Valid body</p></section></body>
                </FictionBook>
                """));

        assertThat(metadata.bodyMalformed()).isFalse();
    }

    @Test
    void rejects_malformed_metadata_before_title_info_is_complete() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("""
                <FictionBook><description><title-info>
                <book-title>Broken</title-info>
                </description></FictionBook>
                """.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(Exception.class);
    }

    private static ByteArrayInputStream fb2(String suffix) {
        String document = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description>
                    <title-info>
                      <genre>sf</genre>
                      <author><first-name>Иван</first-name><last-name>Иванов</last-name></author>
                      <book-title>Повреждённая книга</book-title>
                      <date>2024-01-01</date>
                      <lang>ru</lang>
                    </title-info>
                  </description>
                  %s
                """.formatted(suffix);
        return new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8));
    }
}
