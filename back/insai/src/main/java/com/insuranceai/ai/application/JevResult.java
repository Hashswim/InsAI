package com.insuranceai.ai.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record JevResult(
    @JsonProperty("request_id") UUID requestId,
    @JsonProperty("submission_status") String submissionStatus,
    @JsonProperty("regulations_status") String regulationsStatus,
    String reason, List<Item> items,
    @JsonProperty("risk_signals") List<RiskSignal> riskSignals, String summary,
    @JsonProperty("policy_refs") List<PolicyRef> policyRefs,
    String provider, String model,
    @JsonProperty("prompt_version") String promptVersion,
    @JsonProperty("prompt_sha256") String promptSha256,
    @JsonProperty("chain_version") String chainVersion,
    @JsonProperty("trace_id") UUID traceId,
    @JsonProperty("trace_status") String traceStatus,
    @JsonProperty("input_tokens") int inputTokens,
    @JsonProperty("output_tokens") int outputTokens,
    Verification verification
) {
    @Override public String toString() { return "JevResult[REDACTED]"; }
    /** Claimants see the gaps told to them; a model's summary, judgments and risk signals are for the adjuster. */
    public JevResult forClaimant() {
        if ("rules".equals(provider)) return this;
        return new JevResult(requestId, submissionStatus, regulationsStatus, reason, List.of(), List.of(),
            "서류가 모두 확인되어 담당 손해사정사에게 전달되었습니다.", List.of(), provider, model, promptVersion, promptSha256,
            chainVersion, traceId, traceStatus, inputTokens, outputTokens, verification);
    }
    public record Verification(String method,
                               @JsonProperty("factual_consistency") String factualConsistency,
                               @JsonProperty("confidence_basis") String confidenceBasis,
                               @JsonProperty("human_review") String humanReview) {}
    public record Evidence(@JsonProperty("document_id") UUID documentId, int page, String description) {
        @Override public String toString() { return "JevEvidence[REDACTED]"; }
    }
    /** A point the adjuster should verify (possible false/inflated claim), never a fraud finding. */
    public record RiskSignal(String signal, String level, String reason, List<Evidence> evidence) {
        @Override public String toString() { return "JevRiskSignal[REDACTED]"; }
    }
    /** A standard-terms clause found by RAG; text comes from the public corpus, reason from the agent. */
    public record PolicyRef(@JsonProperty("clause_id") String clauseId, String product, String article, String title,
                            String excerpt, String reason) {
        @Override public String toString() { return "JevPolicyRef[REDACTED]"; }
    }
    public record Item(String item, String judgment, List<Evidence> evidence, double confidence,
                       @JsonProperty("verification_reason") String verificationReason, String supplement) {
        @Override public String toString() { return "JevItem[REDACTED]"; }
    }
}
