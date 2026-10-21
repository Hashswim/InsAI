package com.insuranceai.ai.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record JevResult(
    @JsonProperty("request_id") UUID requestId,
    @JsonProperty("submission_status") String submissionStatus,
    @JsonProperty("regulations_status") String regulationsStatus,
    String reason, List<Item> items, String provider, String model,
    @JsonProperty("prompt_version") String promptVersion,
    @JsonProperty("prompt_sha256") String promptSha256,
    @JsonProperty("chain_version") String chainVersion,
    @JsonProperty("trace_id") UUID traceId,
    @JsonProperty("trace_status") String traceStatus,
    @JsonProperty("input_tokens") int inputTokens,
    @JsonProperty("output_tokens") int outputTokens,
    Verification verification
) {
    public record Verification(String method,
                               @JsonProperty("factual_consistency") String factualConsistency,
                               @JsonProperty("confidence_basis") String confidenceBasis,
                               @JsonProperty("human_review") String humanReview) {}
    public record Evidence(@JsonProperty("document_id") UUID documentId, int page, String description) {}
    public record Item(String item, String judgment, List<Evidence> evidence, double confidence,
                       @JsonProperty("verification_reason") String verificationReason, String supplement) {}
}
