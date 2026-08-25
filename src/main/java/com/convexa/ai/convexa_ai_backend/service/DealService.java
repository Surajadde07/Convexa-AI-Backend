package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.DealRequest;
import com.convexa.ai.convexa_ai_backend.dto.PipelineIntelligenceResponse;
import com.convexa.ai.convexa_ai_backend.dto.PipelineSummaryResponse;
import com.convexa.ai.convexa_ai_backend.dto.RevenueTargetRequest;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.DealRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DealService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Updates the company revenue target. Restricted to OWNER and ADMIN roles.
     */
    public Company updateRevenueTarget(RevenueTargetRequest request, WorkspacePrincipal principal) {
        if (principal == null || principal.getCompanyId() == null) {
            throw new RuntimeException("Unauthorized: missing principal");
        }
        if (principal.getRole() != Role.OWNER && principal.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only workspace owners and administrators can configure revenue targets");
        }
        if (request.getTarget() == null || request.getTarget().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Revenue target must be non-negative");
        }

        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        String period = (request.getPeriod() != null && !request.getPeriod().isBlank())
                ? request.getPeriod().trim().toUpperCase() : "QUARTERLY";

        if ("MONTHLY".equals(period)) {
            company.setMonthlyRevenueTarget(request.getTarget());
            company.setRevenueTargetPeriod("MONTHLY");
        } else {
            company.setQuarterlyRevenueTarget(request.getTarget());
            company.setRevenueTargetPeriod("QUARTERLY");
        }

        return companyRepository.save(company);
    }

    /**
     * Creates or updates a deal and associates it with the specified call record.
     */
    public Deal saveOrUpdateDealForCall(Long callId, DealRequest request, WorkspacePrincipal principal) {
        if (request.getDealValue() == null || request.getDealValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deal value must be non-negative");
        }
        if (request.getDealStatus() == null) {
            throw new IllegalArgumentException("Deal status is required");
        }
        if (request.getDealStage() == null) {
            throw new IllegalArgumentException("Deal stage is required");
        }

        CallRecord callRecord = callRecordRepository.findByIdAndCompanyId(callId, principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Call record not found in this workspace"));

        // If user is a regular USER role, restrict modifications to their own calls
        if (principal.getRole() == Role.USER) {
            if (callRecord.getUser() == null || !callRecord.getUser().getId().equals(principal.getUserId())) {
                throw new RuntimeException("You do not have permission to attach a deal to this call");
            }
        }

        Deal deal = callRecord.getDeal();
        if (deal == null) {
            Company company = companyRepository.findById(principal.getCompanyId())
                    .orElseThrow(() -> new RuntimeException("Company not found"));
            User creator = userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            deal = Deal.builder()
                    .company(company)
                    .createdBy(creator)
                    .dealName(request.getDealName())
                    .accountName(request.getAccountName())
                    .dealValue(request.getDealValue())
                    .dealStatus(request.getDealStatus())
                    .dealStage(request.getDealStage())
                    .build();

            deal = dealRepository.save(deal);
            callRecord.setDeal(deal);
            callRecordRepository.save(callRecord);
        } else {
            // Update existing deal
            if (request.getDealName() != null) deal.setDealName(request.getDealName());
            if (request.getAccountName() != null) deal.setAccountName(request.getAccountName());
            deal.setDealValue(request.getDealValue());
            deal.setDealStatus(request.getDealStatus());
            deal.setDealStage(request.getDealStage());
            deal = dealRepository.save(deal);
        }

        return deal;
    }

    /**
     * Detaches the deal from the specified call record and removes the deal from database if orphaned.
     */
    public void removeDealFromCall(Long callId, WorkspacePrincipal principal) {
        CallRecord callRecord = callRecordRepository.findByIdAndCompanyId(callId, principal.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Call record not found in this workspace"));

        // Scoping check for USER role
        if (principal.getRole() == Role.USER) {
            if (callRecord.getUser() == null || !callRecord.getUser().getId().equals(principal.getUserId())) {
                throw new RuntimeException("You do not have permission to modify this call");
            }
        }

        Deal deal = callRecord.getDeal();
        if (deal != null) {
            callRecord.setDeal(null);
            callRecordRepository.save(callRecord);

            // Check if this deal is referenced by other calls in the company
            long refCount = callRecordRepository.findAll().stream()
                    .filter(c -> c.getDeal() != null && c.getDeal().getId().equals(deal.getId()))
                    .count();

            if (refCount == 0) {
                dealRepository.delete(deal);
            }
        }
    }

    /**
     * Fetches pipeline summary statistics securely for the workspace.
     */
    @Transactional(readOnly = true)
    public PipelineSummaryResponse getPipelineSummary(WorkspacePrincipal principal) {
        Long companyId = principal.getCompanyId();

        BigDecimal pipelineCovered = dealRepository.sumPipelineCoveredByCompanyId(companyId);
        BigDecimal closedWon = dealRepository.sumClosedWonByCompanyId(companyId);
        BigDecimal lostDealValue = dealRepository.sumLostDealValueByCompanyId(companyId);

        long openCount = dealRepository.countCoveredDealsByCompanyIdAndStatus(companyId, DealStatus.OPEN);
        long wonCount = dealRepository.countCoveredDealsByCompanyIdAndStatus(companyId, DealStatus.WON);
        long lostCount = dealRepository.countCoveredDealsByCompanyIdAndStatus(companyId, DealStatus.LOST);

        return PipelineSummaryResponse.builder()
                .pipelineCovered(pipelineCovered)
                .closedWon(closedWon)
                .lostDealValue(lostDealValue)
                .openDealCount(openCount)
                .wonDealCount(wonCount)
                .lostDealCount(lostCount)
                .build();
    }

    /**
     * Computes the complete, data-driven Revenue Intelligence breakdown (Phase 1).
     *
     * Semantics:
     * - Current State: Active Open Pipeline, Health-Weighted Pipeline, At-Risk Pipeline & Deals,
     *   Coverage against Target, Active Stage Breakdown (excludes CLOSED).
     * - Period Filtered: Closed Won, Closed Lost, Period Win Rate, Deals Created in Period.
     * - Conversation-Derived Signals: Pricing Pressure ($), Competitive Exposure ($), Engagement Trajectory.
     *
     * 100% deterministic & database-derived — zero fabricated numbers.
     */
    @Transactional(readOnly = true)
    public PipelineIntelligenceResponse getPipelineIntelligence(WorkspacePrincipal principal, String range) {
        Long companyId = principal.getCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // ── 1. Target & Period configuration ──────────────────────────────────
        String targetPeriod = (company.getRevenueTargetPeriod() != null && !company.getRevenueTargetPeriod().isBlank())
                ? company.getRevenueTargetPeriod().toUpperCase() : "QUARTERLY";

        BigDecimal revenueTarget = "MONTHLY".equals(targetPeriod)
                ? company.getMonthlyRevenueTarget()
                : company.getQuarterlyRevenueTarget();

        // If primary target is null, check if the other period target was set
        if (revenueTarget == null) {
            if ("MONTHLY".equals(targetPeriod) && company.getQuarterlyRevenueTarget() != null) {
                revenueTarget = company.getQuarterlyRevenueTarget();
                targetPeriod = "QUARTERLY";
            } else if ("QUARTERLY".equals(targetPeriod) && company.getMonthlyRevenueTarget() != null) {
                revenueTarget = company.getMonthlyRevenueTarget();
                targetPeriod = "MONTHLY";
            }
        }

        // ── 2. Parse Date Range Cutoffs ────────────────────────────────────────
        LocalDateTime startCutoff = null;
        LocalDateTime endCutoff = LocalDateTime.now();
        String periodLabel = "This Quarter";
        LocalDate today = LocalDate.now();

        String cleanRange = (range != null && !range.isBlank()) ? range.toLowerCase().trim() : "this_quarter";
        switch (cleanRange) {
            case "this_month", "month" -> {
                startCutoff = LocalDate.of(today.getYear(), today.getMonthValue(), 1).atStartOfDay();
                periodLabel = "This Month (" + today.getMonth().name().substring(0, 1) + today.getMonth().name().substring(1).toLowerCase() + ")";
            }
            case "last_quarter" -> {
                int currentQuarter = (today.getMonthValue() - 1) / 3 + 1;
                int lastQuarterYear = (currentQuarter == 1) ? today.getYear() - 1 : today.getYear();
                int lastQuarter = (currentQuarter == 1) ? 4 : currentQuarter - 1;
                int startMonth = (lastQuarter - 1) * 3 + 1;
                int endMonth = startMonth + 2;
                startCutoff = LocalDate.of(lastQuarterYear, startMonth, 1).atStartOfDay();
                endCutoff = LocalDate.of(lastQuarterYear, endMonth, LocalDate.of(lastQuarterYear, endMonth, 1).lengthOfMonth()).atTime(23, 59, 59);
                periodLabel = "Q" + lastQuarter + " " + lastQuarterYear;
            }
            case "30d" -> {
                startCutoff = LocalDateTime.now().minusDays(30);
                periodLabel = "Last 30 Days";
            }
            case "7d" -> {
                startCutoff = LocalDateTime.now().minusDays(7);
                periodLabel = "Last 7 Days";
            }
            case "all" -> {
                startCutoff = null;
                periodLabel = "All Time";
            }
            default -> { // "this_quarter", "quarter"
                int currentQuarter = (today.getMonthValue() - 1) / 3 + 1;
                int startMonth = (currentQuarter - 1) * 3 + 1;
                startCutoff = LocalDate.of(today.getYear(), startMonth, 1).atStartOfDay();
                periodLabel = "Q" + currentQuarter + " " + today.getYear();
            }
        }

        // ── 3. Load All Data for Company ───────────────────────────────────────
        List<Deal> allDeals = dealRepository.findByCompanyId(companyId);
        List<CallRecord> allCalls = callRecordRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);

        // Group calls by deal_id
        Map<Long, List<CallRecord>> callsByDealId = new HashMap<>();
        int callsAttachedToDealsCount = 0;
        for (CallRecord call : allCalls) {
            if (call.getDeal() != null) {
                callsAttachedToDealsCount++;
                callsByDealId.computeIfAbsent(call.getDeal().getId(), k -> new ArrayList<>()).add(call);
            }
        }

        // ── 4. Separate Open vs Closed Deals ───────────────────────────────────
        // Active open deals strictly exclude CLOSED stage / WON / LOST statuses
        List<Deal> openDeals = allDeals.stream()
                .filter(d -> d.getDealStatus() == DealStatus.OPEN && d.getDealStage() != DealStage.CLOSED)
                .toList();

        List<Deal> wonDeals = allDeals.stream()
                .filter(d -> d.getDealStatus() == DealStatus.WON || (d.getDealStage() == DealStage.CLOSED && d.getDealStatus() != DealStatus.LOST))
                .toList();

        List<Deal> lostDeals = allDeals.stream()
                .filter(d -> d.getDealStatus() == DealStatus.LOST)
                .toList();

        // ── 5. All-Time Won / Lost Totals ──────────────────────────────────────
        BigDecimal totalWonValue = wonDeals.stream()
                .map(Deal::getDealValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLostValue = lostDeals.stream()
                .map(Deal::getDealValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalWonDeals = wonDeals.size();
        long totalLostDeals = lostDeals.size();


        // ── 6. Period-Filtered Closed Won & Lost ───────────────────────────────
        final LocalDateTime filterStart = startCutoff;
        final LocalDateTime filterEnd = endCutoff;

        List<Deal> periodWon = wonDeals.stream().filter(d -> {
            LocalDateTime date = d.getUpdatedAt() != null ? d.getUpdatedAt() : d.getCreatedAt();
            if (date == null) return true;
            if (filterStart != null && date.isBefore(filterStart)) return false;
            if (filterEnd != null && date.isAfter(filterEnd)) return false;
            return true;
        }).toList();

        BigDecimal periodClosedWon = periodWon.stream()
                .map(Deal::getDealValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long periodClosedWonCount = periodWon.size();

        List<Deal> periodLost = lostDeals.stream().filter(d -> {
            LocalDateTime date = d.getUpdatedAt() != null ? d.getUpdatedAt() : d.getCreatedAt();
            if (date == null) return true;
            if (filterStart != null && date.isBefore(filterStart)) return false;
            if (filterEnd != null && date.isAfter(filterEnd)) return false;
            return true;
        }).toList();

        BigDecimal periodClosedLost = periodLost.stream()
                .map(Deal::getDealValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long periodClosedLostCount = periodLost.size();

        long periodClosedTotal = periodClosedWonCount + periodClosedLostCount;
        Double periodWinRatePct = null;
        if (periodClosedTotal > 0) {
            periodWinRatePct = Math.round((periodClosedWonCount / (double) periodClosedTotal) * 1000.0) / 10.0;
        }

        long periodDealsCreated = allDeals.stream().filter(d -> {
            LocalDateTime date = d.getCreatedAt();
            if (date == null) return true;
            if (filterStart != null && date.isBefore(filterStart)) return false;
            if (filterEnd != null && date.isAfter(filterEnd)) return false;
            return true;
        }).count();

        // ── 7. Evaluate Open Pipeline, Health-Weighted & At-Risk Deals ──────────
        BigDecimal totalOpenValue = BigDecimal.ZERO;
        BigDecimal healthWeightedPipeline = BigDecimal.ZERO;
        BigDecimal atRiskPipelineValue = BigDecimal.ZERO;
        long atRiskDealCount = 0;
        List<PipelineIntelligenceResponse.AtRiskDealItem> atRiskDealsList = new ArrayList<>();

        // Signal accumulators
        BigDecimal pricingPressureValue = BigDecimal.ZERO;
        int pricingPressureDeals = 0;
        BigDecimal competitiveExposureValue = BigDecimal.ZERO;
        int competitiveExposureDeals = 0;
        int healthyEngagementDeals = 0;
        int decliningEngagementDeals = 0;
        int unlinkedOpenDeals = 0;
        BigDecimal unlinkedOpenDealValue = BigDecimal.ZERO;

        for (Deal deal : openDeals) {
            BigDecimal val = deal.getDealValue() != null ? deal.getDealValue() : BigDecimal.ZERO;
            totalOpenValue = totalOpenValue.add(val);

            List<CallRecord> linkedCalls = callsByDealId.getOrDefault(deal.getId(), Collections.emptyList());
            int callCount = linkedCalls.size();

            if (callCount == 0) {
                unlinkedOpenDeals++;
                unlinkedOpenDealValue = unlinkedOpenDealValue.add(val);
            }

            // Find latest activity date
            LocalDateTime latestActivity = deal.getCreatedAt() != null ? deal.getCreatedAt() : LocalDateTime.now();
            Long latestCallId = null;
            String latestCallName = null;
            String latestSentiment = null;
            String latestBuyingIntent = null;
            boolean hasHighRiskFlag = false;
            boolean hasUnresolvedPricing = false;
            boolean hasCompetitorMention = false;

            if (!linkedCalls.isEmpty()) {
                CallRecord latestCall = linkedCalls.get(0); // already sorted desc
                latestCallId = latestCall.getId();
                latestCallName = latestCall.getFileName();
                latestSentiment = latestCall.getSentiment();
                latestBuyingIntent = latestCall.getBuyingIntent();
                if (latestCall.getCreatedAt() != null) {
                    latestActivity = latestCall.getCreatedAt();
                }

                // Analyze all calls for this deal
                for (CallRecord c : linkedCalls) {
                    // Risk flags
                    if (c.getRiskFlags() != null && !c.getRiskFlags().isBlank()) {
                        try {
                            List<Map<String, Object>> flags = MAPPER.readValue(c.getRiskFlags(),
                                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
                            for (Map<String, Object> f : flags) {
                                if ("High".equalsIgnoreCase(String.valueOf(f.getOrDefault("severity", "")))) {
                                    hasHighRiskFlag = true;
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    // Objections & competitor signals
                    if (c.getObjections() != null && !c.getObjections().isBlank()) {
                        try {
                            List<Map<String, Object>> objs = MAPPER.readValue(c.getObjections(),
                                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
                            for (Map<String, Object> o : objs) {
                                Object resolved = o.get("resolved");
                                boolean isUnresolved = (resolved == null || Boolean.FALSE.equals(resolved) || "false".equals(resolved.toString()));
                                String text = String.valueOf(o.getOrDefault("objection", "")).toLowerCase();
                                if (isUnresolved && (text.contains("price") || text.contains("budget") || text.contains("cost") || text.contains("expensive") || text.contains("afford"))) {
                                    hasUnresolvedPricing = true;
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    // Keywords / summary for competitor mentions
                    String kw = (c.getKeywords() != null ? c.getKeywords() : "") + " " + (c.getTranscript() != null ? c.getTranscript() : "");
                    String lowerKw = kw.toLowerCase();
                    if (lowerKw.contains("gong") || lowerKw.contains("chorus") || lowerKw.contains("salesforce") || lowerKw.contains("hubspot") || lowerKw.contains("competitor")) {
                        hasCompetitorMention = true;
                    }
                }
            }

            int daysInactive = (int) ChronoUnit.DAYS.between(latestActivity, LocalDateTime.now());
            if (daysInactive < 0) daysInactive = 0;

            // ── Health factor & Health-Weighted Calculation ───────────────────
            // Base stage probability:
            // DISCOVERY = 20%, DEMO = 40%, PROPOSAL = 60%, NEGOTIATION = 80%
            double stageProbability = switch (deal.getDealStage()) {
                case DISCOVERY -> 0.20;
                case DEMO -> 0.40;
                case PROPOSAL -> 0.60;
                case NEGOTIATION -> 0.80;
                default -> 0.20;
            };

            double healthFactor = 1.0;
            if (hasHighRiskFlag) healthFactor -= 0.25;
            if (hasUnresolvedPricing) healthFactor -= 0.20;
            if (daysInactive > 21) healthFactor -= 0.30;
            else if (daysInactive > 14) healthFactor -= 0.15;
            if ("Negative".equalsIgnoreCase(latestSentiment)) healthFactor -= 0.15;
            if ("High".equalsIgnoreCase(latestBuyingIntent) && daysInactive <= 7) healthFactor += 0.10;

            healthFactor = Math.max(0.20, Math.min(1.0, healthFactor)); // Clamp between 20% and 100%

            BigDecimal weightedDealValue = val.multiply(BigDecimal.valueOf(stageProbability * healthFactor))
                    .setScale(2, RoundingMode.HALF_UP);
            healthWeightedPipeline = healthWeightedPipeline.add(weightedDealValue);

            // ── At-Risk Qualification ─────────────────────────────────────────
            List<String> riskReasons = new ArrayList<>();
            if (hasHighRiskFlag) {
                riskReasons.add("High-severity risk flag detected on deal call");
            }
            if (hasUnresolvedPricing && (deal.getDealStage() == DealStage.PROPOSAL || deal.getDealStage() == DealStage.NEGOTIATION)) {
                riskReasons.add("Unresolved pricing/budget friction in late stage");
            }
            if (daysInactive > 21) {
                riskReasons.add("Critical inactivity: no call activity for " + daysInactive + " days");
            } else if (daysInactive > 14) {
                riskReasons.add("Stalled: no call activity for " + daysInactive + " days");
            }
            if ("Negative".equalsIgnoreCase(latestSentiment)) {
                riskReasons.add("Negative buyer sentiment on latest conversation");
            }
            if ("Low".equalsIgnoreCase(latestBuyingIntent) || "None".equalsIgnoreCase(latestBuyingIntent)) {
                riskReasons.add("Low or no buyer purchasing intent detected");
            }

            boolean isAtRisk = !riskReasons.isEmpty();
            if (isAtRisk) {
                atRiskDealCount++;
                atRiskPipelineValue = atRiskPipelineValue.add(val);

                String riskLevel = "MEDIUM";
                if (hasHighRiskFlag || daysInactive > 21 || (hasUnresolvedPricing && deal.getDealStage() == DealStage.NEGOTIATION)) {
                    riskLevel = "CRITICAL";
                } else if (daysInactive > 14 || hasUnresolvedPricing || "Negative".equalsIgnoreCase(latestSentiment)) {
                    riskLevel = "HIGH";
                }

                String dealDisplayName = deal.getDealName() != null && !deal.getDealName().isBlank()
                        ? deal.getDealName()
                        : (latestCallName != null ? latestCallName : "Deal #" + deal.getId());

                String ownerDisplayName = deal.getCreatedBy() != null
                        ? (deal.getCreatedBy().getName() != null ? deal.getCreatedBy().getName() : deal.getCreatedBy().getEmail())
                        : "Sales Rep";

                atRiskDealsList.add(PipelineIntelligenceResponse.AtRiskDealItem.builder()
                        .dealId(deal.getId())
                        .dealName(dealDisplayName)
                        .accountName(deal.getAccountName() != null ? deal.getAccountName() : company.getCompanyName())
                        .dealValue(val)
                        .stage(deal.getDealStage().name())
                        .stageLabel(formatStageLabel(deal.getDealStage()))
                        .ownerName(ownerDisplayName)
                        .riskLevel(riskLevel)
                        .daysSinceLastActivity(daysInactive)
                        .mainRiskReason(riskReasons.get(0))
                        .allRiskReasons(riskReasons)
                        .relatedCallCount(callCount)
                        .latestCallId(latestCallId)
                        .build());
            }

            // ── Signal aggregation ────────────────────────────────────────────
            if (hasUnresolvedPricing) {
                pricingPressureDeals++;
                pricingPressureValue = pricingPressureValue.add(val);
            }
            if (hasCompetitorMention && (deal.getDealStage() == DealStage.PROPOSAL || deal.getDealStage() == DealStage.NEGOTIATION)) {
                competitiveExposureDeals++;
                competitiveExposureValue = competitiveExposureValue.add(val);
            }
            if (isAtRisk || daysInactive > 14) {
                decliningEngagementDeals++;
            } else {
                healthyEngagementDeals++;
            }
        }

        // Sort at-risk deals: CRITICAL > HIGH > MEDIUM, then by deal value desc
        atRiskDealsList.sort((a, b) -> {
            int severityA = "CRITICAL".equals(a.getRiskLevel()) ? 3 : "HIGH".equals(a.getRiskLevel()) ? 2 : 1;
            int severityB = "CRITICAL".equals(b.getRiskLevel()) ? 3 : "HIGH".equals(b.getRiskLevel()) ? 2 : 1;
            if (severityA != severityB) return Integer.compare(severityB, severityA);
            return b.getDealValue().compareTo(a.getDealValue());
        });

        // ── 8. Active Stage Breakdown (Only active stages) ──────────────────────
        Map<DealStage, List<Deal>> dealsByStage = openDeals.stream()
                .collect(Collectors.groupingBy(Deal::getDealStage));

        List<PipelineIntelligenceResponse.StageBreakdown> stageBreakdown = new ArrayList<>();
        DealStage[] activeStages = { DealStage.DISCOVERY, DealStage.DEMO, DealStage.PROPOSAL, DealStage.NEGOTIATION };
        Map<DealStage, String> stageColors = Map.of(
                DealStage.DISCOVERY,   "#8b5cf6",
                DealStage.DEMO,        "#3b82f6",
                DealStage.PROPOSAL,    "#06b6d4",
                DealStage.NEGOTIATION, "#f59e0b"
        );

        for (DealStage stage : activeStages) {
            List<Deal> stageDeals = dealsByStage.getOrDefault(stage, Collections.emptyList());
            BigDecimal stageSum = stageDeals.stream()
                    .map(Deal::getDealValue)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            stageBreakdown.add(PipelineIntelligenceResponse.StageBreakdown.builder()
                    .stage(stage.name())
                    .stageLabel(formatStageLabel(stage))
                    .dealCount(stageDeals.size())
                    .totalValue(stageSum)
                    .color(stageColors.getOrDefault(stage, "#8b5cf6"))
                    .build());
        }

        // ── 9. Coverage Ratio & Gap to Target ──────────────────────────────────
        Double pipelineCoverageRatio = null;
        BigDecimal gapToTarget = null;
        if (revenueTarget != null && revenueTarget.compareTo(BigDecimal.ZERO) > 0) {
            pipelineCoverageRatio = totalOpenValue.divide(revenueTarget, 2, RoundingMode.HALF_UP).doubleValue();
            gapToTarget = revenueTarget.subtract(periodClosedWon.add(healthWeightedPipeline));
        }

        return PipelineIntelligenceResponse.builder()
                .revenueTarget(revenueTarget)
                .revenueTargetPeriod(targetPeriod)
                .pipelineCoverageRatio(pipelineCoverageRatio)
                .gapToTarget(gapToTarget)
                .totalOpenValue(totalOpenValue)
                .totalOpenDeals(openDeals.size())
                .healthWeightedPipeline(healthWeightedPipeline)
                .atRiskPipelineValue(atRiskPipelineValue)
                .atRiskDealCount(atRiskDealCount)
                .atRiskDeals(atRiskDealsList)
                .stageBreakdown(stageBreakdown)
                .periodRange(cleanRange)
                .periodLabel(periodLabel)
                .periodClosedWon(periodClosedWon)
                .periodClosedWonCount(periodClosedWonCount)
                .periodClosedLost(periodClosedLost)
                .periodClosedLostCount(periodClosedLostCount)
                .periodWinRatePct(periodWinRatePct)
                .periodDealsCreated(periodDealsCreated)
                .totalWonValue(totalWonValue)
                .totalLostValue(totalLostValue)
                .totalWonDeals(totalWonDeals)
                .totalLostDeals(totalLostDeals)
                .pricingPressureValue(pricingPressureValue)
                .pricingPressureDeals(pricingPressureDeals)
                .competitiveExposureValue(competitiveExposureValue)
                .competitiveExposureDeals(competitiveExposureDeals)
                .healthyEngagementDeals(healthyEngagementDeals)
                .decliningEngagementDeals(decliningEngagementDeals)
                .unlinkedOpenDeals(unlinkedOpenDeals)
                .unlinkedOpenDealValue(unlinkedOpenDealValue)
                .totalCalls(allCalls.size())
                .callsWithDeal(callsAttachedToDealsCount)
                .build();
    }

    private static String formatStageLabel(DealStage stage) {
        if (stage == null) return "";
        return switch (stage) {
            case DISCOVERY -> "Discovery";
            case DEMO -> "Demo";
            case PROPOSAL -> "Proposal";
            case NEGOTIATION -> "Negotiation";
            case CLOSED -> "Closed";
        };
    }
}
