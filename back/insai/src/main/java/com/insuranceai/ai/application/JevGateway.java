package com.insuranceai.ai.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public interface JevGateway {
    record Document(@JsonProperty("document_id") UUID documentId, String kind,
                    @JsonProperty("content_base64") String contentBase64) {
        @Override public String toString() { return "JevDocument[REDACTED]"; }
    }
    record Request(@JsonProperty("request_id") UUID requestId, List<Document> documents) {
        @Override public String toString() { return "JevRequest[REDACTED]"; }
    }
    JevResult analyze(Request request);
}
