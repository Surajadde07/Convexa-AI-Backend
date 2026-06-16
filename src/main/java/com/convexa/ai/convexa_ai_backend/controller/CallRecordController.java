package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.AnalyzeResponse;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.service.CallRecordService;
import com.convexa.ai.convexa_ai_backend.service.CloudinaryService;
import com.convexa.ai.convexa_ai_backend.service.CloudinaryService.CloudinaryUploadResult;
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

import java.util.*;

@RestController
@RequestMapping("/api/calls")
@CrossOrigin("*")
public class CallRecordController {

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private UserRepository userRepository;

    // ── Cloudinary service — constructor injection per Spring Boot best practice ──
    private final CloudinaryService cloudinaryService;

    public CallRecordController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

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
            // UPLOAD AUDIO TO CLOUDINARY
            // ===============================
            //
            // Replaces the old local "uploads" folder write. The uploads
            // directory is no longer used anywhere in this controller.
            //
            // CloudinaryService.uploadAudio() reads file.getBytes() directly
            // (no temp file written to local disk) and returns the secure
            // HTTPS URL + public_id needed for later deletion.

            CloudinaryUploadResult uploadResult =
                    cloudinaryService.uploadAudio(file);

            String cloudinaryUrl      = uploadResult.secureUrl();
            String cloudinaryPublicId = uploadResult.publicId();

            // fileName is still stored for display purposes (e.g. "Recording 1.mp3")
            String fileName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "recording_" + System.currentTimeMillis() + ".mp3";

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
                    .cloudinaryUrl(cloudinaryUrl)
                    .cloudinaryPublicId(cloudinaryPublicId)
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
    //
    // Deletes the Cloudinary asset first, then the database row.
    // Cloudinary deletion failure is logged (inside CloudinaryService) but
    // never blocks the database deletion — an orphaned Cloudinary asset is
    // a much smaller problem than a record the user can't remove from the UI.
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

    // NOTE: filename sanitisation is no longer needed here. Cloudinary
    // generates its own safe public_id (see CloudinaryService.buildPublicId),
    // and the cloudinaryUrl returned by Cloudinary is already a complete,
    // correctly-encoded HTTPS URL — no manual encoding is required anywhere
    // this URL is consumed.


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