package io.civicpulse.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidPasswordValidatorTest {

    private final ValidPasswordValidator validator = new ValidPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"0123456789", "correct horse battery staple", "ünïcödé-pässwörd"})
    void validPasswords(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "123456789", "          "})
    void invalidPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {72, 73})
    void maxLengthIs72Bytes(int length) {
        assertThat(validator.isValid("a".repeat(length), null)).isEqualTo(length <= 72);
    }

    @ParameterizedTest
    @ValueSource(ints = {36, 37})
    void multibyteCharacters(int twoByteCharacters) {
        assertThat(validator.isValid("é".repeat(twoByteCharacters), null)).isEqualTo(twoByteCharacters * 2 <= 72);
    }
}
