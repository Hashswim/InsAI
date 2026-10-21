package com.insuranceai.ai.infrastructure;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.ai.application.JevGateway;
import com.insuranceai.ai.application.JevResult;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Strict live RPC contract; deliberately separate from historical database deserialization. */
final class JevWireResponseDecoder {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
            .maxStringLength(200_000).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    JevResult decode(byte[] bytes, JevGateway.Request request, boolean allowStub) throws IOException {
        // Do not allow Jackson's automatic UTF-16/32 encoding detection on the UTF-8 wire.
        String json = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        var root = mapper.readTree(json);
        fields(root, "request_id", "submission_status", "regulations_status", "reason", "items",
            "provider", "model", "prompt_version", "prompt_sha256", "chain_version", "trace_id",
            "trace_status", "input_tokens", "output_tokens", "verification");
        require(uuid(root.get("request_id")).equals(request.requestId()));
        equal(root, "submission_status", "ON_HOLD");
        equal(root, "regulations_status", "NOT_REGISTERED");
        text(root, "reason", 2000, false);
        String provider = text(root, "provider", 20, false);
        // "rules": answered by the AI service's text-layer presence check without a model call.
        require(Set.of("gemini", "openai", "rules").contains(provider) || (allowStub && provider.equals("stub")));
        text(root, "model", 200, false);
        text(root, "prompt_version", 100, false);
        require(text(root, "prompt_sha256", 64, false).matches("[0-9a-f]{64}"));
        text(root, "chain_version", 100, false);
        int tokens = integer(root.get("input_tokens"), 0, Integer.MAX_VALUE)
            + integer(root.get("output_tokens"), 0, Integer.MAX_VALUE);
        require(!provider.equals("rules") || tokens == 0);
        String trace = text(root, "trace_status", 20, false);
        require(Set.of("RECORDED", "DISABLED", "FAILED").contains(trace));
        if (trace.equals("RECORDED")) uuid(root.get("trace_id"));
        else require(root.get("trace_id").isNull());
        var verification = root.get("verification");
        fields(verification, "method", "factual_consistency", "confidence_basis", "human_review");
        equal(verification, "method", "STRUCTURAL");
        equal(verification, "factual_consistency", "NOT_VERIFIED");
        equal(verification, "confidence_basis", switch (provider) {
            case "stub" -> "OFFLINE_STUB";
            case "rules" -> "RULE_PRESENCE_CHECK";
            default -> "MODEL_SELF_ASSESSMENT";
        });
        equal(verification, "human_review", "REQUIRED");
        var documentIds = request.documents().stream().map(JevGateway.Document::documentId).collect(Collectors.toSet());
        var items = root.get("items");
        require(items.isArray() && !items.isEmpty() && items.size() <= 100);
        var names = new HashSet<String>();
        for (var item : items) {
            fields(item, "item", "judgment", "evidence", "confidence", "verification_reason", "supplement");
            require(names.add(text(item, "item", 200, false).strip().toLowerCase(java.util.Locale.ROOT)));
            String judgment = text(item, "judgment", 30, false);
            require(Set.of("SUITABLE", "NEEDS_SUPPLEMENT", "UNVERIFIABLE").contains(judgment));
            text(item, "verification_reason", 2000, false);
            String supplement = text(item, "supplement", 2000, true);
            require(!judgment.equals("NEEDS_SUPPLEMENT") || !supplement.isBlank());
            var confidenceNode = item.get("confidence");
            require(confidenceNode.isNumber());
            double confidence = confidenceNode.doubleValue();
            require(Double.isFinite(confidence) && confidence >= 0 && confidence <= 1);
            require(!judgment.equals("UNVERIFIABLE") || confidence == 0);
            var evidence = item.get("evidence");
            require(evidence.isArray() && evidence.size() <= 20);
            require(judgment.equals("UNVERIFIABLE") || !evidence.isEmpty());
            require(!provider.equals("stub") || (judgment.equals("UNVERIFIABLE") && evidence.isEmpty()));
            var citations = new HashSet<String>();
            for (var entry : evidence) {
                fields(entry, "document_id", "page", "description");
                UUID id = uuid(entry.get("document_id"));
                require(documentIds.contains(id));
                int page = integer(entry.get("page"), 1, 30);
                String description = text(entry, "description", 2000, false);
                require(citations.add(id + ":" + page + ":" + description.strip()));
            }
        }
        return mapper.treeToValue(root, JevResult.class);
    }

    private static void fields(JsonNode node, String... expected) {
        require(node != null && node.isObject());
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(expected)));
    }

    private static String text(JsonNode node, String key, int max, boolean empty) {
        var value = node.get(key);
        require(value != null && value.isTextual());
        String result = value.textValue();
        require(result.codePointCount(0, result.length()) <= max && (empty || !result.isBlank()));
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (Character.isHighSurrogate(c)) {
                require(i + 1 < result.length() && Character.isLowSurrogate(result.charAt(++i)));
            } else require(!Character.isLowSurrogate(c));
        }
        return result;
    }

    private static void equal(JsonNode node, String key, String expected) {
        require(expected.equals(text(node, key, 100, false)));
    }

    private static int integer(JsonNode value, int min, int max) {
        require(value != null && value.isIntegralNumber() && value.canConvertToInt());
        int result = value.intValue();
        require(result >= min && result <= max);
        return result;
    }

    private static UUID uuid(JsonNode node) {
        require(node != null && node.isTextual());
        String value = node.textValue();
        UUID result = UUID.fromString(value);
        require(result.toString().equals(value));
        return result;
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid JEV wire response");
    }
}
