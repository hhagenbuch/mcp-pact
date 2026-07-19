package io.github.hhagenbuch.mcppact.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hhagenbuch.mcppact.core.model.Pact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and writes {@code *.mcp-pact.json} files. */
public final class PactIO {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private PactIO() {
    }

    public static Pact parse(String json) {
        try {
            return MAPPER.readValue(json, Pact.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("not a valid mcp-pact document: " + e.getMessage(), e);
        }
    }

    public static Pact load(Path path) {
        try {
            return parse(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toJson(Pact pact) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(pact);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(Path path, Pact pact) {
        try {
            Files.writeString(path, toJson(pact));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
