package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ppiyaki.chat.service.SentenceBuffer;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SentenceBufferTest {

    private SentenceBuffer sentenceBuffer;

    @BeforeEach
    void setUp() {
        sentenceBuffer = new SentenceBuffer();
    }

    @Test
    @DisplayName("마침표로 문장이 완성되면 반환한다")
    void append_periodEnded_returnsSentence() {
        // given & when
        final Optional<String> result1 = sentenceBuffer.append("아스피린은 ");
        final Optional<String> result2 = sentenceBuffer.append("공복에 복용을 피하세요.");

        // then
        assertThat(result1).isEmpty();
        assertThat(result2).contains("아스피린은 공복에 복용을 피하세요.");
    }

    @Test
    @DisplayName("물음표로 문장이 끝나면 반환한다")
    void append_questionMarkEnded_returnsSentence() {
        // given & when
        final Optional<String> result = sentenceBuffer.append("부작용이 뭔가요?");

        // then
        assertThat(result).contains("부작용이 뭔가요?");
    }

    @Test
    @DisplayName("느낌표로 문장이 끝나면 반환한다")
    void append_exclamationMarkEnded_returnsSentence() {
        // given & when
        final Optional<String> result = sentenceBuffer.append("꼭 드세요!");

        // then
        assertThat(result).contains("꼭 드세요!");
    }

    @Test
    @DisplayName("flush 시 남은 텍스트를 반환한다")
    void flush_remainingText_returnsText() {
        // given
        sentenceBuffer.append("남은 텍스트");

        // when
        final Optional<String> result = sentenceBuffer.flush();

        // then
        assertThat(result).contains("남은 텍스트");
    }

    @Test
    @DisplayName("flush 시 비어있으면 empty를 반환한다")
    void flush_empty_returnsEmpty() {
        // when
        final Optional<String> result = sentenceBuffer.flush();

        // then
        assertThat(result).isEmpty();
    }
}
