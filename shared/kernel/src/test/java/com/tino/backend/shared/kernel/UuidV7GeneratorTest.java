package com.tino.backend.shared.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {
    @Test
    void generatesValidUniqueAndReasonablyOrderedUuidV7Values() {
        var generator = new UuidV7Generator();
        var values = IntStream.range(0, 1_000)
                .mapToObj(ignored -> generator.next())
                .toList();

        assertThat(values).allSatisfy(value -> assertThat(value.version()).isEqualTo(7));
        assertThat(values).allSatisfy(value -> assertThat(value.variant()).isEqualTo(2));
        assertThat(new HashSet<>(values)).hasSize(values.size());
        assertThat(values).isSortedAccordingTo(UUID::compareTo);
    }
}
