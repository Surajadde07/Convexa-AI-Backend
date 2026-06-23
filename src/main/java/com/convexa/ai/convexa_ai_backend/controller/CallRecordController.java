package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.AnalyzeResponse;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.CallRecordService;
import com.convexa.ai.convexa_ai_backend.service.CloudinaryService;
import com.convexa.ai.convexa_ai_backend.service.CloudinaryService.CloudinaryUploadResult;
import com.convexa.ai.convexa_ai_backend.dto.TranscriptRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import com.convexa.ai.convexa_ai_backend.entity.User;

import java.util.*;

// ── REMOVED IMPORTS ────────────────────────────────────────────────────────────
// QualityScoreResponse is no longer used — quality scores now come from
// AnalyzeResponse (which already contained them). QualityScoreResponse was
// only used by the old /quality-score proxy which has been commented out
// since the previous refactor.
//
// import com.convexa.ai.convexa_ai_backend.dto.QualityScoreResponse;
// ──────────────────────────────────────────────────────────────────────────────



@RestController
@RequestMapping("/api/calls")
@CrossOrigin("*")
public class CallRecordController {

    @Autowired
    private CallRecordService callRecordService;

    @Value("${ai.service.url}")
    private String aiServiceUrl;
    @Autowired
    private UserRepository userRepository;

    // ── Cloudinary service — constructor injection per Spring Boot best practice ──
    private final CloudinaryService cloudinaryService;

    public CallRecordController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    private final RestTemplate restTemplate = new RestTemplate();

