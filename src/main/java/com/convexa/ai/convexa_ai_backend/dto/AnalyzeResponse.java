package com.convexa.ai.convexa_ai_backend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AnalyzeResponse {

    private String summary;

    private String sentiment;

    private String insights;

    private Integer overallScore;

    private Integer communication;

    private Integer problemResolution;

    private Integer professionalism;

    private Integer customerSatisfaction;

    private List<String> strengths;

    private List<String> improvements;

    // ── Existing Sprint 0 fields ───────────────────────────────────────────────
    //
    // keywords — returned by /analyze as a JSON array of strings.
    //   Jackson maps it to List<String>. The controller joins with ", " before
    //   saving to CallRecord.keywords (TEXT column).
    //
    // timeline — returned by /analyze as a JSON array of objects.
    //   Each element: {"time": "MM:SS", "title": "Phase Name"}.
    //   Map<String,String> is correct because both keys are always strings.
    //   The controller serializes to a JSON string for CallRecord.timeline.
    // ─────────────────────────────────────────────────────────────────────────
    private List<String> keywords;

    private List<Map<String, String>> timeline;

    // ── Sprint 1 new fields ────────────────────────────────────────────────────
    //
    // outcomeStatus — scalar string enum from FastAPI.
    //   One of: Won | Lost | Follow Up Required | Escalated | Pending
    //   Stored directly in CallRecord.outcomeStatus (TEXT column).
    //
    // actionItems — JSON array of objects: [{"title":"...","completed":false}]
    //   "completed" is a JSON boolean so we use Map<String,Object> to preserve
    //   the boolean type. Map<String,String> would coerce it to the string
    //   "false", breaking any frontend boolean check.
    //   The controller serializes to JSON string → CallRecord.actionItems.
    //
    // riskFlags — JSON array of objects: [{"severity":"High","message":"..."}]
    //   Both values are strings so Map<String,String> is correct here.
    //   The controller serializes to JSON string → CallRecord.riskFlags.
    //
    // followUpSuggestions — JSON array of plain strings.
    //   List<String> maps directly. Controller serializes → CallRecord.followUpSuggestions.
    //
    // confidence — integer 0-100. Jackson maps JSON number → Integer.
    //   Stored as Integer column (nullable). Old rows return null.
    //
    // callType — scalar string enum.
    //   One of: Inbound Support | Outbound Sales | Renewal | Onboarding |
    //   Escalation | Collections | Follow-Up | Discovery | Demo | Negotiation
    //   Stored directly in CallRecord.callType.
    //
    // buyingIntent — scalar string enum.
    //   One of: High | Medium | Low | None | N/A
    //   Stored directly in CallRecord.buyingIntent.
    //
    // buyingSignals — JSON array of plain strings.
    //   List<String> maps directly. Controller serializes → CallRecord.buyingSignals.
    //
    // objections — JSON array of objects: [{"objection":"...","resolved":true}]
    //   "resolved" is a JSON boolean so Map<String,Object> preserves the type.
    //   Controller serializes → CallRecord.objections.
    // ─────────────────────────────────────────────────────────────────────────

    private String outcomeStatus;

    private List<Map<String, Object>> actionItems;

    private List<Map<String, String>> riskFlags;

    private List<String> followUpSuggestions;

    private Integer confidence;

    private String callType;

    private String buyingIntent;

    private List<String> buyingSignals;

    private List<Map<String, Object>> objections;
}