package com.convexa.ai.convexa_ai_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineSummaryResponse {
    private BigDecimal pipelineCovered;
    private BigDecimal closedWon;
    private BigDecimal lostDealValue;
    private long openDealCount;
    private long wonDealCount;
    private long lostDealCount;
}
