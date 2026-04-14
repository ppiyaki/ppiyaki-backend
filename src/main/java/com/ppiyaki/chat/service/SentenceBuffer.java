package com.ppiyaki.chat.service;

import java.util.Optional;

public class SentenceBuffer {

    private final StringBuilder buffer = new StringBuilder();

    public Optional<String> append(final String token) {
        buffer.append(token);
        final String current = buffer.toString();

        final int lastSentenceEnd = findLastSentenceEnd(current);
        if (lastSentenceEnd >= 0) {
            final String sentence = current.substring(0, lastSentenceEnd + 1).trim();
            buffer.delete(0, lastSentenceEnd + 1);
            if (!sentence.isEmpty()) {
                return Optional.of(sentence);
            }
        }
        return Optional.empty();
    }

    public Optional<String> flush() {
        final String remaining = buffer.toString().trim();
        buffer.setLength(0);
        if (!remaining.isEmpty()) {
            return Optional.of(remaining);
        }
        return Optional.empty();
    }

    private int findLastSentenceEnd(final String text) {
        int lastIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            if (ch == '.' || ch == '?' || ch == '!' || ch == '。') {
                lastIndex = i;
            }
        }
        return lastIndex;
    }
}
