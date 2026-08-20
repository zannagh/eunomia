package de.zannagh.eunomia.compatibility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the constants, the {@link CompatInitializationResult#failure(String)} factory, and the
 * {@link CompatInitializationResult#isMissingInitializerResult()} discriminator.
 */
class CompatInitializationResultTest {

    @Test
    void successConstantIsSuccessfulAndNotAMissingResult() {
        assertThat(CompatInitializationResult.SUCCESS.success()).isTrue();
        assertThat(CompatInitializationResult.SUCCESS.message()).isNotBlank();
        assertThat(CompatInitializationResult.SUCCESS.isMissingInitializerResult()).isFalse();
    }

    @Test
    void missingConstantIsUnsuccessfulAndFlaggedAsMissing() {
        assertThat(CompatInitializationResult.MISSING.success()).isFalse();
        assertThat(CompatInitializationResult.MISSING.isMissingInitializerResult()).isTrue();
    }

    @Test
    void failureConstantIsUnsuccessfulButNotAMissingResult() {
        assertThat(CompatInitializationResult.FAILURE.success()).isFalse();
        assertThat(CompatInitializationResult.FAILURE.isMissingInitializerResult()).isFalse();
    }

    @Test
    void failureFactoryCarriesTheGivenMessageAndIsNotAMissingResult() {
        CompatInitializationResult result = CompatInitializationResult.failure("boom");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("boom");
        assertThat(result.isMissingInitializerResult()).isFalse();
    }

    @Test
    void recordEqualityFollowsSuccessAndMessage() {
        CompatInitializationResult sameAsSuccess =
                new CompatInitializationResult(true, CompatInitializationResult.SUCCESS.message());

        assertThat(sameAsSuccess).isEqualTo(CompatInitializationResult.SUCCESS);
        assertThat(CompatInitializationResult.failure("a")).isNotEqualTo(CompatInitializationResult.failure("b"));
    }
}
