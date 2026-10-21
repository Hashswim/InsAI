package com.insuranceai.ai.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record RunJevCommand(@NotNull UUID caseId, @NotNull @Size(min=3, max=3) List<@NotNull @Valid FileRef> documents) {
    public enum Kind {
        DIAGNOSIS, TREATMENT_CONFIRMATION, ITEMIZED_BILL, INITIAL_VISIT_RECORD, MEDICAL_RECEIPT;
        public static Kind[] legacyValues() { return new Kind[] {DIAGNOSIS,TREATMENT_CONFIRMATION,ITEMIZED_BILL}; }
    }
    public static boolean validKinds(List<FileRef> refs) {
        var kinds=refs.stream().map(FileRef::kind).collect(java.util.stream.Collectors.toSet());
        return kinds.equals(java.util.Set.of(Kind.legacyValues()))
            || kinds.equals(java.util.Set.of(Kind.ITEMIZED_BILL,Kind.INITIAL_VISIT_RECORD,Kind.MEDICAL_RECEIPT));
    }
    public record FileRef(@NotNull UUID fileId, @NotNull Kind kind) {}
}
