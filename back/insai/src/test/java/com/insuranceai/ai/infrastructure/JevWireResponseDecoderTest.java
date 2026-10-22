package com.insuranceai.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insuranceai.ai.application.JevGateway;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JevWireResponseDecoderTest {
    final ObjectMapper mapper = new ObjectMapper();
    final JevWireResponseDecoder decoder = new JevWireResponseDecoder();

    ObjectNode fixture() throws Exception {
        return (ObjectNode) mapper.readTree(Files.readString(Path.of("../../docs/ai/contracts/jev-response-example.json")));
    }

    JevGateway.Request request(ObjectNode root) {
        return new JevGateway.Request(UUID.fromString(root.get("request_id").asText()), List.of(
            new JevGateway.Document(UUID.fromString("22222222-2222-4222-8222-222222222222"), "DIAGNOSIS", "SENTINEL_PDF")));
    }

    @Test void acceptsSharedContractWithoutExposingRawRecords() throws Exception {
        var root = fixture();
        var request = request(root);
        var result = decoder.decode(mapper.writeValueAsBytes(root), request, false);
        assertEquals("gemini", result.provider());
        assertEquals(1, result.items().getFirst().evidence().getFirst().page());
        assertEquals("JevResult[REDACTED]", result.toString());
        assertEquals("JevItem[REDACTED]", result.items().getFirst().toString());
        assertEquals("JevEvidence[REDACTED]", result.items().getFirst().evidence().getFirst().toString());
        assertFalse(request.toString().contains("SENTINEL"));
        assertFalse(request.documents().toString().contains("SENTINEL"));
    }

    ObjectNode rules() throws Exception {
        var root = fixture();
        root.put("provider", "rules").put("model", "jev-rules-v1").put("input_tokens", 0).put("output_tokens", 0);
        root.putArray("risk_signals");
        root.putArray("policy_refs");
        ((ObjectNode) root.get("verification")).put("confidence_basis", "RULE_PRESENCE_CHECK");
        return root;
    }

    @Test void acceptsRuleOnlyResultWithoutModelUsage() throws Exception {
        var root = rules();
        var result = decoder.decode(mapper.writeValueAsBytes(root), request(root), false);
        assertEquals("rules", result.provider());
        assertEquals("RULE_PRESENCE_CHECK", result.verification().confidenceBasis());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tokens", "model_basis", "rules_basis_on_model", "rules_signal"})
    void rejectsInconsistentRuleOnlyResult(String mutation) throws Exception {
        var root = rules();
        switch (mutation) {
            case "tokens" -> root.put("input_tokens", 1);
            case "model_basis" -> ((ObjectNode) root.get("verification")).put("confidence_basis", "MODEL_SELF_ASSESSMENT");
            case "rules_basis_on_model" -> root.put("provider", "gemini");
            case "rules_signal" -> root.set("risk_signals", fixture().get("risk_signals"));
            default -> fail("unknown mutation");
        }
        assertThrows(Exception.class, () -> decoder.decode(mapper.writeValueAsBytes(root), request(root), false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing_tokens", "null_tokens", "string_tokens", "fraction_tokens", "negative_tokens",
        "overflow_tokens", "unknown_field", "missing_confidence", "null_confidence", "string_confidence",
        "huge_confidence", "negative_confidence", "unknown_judgment", "no_evidence", "foreign_document",
        "page_zero", "page_large", "fraction_page", "string_page", "blank_description", "long_description",
        "missing_supplement", "blank_supplement", "unknown_confidence", "trace_missing", "trace_contradiction",
        "trace_unknown", "bad_uuid", "wrong_request", "duplicate_item", "duplicate_evidence", "empty_model",
        "null_item", "many_items", "many_evidence", "null_verification", "surrogate",
        "missing_summary", "blank_summary", "missing_signals", "signal_level", "signal_no_evidence",
        "signal_foreign_document", "signal_page_large", "many_signals", "missing_refs", "ref_bad_id",
        "ref_long_excerpt", "ref_duplicate", "ref_unknown_field"})
    void rejectsInvalidNestedWireData(String mutation) throws Exception {
        var root = fixture();
        var request = request(root);
        var item = (ObjectNode) root.withArray("items").get(0);
        var evidence = (ObjectNode) item.withArray("evidence").get(0);
        switch (mutation) {
            case "missing_tokens" -> root.remove("input_tokens");
            case "null_tokens" -> root.putNull("input_tokens");
            case "string_tokens" -> root.put("input_tokens", "10");
            case "fraction_tokens" -> root.put("input_tokens", 10.5);
            case "negative_tokens" -> root.put("input_tokens", -1);
            case "overflow_tokens" -> root.put("input_tokens", Long.MAX_VALUE);
            case "unknown_field" -> item.put("secret", "SENTINEL");
            case "missing_confidence" -> item.remove("confidence");
            case "null_confidence" -> item.putNull("confidence");
            case "string_confidence" -> item.put("confidence", "0.8");
            case "huge_confidence" -> item.put("confidence", 1.01);
            case "negative_confidence" -> item.put("confidence", -0.01);
            case "unknown_judgment" -> item.put("judgment", "APPROVED");
            case "no_evidence" -> item.putArray("evidence");
            case "foreign_document" -> evidence.put("document_id", UUID.randomUUID().toString());
            case "page_zero" -> evidence.put("page", 0);
            case "page_large" -> evidence.put("page", 31);
            case "fraction_page" -> evidence.put("page", 1.5);
            case "string_page" -> evidence.put("page", "1");
            case "blank_description" -> evidence.put("description", " ");
            case "long_description" -> evidence.put("description", "x".repeat(2001));
            case "missing_supplement" -> item.remove("supplement");
            case "blank_supplement" -> item.put("judgment", "NEEDS_SUPPLEMENT");
            case "unknown_confidence" -> item.put("judgment", "UNVERIFIABLE");
            case "trace_missing" -> root.putNull("trace_id");
            case "trace_contradiction" -> root.put("trace_status", "DISABLED");
            case "trace_unknown" -> root.put("trace_status", "PENDING");
            case "bad_uuid" -> root.put("request_id", "1-1-1-1-1");
            case "wrong_request" -> root.put("request_id", UUID.randomUUID().toString());
            case "duplicate_item" -> root.withArray("items").add(item.deepCopy());
            case "duplicate_evidence" -> item.withArray("evidence").add(evidence.deepCopy());
            case "empty_model" -> root.put("model", " ");
            case "null_item" -> root.withArray("items").addNull();
            case "many_items" -> { for (int i=0; i<100; i++) root.withArray("items").add(item.deepCopy()); }
            case "many_evidence" -> { for (int i=0; i<20; i++) item.withArray("evidence").add(evidence.deepCopy()); }
            case "null_verification" -> root.putNull("verification");
            case "surrogate" -> item.put("item", "\uD800");
            case "missing_summary" -> root.remove("summary");
            case "blank_summary" -> root.put("summary", " ");
            case "missing_signals" -> root.remove("risk_signals");
            case "signal_level" -> ((ObjectNode) root.withArray("risk_signals").get(0)).put("level", "FRAUD");
            case "signal_no_evidence" -> ((ObjectNode) root.withArray("risk_signals").get(0)).putArray("evidence");
            case "signal_foreign_document" -> ((ObjectNode) root.withArray("risk_signals").get(0).get("evidence").get(0))
                .put("document_id", UUID.randomUUID().toString());
            case "signal_page_large" -> ((ObjectNode) root.withArray("risk_signals").get(0).get("evidence").get(0)).put("page", 31);
            case "missing_refs" -> root.remove("policy_refs");
            case "ref_bad_id" -> ((ObjectNode) root.withArray("policy_refs").get(0)).put("clause_id", "제7조");
            case "ref_long_excerpt" -> ((ObjectNode) root.withArray("policy_refs").get(0)).put("excerpt", "x".repeat(401));
            case "ref_duplicate" -> root.withArray("policy_refs").add(root.withArray("policy_refs").get(0).deepCopy());
            case "ref_unknown_field" -> ((ObjectNode) root.withArray("policy_refs").get(0)).put("text", "SENTINEL");
            case "many_signals" -> { var signals = root.withArray("risk_signals");
                for (int i=0; i<20; i++) signals.add(signals.get(0).deepCopy()); }
            default -> fail("unknown mutation");
        }
        assertThrows(Exception.class, () -> decoder.decode(mapper.writeValueAsBytes(root), request, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "trailing", "nonfinite", "depth"})
    void rejectsMalformedJson(String mutation) throws Exception {
        var root = fixture();
        String json = mapper.writeValueAsString(root);
        json = switch (mutation) {
            case "duplicate" -> json.replace("\"confidence\":0.8", "\"confidence\":0.8,\"confidence\":0.9");
            case "trailing" -> json + " {}";
            case "nonfinite" -> json.replace("\"confidence\":0.8", "\"confidence\":1e999");
            default -> "[".repeat(40) + "0" + "]".repeat(40);
        };
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> decoder.decode(bytes, request(root), false));
    }
}
