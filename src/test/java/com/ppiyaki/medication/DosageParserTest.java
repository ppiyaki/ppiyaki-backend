package com.ppiyaki.medication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DosageParser.parsePillCount")
class DosageParserTest {

    @Test
    @DisplayName("\"1정\" → Optional.of(1)")
    void single_pill() {
        assertThat(DosageParser.parsePillCount("1정")).contains(1);
    }

    @Test
    @DisplayName("\"2알\" → Optional.of(2)")
    void two_pills() {
        assertThat(DosageParser.parsePillCount("2알")).contains(2);
    }

    @Test
    @DisplayName("\"1.5캡슐\" → Optional.of(1) (정수 첫 매치만)")
    void decimal_truncated_to_first_int() {
        assertThat(DosageParser.parsePillCount("1.5캡슐")).contains(1);
    }

    @Test
    @DisplayName("\"반알\" → Optional.empty()")
    void korean_no_digit() {
        assertThat(DosageParser.parsePillCount("반알")).isEmpty();
    }

    @Test
    @DisplayName("null → Optional.empty()")
    void null_input() {
        assertThat(DosageParser.parsePillCount(null)).isEmpty();
    }

    @Test
    @DisplayName("blank → Optional.empty()")
    void blank_input() {
        assertThat(DosageParser.parsePillCount("   ")).isEmpty();
    }

    @Test
    @DisplayName("\"3정 1일 2회\" → 3 (첫 매치)")
    void multiple_numbers_uses_first() {
        assertThat(DosageParser.parsePillCount("3정 1일 2회")).contains(3);
    }
}
