package com.hgn.sos.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimRequest(@NotBlank String coordinatorId) {}