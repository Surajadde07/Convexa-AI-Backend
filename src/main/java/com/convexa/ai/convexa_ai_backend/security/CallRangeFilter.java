package com.convexa.ai.convexa_ai_backend.security;

import com.convexa.ai.convexa_ai_backend.entity.CallRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Applies the same "7d / 30d / all" range semantics the frontend used to
 * apply client-side (DashboardPage's `rangedCalls` useMemo). Centralized
 * here so DashboardService and AnalyticsService can't quietly diverge on
 * what "last 7 days" means.
 */
public final class CallRangeFilter {

    private CallRangeFilter() {}

    public static List<CallRecord> apply(List<CallRecord> calls, String range) {
        if (range == null || range.equalsIgnoreCase("all")) {
            return calls;
        }

        int days = switch (range.toLowerCase()) {
            case "7d" -> 7;
            case "30d" -> 30;
            default -> -1; // unrecognized value → no filtering, same as "all"
        };

        if (days <= 0) {
            return calls;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

        return calls.stream()
                .filter(c -> c.getCreatedAt() == null || !c.getCreatedAt().isBefore(cutoff))
                .toList();
    }
}
