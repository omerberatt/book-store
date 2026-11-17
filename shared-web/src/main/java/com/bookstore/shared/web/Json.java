package com.bookstore.shared.web;

import com.bookstore.shared.kernel.identity.Identifier;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The API's JSON rules, in one place:
 *
 * <ul>
 *   <li>records are read and written by their components;</li>
 *   <li>typed ids ({@link Identifier}) are written as plain numbers, so {@code BookId} is {@code 42};</li>
 *   <li>{@code Instant} is written as ISO-8601 text and {@code Year} as a number;</li>
 *   <li>unknown fields in a request are an error, so a typo is not silently ignored.</li>
 * </ul>
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule("bookstore-ids").addSerializer(Identifier.class, new IdSerializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private Json() {
    }

    /** @return the configured mapper; do not change its settings */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Writes a value as JSON.
     *
     * @param value the value
     * @return UTF-8 bytes
     */
    public static byte[] write(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not write " + value.getClass().getName() + " as JSON", e);
        }
    }

    /**
     * Reads a request body.
     *
     * @param body UTF-8 bytes
     * @param type the request record
     * @param <T>  the type
     * @return the value
     * @throws WebException 400 if the body is not valid JSON for the type
     */
    public static <T> T read(byte[] body, Class<T> type) {
        if (body.length == 0) {
            throw WebException.badRequest("request.body-missing", "The request needs a JSON body");
        }
        try {
            return MAPPER.readValue(body, type);
        } catch (JacksonException e) {
            throw WebException.badRequest("request.malformed-json", "The body is not valid JSON for this request: "
                    + e.getOriginalMessage());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the request body", e);
        }
    }

    /** Writes a typed id as its number. */
    private static final class IdSerializer extends StdSerializer<Identifier> {

        IdSerializer() {
            super(Identifier.class);
        }

        @Override
        public void serialize(Identifier id, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeNumber(id.value());
        }
    }
}
