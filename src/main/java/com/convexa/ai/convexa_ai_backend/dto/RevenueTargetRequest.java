package com.convexa.ai.convexa_ai_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for setting or updating workspace revenue target.
 * Restricted to OWNER and ADMIN roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTargetRequest {

    @NotNull(message = "Revenue target amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Revenue target must be greater than or equal to 0")
    private BigDecimal target;

    // Optional: "QUARTERLY" or "MONTHLY" (defaults to "QUARTERLY" if omitted)
    private String period;
}
