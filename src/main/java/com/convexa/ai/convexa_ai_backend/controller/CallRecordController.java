package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.AnalyzeResponse;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.CallRecordService;
import com.convexa.ai.convexa_ai_backend.dto.QualityScoreResponse;
import com.convexa.ai.convexa_ai_backend.dto.TranscriptRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/calls")
@CrossOrigin("*")
public class CallRecordController {

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private UserRepository userRepository;

    private final RestTemplate restTemplate = new RestTemplate();

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
            // CREATE UPLOADS FOLDER
            // ===============================

            String uploadDir = "uploads";

            File directory = new File(uploadDir);

            if (!directory.exists()) {
                directory.mkdirs();
            }

            // ===============================
            // SAVE FILE
            // ===============================

            // ── Issue #1 fix: sanitise filename before storing ──────────────
            // Raw filenames may contain spaces, '#', '?' and other characters
            // that are illegal / misinterpreted in URL path segments.
            // '#' is the worst offender: the browser strips everything from '#'
            // onward as a fragment BEFORE sending the HTTP request, so the
            // server never receives the full path and returns 404.
            String rawName    = file.getOriginalFilename();
            String fileName   = sanitiseFileName(rawName);
            // ───────────────────────────────────────────────────────────────

            Path filePath = Paths.get(uploadDir, fileName);

            Files.write(filePath, file.getBytes());

            String audioUrl = "/audio/" + fileName;

            // ===============================
            // SEND FILE TO FASTAPI (/transcribe)
            // ===============================

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            body.add("file", file.getResource());

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            "http://127.0.0.1:8000/transcribe",
                            requestEntity,
                            String.class
                    );

            ObjectMapper objectMapper = new ObjectMapper();

            JsonNode jsonNode =
                    objectMapper.readTree(response.getBody());

            String transcript =
                    jsonNode.get("transcript").asText();

            // ANalyze api

            String analyzeUrl =
                    "http://127.0.0.1:8000/analyze";

            TranscriptRequest analyzeRequest =
                    new TranscriptRequest(
                            transcript
                    );

            HttpHeaders analyzeHeaders =
                    new HttpHeaders();

            analyzeHeaders.setContentType(
                    MediaType.APPLICATION_JSON
            );

            HttpEntity<TranscriptRequest> analyzeEntity =
                    new HttpEntity<>(
                            analyzeRequest,
                            analyzeHeaders
                    );

            ResponseEntity<AnalyzeResponse> analyzeResponse =
                    restTemplate.postForEntity(
                            analyzeUrl,
                            analyzeEntity,
                            AnalyzeResponse.class
                    );

            AnalyzeResponse analyze =
                    analyzeResponse.getBody();


            // ===============================
            // CALL SUMMARY API
            // ===============================