    // ObjectMapper is reused across calls to avoid repeated instantiation cost.
    // It is thread-safe when used without reconfiguration.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/upload")
    public ResponseEntity<?> uploadAudio(
            @RequestParam("audio") MultipartFile file, HttpServletRequest request
    ) {

        try {

            String userEmail =
                    (String) request.getAttribute(
                            "userEmail"
                    );

            User user =
                    userRepository.findByEmail(
                            userEmail
                    ).orElseThrow(() ->
                            new RuntimeException(
                                    "User not found"
                            )
                    );

            // ===============================
            // UPLOAD AUDIO TO CLOUDINARY
            // ===============================

            CloudinaryUploadResult uploadResult =
                    cloudinaryService.uploadAudio(file);

            String cloudinaryUrl      = uploadResult.secureUrl();
            String cloudinaryPublicId = uploadResult.publicId();

            String fileName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "recording_" + System.currentTimeMillis() + ".mp3";

            // ===============================
            // STEP 1 — TRANSCRIBE (FastAPI /transcribe)
            // ===============================

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", file.getResource());

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            aiServiceUrl +"/transcribe",
                            requestEntity,
                            String.class
                    );

            JsonNode jsonNode =
                    objectMapper.readTree(response.getBody());

            String transcript =
                    jsonNode.get("transcript").asText();

            // ===============================
            // STEP 2 — UNIFIED ANALYSIS (FastAPI /analyze)
            //
            // One call now returns: summary, sentiment, insights, all scores,
            // strengths, improvements, keywords[], and timeline[].
            //
            // REMOVED: the separate POST to http://127.0.0.1:8000/keywords
            // that used to run after /analyze. keywords is now a field of
            // AnalyzeResponse (List<String> keywords) populated from the same
            // Groq call.
            // ===============================

            String analyzeUrl = aiServiceUrl + "/analyze";

            TranscriptRequest analyzeRequest =
                    new TranscriptRequest(transcript);

            HttpHeaders analyzeHeaders = new HttpHeaders();
            analyzeHeaders.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<TranscriptRequest> analyzeEntity =
                    new HttpEntity<>(analyzeRequest, analyzeHeaders);

            ResponseEntity<AnalyzeResponse> analyzeResponse =
                    restTemplate.postForEntity(
                            analyzeUrl,
                            analyzeEntity,
                            AnalyzeResponse.class
                    );

            AnalyzeResponse analyze = analyzeResponse.getBody();

            String strengthsJson = "[]";
            if (analyze.getStrengths() != null && !analyze.getStrengths().isEmpty()) {
                try {
                    strengthsJson = objectMapper.writeValueAsString(analyze.getStrengths());
                } catch (Exception e) {
                    strengthsJson = "[]";
                }
            }

            String improvementsJson = "[]";
            if (analyze.getImprovements() != null && !analyze.getImprovements().isEmpty()) {
                try {
                    improvementsJson = objectMapper.writeValueAsString(analyze.getImprovements());
                } catch (Exception e) {
                    improvementsJson = "[]";
                }
            }

            // ── OLD: separate /keywords call — REMOVED ────────────────────────
            //
            // Previously a second HTTP call was made to http://127.0.0.1:8000/keywords
            // which returned {"keywords": ["kw1", "kw2", ...]}.
            // That endpoint no longer exists in FastAPI — keywords are now
            // included in the /analyze response as analyze.getKeywords().
            //
            // String keywordsUrl = "http://127.0.0.1:8000/keywords";
            // Map<String, String> keywordsRequest = new HashMap<>();
            // keywordsRequest.put("transcript", transcript);
            // HttpHeaders keywordsHeaders = new HttpHeaders();
            // keywordsHeaders.setContentType(MediaType.APPLICATION_JSON);
            // HttpEntity<Map<String, String>> keywordsEntity = new HttpEntity<>(keywordsRequest, keywordsHeaders);
            // ResponseEntity<String> keywordsResponse = restTemplate.postForEntity(keywordsUrl, keywordsEntity, String.class);
            // JsonNode keywordsNode = objectMapper.readTree(keywordsResponse.getBody());
            // JsonNode keywordsArray = keywordsNode.get("keywords");
            // List<String> keywordList = new ArrayList<>();
            // keywordsArray.forEach(node -> keywordList.add(node.asText()));
            // String keywords = String.join(", ", keywordList);
            // ─────────────────────────────────────────────────────────────────

            // ── Build keywords string from /analyze response ──────────────────
            //
            // analyze.getKeywords() is a List<String> deserialized directly
            // from the Groq JSON. We join it with ", " to match the format
            // already stored in CallRecord.keywords (TEXT column).
            // Null-safe: if the model failed to return keywords we default to "".
            String keywords = (analyze.getKeywords() != null && !analyze.getKeywords().isEmpty())
                    ? String.join(", ", analyze.getKeywords())
                    : "";

            // ── Serialize timeline to JSON string ─────────────────────────────
            //
            // analyze.getTimeline() is List<Map<String,String>>.
            // We serialize it to a compact JSON string for storage in the
            // CallRecord.timeline TEXT column.
            // The frontend will JSON.parse() it back to an array.
            // If the model returned no timeline, store "[]" so JSON.parse
            // never throws on the frontend.
            String timelineJson = "[]";
            if (analyze.getTimeline() != null && !analyze.getTimeline().isEmpty()) {
                try {
                    timelineJson = objectMapper.writeValueAsString(analyze.getTimeline());
                } catch (Exception e) {
                    // Non-fatal — an empty timeline is better than a failed upload
                    timelineJson = "[]";
                }
            }

            // ===============================
            // SAVE TO DATABASE
            // ===============================

            CallRecord callRecord = CallRecord.builder()
                    .fileName(fileName)
                    .cloudinaryUrl(cloudinaryUrl)
                    .cloudinaryPublicId(cloudinaryPublicId)
                    .transcript(transcript)
                    .summary(analyze.getSummary())
                    .sentiment(analyze.getSentiment())
                    .insights(analyze.getInsights())
                    .overallScore(analyze.getOverallScore())
                    .communication(analyze.getCommunication())
                    .problemResolution(analyze.getProblemResolution())
                    .professionalism(analyze.getProfessionalism())
                    .customerSatisfaction(analyze.getCustomerSatisfaction())
                    .strengths(strengthsJson)       // CHANGED: JSON array string, not comma-joined
                    .improvements(improvementsJson) // CHANGED: JSON array string, not comma-joined
                    .keywords(keywords)
                    .timeline(timelineJson) // NEW — persisted from /analyze response
                    .status("COMPLETED")
                    .user(user)
                    .build();

            callRecordService.saveCallRecord(callRecord);

            // ===============================
            // RETURN RESPONSE
            // ===============================

            return ResponseEntity.ok(callRecord);

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body("Error: " + e.getMessage());
        }
    }

    // SAVE CALL RECORD
    @PostMapping
    public CallRecord saveCallRecord(
            @Valid @RequestBody CallRecord callRecord
    ) {
        return callRecordService.saveCallRecord(callRecord);
    }

    // GET ALL CALL RECORDS
    @GetMapping
    public List<CallRecord> getAllCallRecords() {
        return callRecordService.getAllCallRecords();
    }

    // GET CALL RECORD BY ID
    @GetMapping("/{id}")
    public CallRecord getCallRecordById(@PathVariable Long id) {
        return callRecordService.getCallRecordById(id);
    }

    // DELETE CALL RECORD
    //
    // Deletes the Cloudinary asset first, then the database row.
    @DeleteMapping("/{id}")
    public String deleteCallRecord(@PathVariable Long id) {
        CallRecord existing = callRecordService.getCallRecordById(id);

        if (existing != null && existing.getCloudinaryPublicId() != null) {
            cloudinaryService.deleteAudio(existing.getCloudinaryPublicId());
        }

        callRecordService.deleteCallRecord(id);
        return "Call Record Deleted Successfully";
    }

    @GetMapping("/my-calls")
    public List<CallRecord> getMyCalls(
            HttpServletRequest request
    ) {

        String email =
                (String) request.getAttribute(
                        "userEmail"
                );

        System.out.println("EMAIL FROM JWT = " + email);

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        System.out.println("USER FOUND = " + user.getEmail());

        return callRecordService
                .getCallsByUserId(
                        user.getId()
                );
    }

    // ── REMOVED: POST /api/calls/timeline ─────────────────────────────────────
    //
    // This endpoint proxied to http://127.0.0.1:8000/timeline which no longer
    // exists in the FastAPI service (timeline is now returned as part of the
    // unified /analyze response and stored in CallRecord.timeline).
    //
    // The frontend (CallDetailsPage.jsx) previously fetched this on-demand when
    // the Timeline tab was opened. It now reads call.timeline directly from the
    // CallRecord object already loaded at page load — no extra fetch needed.
    //
    // @PostMapping("/timeline")
    // public ResponseEntity<?> generateTimeline(@RequestBody Map<String, String> body) {
    //     try {
    //         String transcript = body.get("transcript");
    //         if (transcript == null || transcript.isBlank()) {
    //             return ResponseEntity.badRequest().body("transcript is required");
    //         }
    //         HttpHeaders headers = new HttpHeaders();
    //         headers.setContentType(MediaType.APPLICATION_JSON);
    //         HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("transcript", transcript), headers);
    //         ResponseEntity<String> response = restTemplate.postForEntity(
    //                 "http://127.0.0.1:8000/timeline", request, String.class);
    //         return ResponseEntity.status(response.getStatusCode())
    //                 .contentType(MediaType.APPLICATION_JSON)
    //                 .body(response.getBody());
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         return ResponseEntity.ok("[]");
    //     }
    // }
    // ─────────────────────────────────────────────────────────────────────────
}
