package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ppiyaki.chat.service.SentenceBuffer;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SentenceBufferTest {

    private SentenceBuffer sentenceBuffer;

    @BeforeEach
    void setUp() {
        sentenceBuffer = new SentenceBuffer();
    }

    @Test
    void append_문장이_완성되면_반환한다() {
        // given & when
        final Optional<String> result1 = sentenceBuffer.append("아스피린은 ");
        final Optional<String> result2 = sentenceBuffer.append("공복에 복용을 피하세요.");

        // then
        assertThat(result1).isEmpty();
        assertThat(result2).contains("아스피린은 공복에 복용을 피하세요.");
    }

    @Test
    void append_물음표로_문장이_끝나면_반환한다() {
        // given & when
        final Optional<String> result = sentenceBuffer.append("부작용이 뭔가요?");

        // then
        assertThat(result).contains("부작용이 뭔가요?");
    }

    @Test
    void append_느낌표로_문장이_끝나면_반환한다() {
        // given & when
        final Optional<String> result = sentenceBuffer.append("꼭 드세요!");

        // then
        assertThat(result).contains("꼭 드세요!");
    }

    @Test
    void flush_남은_텍스트를_반환한다() {
        // given
        sentenceBuffer.append("남은 텍스트");

        // when
        final Optional<String> result = sentenceBuffer.flush();

        // then
        assertThat(result).contains("남은 텍스트");
    }

    @Test
    void flush_비어있으면_empty를_반환한다() {
        // when
        final Optional<String> result = sentenceBuffer.flush();

        // then
        assertThat(result).isEmpty();
    }
}
