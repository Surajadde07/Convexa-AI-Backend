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

    // ── NEW: added for Groq unified /analyze response ─────────────────────────
    //
    // keywords — the Groq prompt now returns keywords[] directly inside the
    //   /analyze JSON instead of requiring a separate /keywords API call.
    //   Jackson maps the JSON array straight to List<String>.
    //   The controller joins this list with ", " before saving to CallRecord.keywords.
    //
    // timeline — the Groq prompt now returns timeline[] inside /analyze.
    //   Each element is {"time": "MM:SS", "title": "Phase Name"}.
    //   We use Map<String,String> to avoid needing a new DTO class — the two
    //   keys "time" and "title" are always strings so Map<String,String> is
    //   the correct, minimal representation.
    //   The controller serializes this list to a JSON string before saving to
    //   CallRecord.timeline (TEXT column added in CallRecord.java).
    // ─────────────────────────────────────────────────────────────────────────
    private List<String> keywords;

    private List<Map<String, String>> timeline;
}
