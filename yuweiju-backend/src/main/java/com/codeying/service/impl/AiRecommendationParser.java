package com.codeying.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure interpretation of model content. Catalog data and card fields remain server-owned. */
final class AiRecommendationParser {
    private AiRecommendationParser() {}

    record Selection(String reply, List<Long> dishIds) {}

    static JsonNode readObject(ObjectMapper mapper, String raw) throws IOException {
        if (!StringUtils.hasText(raw)) throw new IllegalArgumentException("Missing model content");
        String json = raw.trim();
        // DeepSeek may wrap otherwise legitimate JSON in a single complete Markdown code fence.
        if (json.startsWith("```")) {
            int newline = json.indexOf('\n');
            if (newline < 0 || !json.endsWith("```")) throw new IllegalArgumentException("Invalid JSON fence");
            String language = json.substring(3, newline).trim();
            if (!language.isEmpty() && !"json".equalsIgnoreCase(language)) throw new IllegalArgumentException("Invalid JSON fence");
            json = json.substring(newline + 1, json.length() - 3).trim();
        }
        JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Expected model object");
        return root;
    }

    static Selection parse(ObjectMapper mapper, String raw, Set<Long> candidates) throws IOException {
        JsonNode root = readObject(mapper, raw);
        if (!root.path("reply").isTextual() || !StringUtils.hasText(root.path("reply").textValue())
                || !root.path("action").isTextual() || !"suggest_dish".equals(root.path("action").textValue())
                || !root.has("payload") || !root.path("payload").isNull()
                || !root.path("dishes").isArray() || root.path("dishes").isEmpty()) {
            throw new IllegalArgumentException("Invalid recommendation fields");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode dish : root.path("dishes")) {
            JsonNode id = dish.path("dishId");
            if (!dish.isObject() || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0
                    || !candidates.contains(id.longValue())) {
                throw new IllegalArgumentException("Invalid recommendation selection");
            }
            ids.add(id.longValue());
        }
        return new Selection(root.path("reply").textValue().trim(), List.copyOf(ids));
    }
}
