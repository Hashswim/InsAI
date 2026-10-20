package com.insuranceai.ai.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record RunJevCommand(@NotNull UUID caseId, @NotNull @Size(min=3, max=3) List<@Valid FileRef> documents) {
    public enum Kind { DIAGNOSIS, TREATMENT_CONFIRMATION, ITEMIZED_BILL }
    public record FileRef(@NotNull UUID fileId, @NotNull Kind kind) {}
}
