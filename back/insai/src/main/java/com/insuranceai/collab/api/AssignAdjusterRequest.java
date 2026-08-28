package com.insuranceai.collab.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignAdjusterRequest(@NotNull UUID adjusterId) {
}
