package com.geydev.kalfactions.integration.xaero.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArchiveHashingTest {
    private static final String VALID = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void acceptsALowercaseSha256() {
        assertTrue(ArchiveHashing.isSha256(VALID));
        assertEquals(VALID, ArchiveHashing.sha256(new byte[0]));
    }

    @Test
    void hashIsStableAcrossTheTwoOverloads() {
        byte[] bytes = "kingdoms".getBytes(StandardCharsets.UTF_8);
        assertEquals(ArchiveHashing.sha256(bytes), ArchiveHashing.sha256(bytes));
        assertEquals(64, ArchiveHashing.sha256(bytes).length());
        assertTrue(ArchiveHashing.isSha256(ArchiveHashing.sha256(bytes)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "abc",
            "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b8555",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85g",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85 "
    })
    void rejectsAnythingThatIsNotSixtyFourLowercaseHexCharacters(String candidate) {
        assertFalse(ArchiveHashing.isSha256(candidate), candidate);
    }

    @Test
    void rejectsNull() {
        assertFalse(ArchiveHashing.isSha256(null));
    }

    @Test
    void rejectsEveryShapeThatCouldEscapeAFolder() {
        List<String> traversal = List.of(
                "..",
                "../../etc/passwd",
                "..\\..\\windows",
                "/absolute",
                "C:/windows/system32",
                "a/b",
                "a\\b",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b8/5",
                "....//....//" + VALID
        );
        for (String candidate : traversal) {
            assertFalse(ArchiveHashing.isSha256(candidate), candidate);
        }
    }
}
