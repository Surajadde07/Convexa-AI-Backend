package com.convexa.ai.convexa_ai_backend.dto;

import com.convexa.ai.convexa_ai_backend.entity.DealStage;
import com.convexa.ai.convexa_ai_backend.entity.DealStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealRequest {

    private String dealName;

    private String accountName;

    @NotNull(message = "Deal value is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Deal value must be greater than or equal to 0")
    private BigDecimal dealValue;

    @NotNull(message = "Deal status is required")
    private DealStatus dealStatus;

    @NotNull(message = "Deal stage is required")
    private DealStage dealStage;

}
