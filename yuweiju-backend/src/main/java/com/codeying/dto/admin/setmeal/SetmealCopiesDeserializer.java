package com.codeying.dto.admin.setmeal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** Copies are SQL INT quantities; Jackson's default float-to-int coercion would silently truncate. */
public class SetmealCopiesDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return parser.getIntValue();
        // Retain the existing integer-string consumer compatibility without accepting decimals.
        if (parser.hasToken(JsonToken.VALUE_STRING) && parser.getText().trim().matches("[+-]?[0-9]+")) {
            try {
                return Integer.valueOf(parser.getText().trim());
            } catch (NumberFormatException ignored) {
                // Report the same public validation error as other malformed quantities.
            }
        }
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
}
