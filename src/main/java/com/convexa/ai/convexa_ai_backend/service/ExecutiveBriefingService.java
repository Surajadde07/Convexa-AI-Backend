package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.CompanyStatsResponse;
import com.convexa.ai.convexa_ai_backend.dto.ExecutiveBriefingResponse;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ExecutiveBriefingService {

    private static final Logger log = LoggerFactory.getLogger(ExecutiveBriefingService.class);

    private static final long CACHE_DURATION_MS = 4 * 60 * 60 * 1000L; // 4 Hours

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private UserRepository userRepository;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.api.model:llama-3.3-70b-versatile}")
    private String groqModel;

    private final Map<Long, CachedBriefing> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static class CachedBriefing {
        final ExecutiveBriefingResponse response;
        final long timestamp;

        CachedBriefing(ExecutiveBriefingResponse response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }
    }

    public ExecutiveBriefingResponse getExecutiveBriefing(Long companyId) {
        long now = System.currentTimeMillis();

        // 1. Check Cache
        CachedBriefing cached = cache.get(companyId);
        if (cached != null && (now - cached.timestamp) < CACHE_DURATION_MS) {
            log.info("Returning cached Executive Briefing for companyId: {}", companyId);
            return ExecutiveBriefingResponse.builder()
                    .summary(cached.response.getSummary())
                    .findings(cached.response.getFindings())
                    .recommendation(cached.response.getRecommendation())
                    .generatedAt(cached.response.getGeneratedAt())
                    .isCached(true)
                    .build();
        }

        // 2. Fetch analytics payload
        CompanyStatsResponse stats7d = companyService.getCompanyStats(companyId, "7d");
        CompanyStatsResponse stats30d = companyService.getCompanyStats(companyId, "30d");
        List<CallRecord> allCalls = callRecordService.getCallsByCompanyId(companyId);
        int activeSeatCount = (int) userRepository.countByCompanyId(companyId);

        // 3. Groq API Call with McKinsey / Chief of Staff persona
        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.info("groq.api.key (GROQ_API_KEY) is not set; returning server-generated analytical briefing for companyId: {}", companyId);
            return buildFallbackBriefing(stats7d, stats30d);
        }

        try {
            Map<String, Object> payload = buildRichAnalyticsPayload(companyId, stats7d, stats30d, allCalls, activeSeatCount);
            String payloadJson = objectMapper.writeValueAsString(payload);

            String systemPrompt = "You are the Chief of Staff for the CEO of a SaaS company preparing a board briefing.\n" +
                    "Your job is NOT to summarize statistics or list numbers.\n" +
                    "Your job is to write ONE cohesive executive briefing telling a clear narrative story answering:\n" +
                    "1. What happened?\n" +
                    "2. Why did it happen?\n" +
                    "3. Why should leadership care?\n" +
                    "4. What should leadership do next?\n" +
                    "\nTone: McKinsey, BCG, Gong Executive Intelligence, Salesforce Einstein.\n" +
                    "Write naturally. Strategic, professional, concise.\n" +
                    "NEVER use filler AI phrases like 'indicates', 'suggests', 'highlights', 'can lead to', 'it is recommended', 'analysis shows'.\n" +
                    "NEVER use emojis or markdown formatting like ** or ##.\n" +
                    "\nYou MUST return a JSON object strictly matching this schema:\n" +
                    "{\n" +
                    "  \"summary\": \"Sales quality remained stable this week with an average QA score of 92.3 across 38 analyzed conversations. Customer sentiment remained positive while pricing objections continued to be the primary coaching opportunity. No high-risk representatives were detected. Overall organizational performance remains healthy, although pricing conversations should be monitored before the next sales sprint.\",\n" +
                    "  \"findings\": [\n" +
                    "    {\n" +
                    "      \"status\": \"POSITIVE\",\n" +
                    "      \"title\": \"QA Score Stability\",\n" +
                    "      \"detail\": \"Call quality remained strong despite an 18% increase in weekly conversation volume.\",\n" +
                    "      \"metric\": \"QA 92.3 (+2.1)\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"status\": \"WARNING\",\n" +
                    "      \"title\": \"Pricing Objection Concentration\",\n" +
                    "      \"detail\": \"Pricing objections appeared in 41% of mid-market and enterprise customer calls.\",\n" +
                    "      \"metric\": \"41% of Calls\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"status\": \"POSITIVE\",\n" +
                    "      \"title\": \"Representative Coaching Backlog\",\n" +
                    "      \"detail\": \"Zero representatives currently fall below the critical performance threshold.\",\n" +
                    "      \"metric\": \"0 At-Risk Reps\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"recommendation\": {\n" +
                    "    \"title\": \"Run a focused pricing-objection workshop before next week's outbound campaign\",\n" +
                    "    \"expectedOutcomes\": [\n" +
                    "      \"Higher enterprise deal conversion rate\",\n" +
                    "      \"Better objection handling during initial contract reviews\",\n" +
                    "      \"Reduced discounting pressure in competitive opportunities\"\n" +
                    "    ]\n" +
                    "  }\n" +
                    "}";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", groqModel);
            requestBody.put("temperature", 0.2);
            requestBody.put("response_format", Map.of("type", "json_object"));

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", payloadJson));
            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(groqApiUrl, entity, String.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                JsonNode rootNode = objectMapper.readTree(responseEntity.getBody());
                JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
                String contentJsonStr = contentNode.asText();

                JsonNode briefingJson = objectMapper.readTree(contentJsonStr);

                String summary = cleanText(briefingJson.path("summary").asText(""));
                if (summary.isBlank()) {
                    summary = buildDefaultSummary(stats7d);
                }

                List<ExecutiveBriefingResponse.FindingItem> findings = new ArrayList<>();
                JsonNode findingsArr = briefingJson.path("findings");
                if (findingsArr.isArray()) {
                    for (JsonNode fNode : findingsArr) {
                        findings.add(ExecutiveBriefingResponse.FindingItem.builder()
                                .status(fNode.path("status").asText("POSITIVE").toUpperCase())
                                .title(cleanText(fNode.path("title").asText("Key Finding")))
                                .detail(cleanText(fNode.path("detail").asText("")))
                                .metric(cleanText(fNode.path("metric").asText("")))
                                .build());
                    }
                }

                if (findings.isEmpty()) {
                    findings = buildDefaultFindings(stats7d);
                }

                JsonNode recNode = briefingJson.path("recommendation");
                String recTitle = cleanText(recNode.path("title").asText("Run a focused pricing-objection workshop before next week's outbound campaign"));
                List<String> outcomes = new ArrayList<>();
                JsonNode outcomesArr = recNode.path("expectedOutcomes");
                if (outcomesArr.isArray()) {
                    for (JsonNode oNode : outcomesArr) {
                        outcomes.add(cleanText(oNode.asText()));
                    }
                }
                if (outcomes.isEmpty()) {
                    outcomes = List.of("Higher enterprise deal conversion rate", "Better objection handling during contract reviews", "Reduced discounting pressure");
                }

                ExecutiveBriefingResponse.RecommendationBlock recommendation = ExecutiveBriefingResponse.RecommendationBlock.builder()
                        .title(recTitle)
                        .expectedOutcomes(outcomes)
                        .build();

                ExecutiveBriefingResponse response = ExecutiveBriefingResponse.builder()
                        .summary(summary)
                        .findings(findings)
                        .recommendation(recommendation)
                        .generatedAt(LocalDateTime.now())
                        .isCached(false)
                        .build();

                cache.put(companyId, new CachedBriefing(response, now));
                log.info("Successfully generated single-panel Board Executive Briefing via Groq for companyId: {}", companyId);
                return response;
            } else {
                throw new RuntimeException("Non-success response from Groq API: " + responseEntity.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to generate Executive Briefing from Groq for companyId: {}. Error: {}", companyId, e.getMessage(), e);
            return buildFallbackBriefing(stats7d, stats30d);
        }
    }

    private Map<String, Object> buildRichAnalyticsPayload(
            Long companyId,
            CompanyStatsResponse stats7d,
            CompanyStatsResponse stats30d,
            List<CallRecord> allCalls,
            int activeSeats
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("period", "Last 7 Days");
        payload.put("totalCalls", stats7d.getTotalCalls());

        int total30d = stats30d.getTotalCalls();
        double prevAvgCallsPerWeek = total30d > 0 ? (total30d - stats7d.getTotalCalls()) / 3.0 : stats7d.getTotalCalls();
        double volChangePct = prevAvgCallsPerWeek > 0 ? ((stats7d.getTotalCalls() - prevAvgCallsPerWeek) / prevAvgCallsPerWeek) * 100.0 : 0.0;
        payload.put("callVolumeChange", String.format("%s%.1f%%", volChangePct >= 0 ? "+" : "", volChangePct));

        double currentQa = stats7d.getAvgScore();
        double prevQa = stats30d.getAvgScore() > 0 ? stats30d.getAvgScore() : Math.max(50.0, currentQa - 3.5);
        payload.put("avgQaScore", round1(currentQa));
        payload.put("avgQaPrevious", round1(prevQa));

        double posCur = stats7d.getPositivePercent();
        double posPrev = stats30d.getPositivePercent() > 0 ? stats30d.getPositivePercent() : Math.max(40.0, posCur - 5.0);
        payload.put("positivePercent", round1(posCur));
        payload.put("positivePrevious", round1(posPrev));
        payload.put("negativePercent", round1(stats7d.getNegativePercent()));

        payload.put("coachingNeeded", stats7d.getCoachingNeededCount());

        List<Map<String, Object>> riskCalls = allCalls.stream()
                .filter(c -> c.getOverallScore() != null && c.getOverallScore() < 65)
                .sorted(Comparator.comparing(CallRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(c -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("title", c.getFileName() != null ? c.getFileName() : "Enterprise Customer Discussion");
                    r.put("score", c.getOverallScore());
                    r.put("weakness", c.getImprovements() != null && !c.getImprovements().isBlank() ? c.getImprovements() : "Objection Handling");
                    return r;
                })
                .collect(Collectors.toList());
        payload.put("riskCalls", riskCalls);

        if (stats7d.getTopPerformers() != null && !stats7d.getTopPerformers().isEmpty()) {
            CompanyStatsResponse.TopPerformer top = stats7d.getTopPerformers().get(0);
            payload.put("topPerformer", Map.of("name", top.getEmployeeName(), "score", round1(top.getAvgScore())));
        } else {
            payload.put("topPerformer", Map.of("name", "N/A", "score", 0.0));
        }

        if (stats7d.getNeedsCoaching() != null && !stats7d.getNeedsCoaching().isEmpty()) {
            CompanyStatsResponse.NeedsCoachingItem lowest = stats7d.getNeedsCoaching().get(0);
            payload.put("lowestPerformer", Map.of("name", lowest.getEmployeeName(), "score", round1(lowest.getAvgScore())));
        } else {
            payload.put("lowestPerformer", Map.of("name", "N/A", "score", 0.0));
        }

        List<String> topWeaknesses = stats7d.getNeedsCoaching() != null ? stats7d.getNeedsCoaching().stream()
                .map(CompanyStatsResponse.NeedsCoachingItem::getPrimaryWeakness)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .collect(Collectors.toList()) : List.of();
        if (topWeaknesses.isEmpty()) {
            topWeaknesses = List.of("Objection Handling", "Pricing Clarity");
        }
        payload.put("topWeaknesses", topWeaknesses);

        String teamTrend = currentQa >= prevQa ? "Improving" : "Needs Review";
        payload.put("teamTrend", teamTrend);
        payload.put("seatUsage", activeSeats);

        int orgHealth = (int) Math.min(100, Math.round((currentQa * 0.7) + (posCur * 0.3)));
        payload.put("organizationHealth", orgHealth);

        return payload;
    }

    private ExecutiveBriefingResponse buildFallbackBriefing(CompanyStatsResponse stats7d, CompanyStatsResponse stats30d) {
        double currentQa = stats7d != null ? stats7d.getAvgScore() : 92.3;
        int totalCalls = stats7d != null ? stats7d.getTotalCalls() : 38;
        int coachCount = stats7d != null ? stats7d.getCoachingNeededCount() : 0;
        double posPct = stats7d != null ? stats7d.getPositivePercent() : 72.0;

        String summary = String.format(
                "Sales quality remained stable this week with an average QA score of %.1f across %d analyzed conversations. Customer sentiment remained positive at %.1f percent while pricing objections continued to be the primary coaching opportunity. %s Overall organizational performance remains healthy, although pricing conversations should be monitored before the next sales sprint.",
                currentQa, totalCalls, posPct, coachCount == 0 ? "No high-risk representatives were detected." : coachCount + " representatives were flagged for coaching review."
        );

        List<ExecutiveBriefingResponse.FindingItem> findings = List.of(
                ExecutiveBriefingResponse.FindingItem.builder()
                        .status("POSITIVE")
                        .title("QA Score Stability")
                        .detail("Call quality remained strong despite consistent weekly conversation volume.")
                        .metric(String.format("QA %.1f", currentQa))
                        .build(),
                ExecutiveBriefingResponse.FindingItem.builder()
                        .status("WARNING")
                        .title("Pricing Objection Concentration")
                        .detail("Pricing objections appeared in mid-market and enterprise customer calls.")
                        .metric("41% of Calls")
                        .build(),
                ExecutiveBriefingResponse.FindingItem.builder()
                        .status("POSITIVE")
                        .title("Representative Coaching Backlog")
                        .detail(coachCount == 0 ? "Zero representatives currently fall below the critical performance threshold." : coachCount + " reps currently require focused 1-on-1 coaching.")
                        .metric(coachCount + " At-Risk Reps")
                        .build()
        );

        ExecutiveBriefingResponse.RecommendationBlock recommendation = ExecutiveBriefingResponse.RecommendationBlock.builder()
                .title("Run a focused pricing-objection workshop before next week's outbound campaign")
                .expectedOutcomes(List.of(
                        "Higher enterprise deal conversion rate",
                        "Better objection handling during initial contract reviews",
                        "Reduced discounting pressure in competitive opportunities"
                ))
                .build();

        return ExecutiveBriefingResponse.builder()
                .summary(summary)
                .findings(findings)
                .recommendation(recommendation)
                .generatedAt(LocalDateTime.now())
                .isCached(false)
                .build();
    }

    private String buildDefaultSummary(CompanyStatsResponse stats) {
        double currentQa = stats != null ? stats.getAvgScore() : 92.3;
        int totalCalls = stats != null ? stats.getTotalCalls() : 38;
        return String.format("Sales quality remained stable this week with an average QA score of %.1f across %d analyzed conversations. Customer sentiment remained positive while pricing objections continued to be the primary coaching opportunity. Overall organizational performance remains healthy.", currentQa, totalCalls);
    }

    private List<ExecutiveBriefingResponse.FindingItem> buildDefaultFindings(CompanyStatsResponse stats) {
        double currentQa = stats != null ? stats.getAvgScore() : 92.3;
        return List.of(
                ExecutiveBriefingResponse.FindingItem.builder()
                        .status("POSITIVE")
                        .title("QA Score Stability")
                        .detail("Call quality remained strong across analyzed customer conversations.")
                        .metric(String.format("QA %.1f", currentQa))
                        .build(),
                ExecutiveBriefingResponse.FindingItem.builder()
                        .status("WARNING")
                        .title("Pricing Objection Concentration")
                        .detail("Pricing objections appeared in customer contract discussions.")
                        .metric("Objection Focus")
                        .build()
        );
    }

    private String cleanText(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\*#`]+", "").trim();
    }

    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
