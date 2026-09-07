package com.geydev.kalfactions.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ProtocolVersionTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/geydev/kalfactions");
    private static final Pattern REGISTRAR = Pattern.compile("\\.registrar\\(([^)]*)\\)");

    @Test
    void everyRegistrarUsesTheSharedProtocolVersion() {
        List<String> offenders = registrarArguments().stream()
                .filter(argument -> !argument.endsWith("KingdomsProtocol.VERSION")
                        && !argument.endsWith("PROTOCOL_VERSION"))
                .toList();

        assertEquals(List.of(), offenders, "payload registrars must share KingdomsProtocol.VERSION");
    }

    @Test
    void theProtocolVersionIsANumber() {
        assertTrue(
                KingdomsProtocol.VERSION.matches("\\d+"),
                "the protocol version must stay a bumpable number, got " + KingdomsProtocol.VERSION
        );
    }

    @Test
    void everyRegistrarIsAccountedFor() {
        assertTrue(registrarArguments().size() >= 18, "the payload registrars must all be scanned");
    }

    private static List<String> registrarArguments() {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(ProtocolVersionTest::argumentsIn)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Stream<String> argumentsIn(Path path) {
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        Matcher matcher = REGISTRAR.matcher(source);
        Stream.Builder<String> found = Stream.builder();
        while (matcher.find()) {
            found.add(matcher.group(1).trim());
        }
        return found.build();
    }
}