//            HttpHeaders summaryHeaders = new HttpHeaders();
//            summaryHeaders.setContentType(MediaType.APPLICATION_JSON);
//
//            String summaryRequestBody =
//                    """
//                    {
//                        "transcript": %s
//                    }
//                    """.formatted(
//                            objectMapper.writeValueAsString(transcript)
//                    );
//
//            HttpEntity<String> summaryRequest =
//                    new HttpEntity<>(
//                            summaryRequestBody,
//                            summaryHeaders
//                    );
//
//            ResponseEntity<String> summaryResponse =
//                    restTemplate.postForEntity(
//                            "http://127.0.0.1:8000/summary",
//                            summaryRequest,
//                            String.class
//                    );
//
//            JsonNode summaryJson =
//                    objectMapper.readTree(summaryResponse.getBody());
//
//            String summary =
//                    summaryJson.get("summary").asText();
//
//            String sentimentUrl = "http://127.0.0.1:8000/sentiment";
//
//            String sentimentRequest =
//                    "{\"transcript\":\"" +
//                            transcript.replace("\"", "\\\"")
//                            + "\"}";
//
//            HttpHeaders sentimentHeaders = new HttpHeaders();
//            sentimentHeaders.setContentType(MediaType.APPLICATION_JSON);
//
//            HttpEntity<String> sentimentEntity =
//                    new HttpEntity<>(sentimentRequest, sentimentHeaders);
//
//            ResponseEntity<String> sentimentResponse =
//                    restTemplate.postForEntity(
//                            sentimentUrl,
//                            sentimentEntity,
//                            String.class
//                    );
//
//            JsonNode sentimentNode =
//                    objectMapper.readTree(sentimentResponse.getBody());
//
//            String sentiment =
//                    sentimentNode.get("sentiment").asText();
//
//            // ===============================
//            // CALL INSIGHTS API
//            // ===============================
//
//            String insightsUrl = "http://127.0.0.1:8000/insights";
//
//            Map<String, String> insightsRequest =
//                    new HashMap<>();
//
//            insightsRequest.put(
//                    "transcript",
//                    transcript
//            );
//
//            HttpHeaders insightsHeaders =
//                    new HttpHeaders();
//
//            insightsHeaders.setContentType(
//                    MediaType.APPLICATION_JSON
//            );
//
//            HttpEntity<Map<String, String>> insightsEntity =
//                    new HttpEntity<>(
//                            insightsRequest,
//                            insightsHeaders
//                    );
//
//            ResponseEntity<String> insightsResponse =
//                    restTemplate.postForEntity(
//                            insightsUrl,
//                            insightsEntity,
//                            String.class
//                    );
//
//            JsonNode insightsNode =
//                    objectMapper.readTree(
//                            insightsResponse.getBody()
//                    );
//
//            String insights =
//                    insightsNode.get("insights")
//                            .asText();
//
//            // ===============================
//            // CALL QUALITY SCORE API
//            // ===============================
//
//            String qualityScoreUrl =
//                    "http://127.0.0.1:8000/quality-score";
//
//            TranscriptRequest qualityRequest =
//                    new TranscriptRequest(transcript);
//
//            HttpHeaders qualityHeaders =
//                    new HttpHeaders();
//
//            qualityHeaders.setContentType(
//                    MediaType.APPLICATION_JSON
//            );
//
//            HttpEntity<TranscriptRequest> qualityEntity =
//                    new HttpEntity<>(
//                            qualityRequest,
//                            qualityHeaders
//                    );
//
//            ResponseEntity<QualityScoreResponse> qualityResponse =
//                    restTemplate.postForEntity(
//                            qualityScoreUrl,
//                            qualityEntity,
//                            QualityScoreResponse.class
//                    );
//
//            QualityScoreResponse qualityScore =
//                    qualityResponse.getBody();

            // ===============================
            // Keywords Extraction
            // ===============================


            String keywordsUrl =
                    "http://127.0.0.1:8000/keywords";

            Map<String, String> keywordsRequest =
                    new HashMap<>();

            keywordsRequest.put(
                    "transcript",
                    transcript
            );

            HttpHeaders keywordsHeaders =
                    new HttpHeaders();

            keywordsHeaders.setContentType(
                    MediaType.APPLICATION_JSON
            );

            HttpEntity<Map<String, String>> keywordsEntity =
                    new HttpEntity<>(
                            keywordsRequest,
                            keywordsHeaders
                    );

            ResponseEntity<String> keywordsResponse =
                    restTemplate.postForEntity(
                            keywordsUrl,
                            keywordsEntity,
                            String.class
                    );

            JsonNode keywordsNode =
                    objectMapper.readTree(
                            keywordsResponse.getBody()
                    );

            JsonNode keywordsArray =
                    keywordsNode.get("keywords");


            List<String> keywordList =
                    new ArrayList<>();

            keywordsArray.forEach(node ->
                    keywordList.add(node.asText()));

            String keywords =
                    String.join(", ", keywordList);

            // ===============================
            // SAVE TO DATABASE
            // ===============================

            CallRecord callRecord = CallRecord.builder()
                    .fileName(fileName)
                    .filePath(audioUrl)
                    .transcript(transcript)
                    .summary(
                            analyze.getSummary()
                    )

                    .sentiment(
                            analyze.getSentiment()
                    )

                    .insights(
                            analyze.getInsights()
                    )

                    .overallScore(
                            analyze.getOverallScore()
                    )

                    .communication(
                            analyze.getCommunication()
                    )

                    .problemResolution(
                            analyze.getProblemResolution()
                    )

                    .professionalism(
                            analyze.getProfessionalism()
                    )

                    .customerSatisfaction(
                            analyze.getCustomerSatisfaction()
                    )

                    .strengths(
                            String.join(
                                    ", ",
                                    analyze.getStrengths()
                            )
                    )

                    .improvements(
                            String.join(
                                    ", ",
                                    analyze.getImprovements()
                            )
                    )
                    .keywords(keywords)
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
    @DeleteMapping("/{id}")
    public String deleteCallRecord(@PathVariable Long id) {
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Issue #1 fix — URL-safe filename sanitiser
    //
    //  Replaces every character that is problematic in an HTTP path segment:
    //    • space → _  (unencoded spaces break URL parsing)
    //    • #     → _  (browser treats as fragment; NEVER sent to server)
    //    • ?     → _  (query-string delimiter)
    //    • and any other non-alphanumeric char except . - _
    //
    //  The file extension (last dot segment) is always preserved unchanged.
    //  Consecutive underscores are collapsed, and leading/trailing ones trimmed.
    // ─────────────────────────────────────────────────────────────────────────
    private String sanitiseFileName(String original) {
        if (original == null || original.isBlank()) {
            return "recording_" + System.currentTimeMillis() + ".mp3";
        }
        int dotIdx  = original.lastIndexOf('.');
        String stem = dotIdx > 0 ? original.substring(0, dotIdx) : original;
        String ext  = dotIdx > 0 ? original.substring(dotIdx)    : "";

        // Keep only alphanumeric, dot, hyphen, underscore — replace everything else
        String safeStem = stem.replaceAll("[^A-Za-z0-9.\\-]", "_");

        // Collapse consecutive underscores and trim edge underscores
        safeStem = safeStem.replaceAll("_+", "_").replaceAll("^_+|_+$", "");

        if (safeStem.isEmpty()) {
            safeStem = "recording_" + System.currentTimeMillis();
        }

        return safeStem + ext;
    }


    @PostMapping("/timeline")
    public ResponseEntity<?> generateTimeline(
            @RequestBody Map<String, String> body
    ) {
        try {
            String transcript = body.get("transcript");
            if (transcript == null || transcript.isBlank()) {
                return ResponseEntity.badRequest().body("transcript is required");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(
                    Map.of("transcript", transcript),
                    headers
            );

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "http://127.0.0.1:8000/timeline",
                    request,
                    String.class
            );

            // Return the FastAPI response directly — it's already a JSON array
            return ResponseEntity
                    .status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());

        } catch (Exception e) {
            e.printStackTrace();
            // Return an empty timeline instead of an error so the UI degrades gracefully
            return ResponseEntity.ok("[]");
        }
    }
}