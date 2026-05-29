package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.service.CallRecordService;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/calls")
@CrossOrigin("*")
public class CallRecordController {

    @Autowired
    private CallRecordService callRecordService;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/upload")
    public ResponseEntity<?> uploadAudio(
            @RequestParam("audio") MultipartFile file
    ) {

        try {

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

            String fileName = file.getOriginalFilename();

            Path filePath = Paths.get(uploadDir, fileName);

            Files.write(filePath, file.getBytes());

            // ===============================
            // SEND FILE TO FASTAPI SERVICE
            // ===============================

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            body.add("file", file.getResource());

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            String fastApiUrl = "http://127.0.0.1:8000/transcribe";

            ResponseEntity<String> response = restTemplate.postForEntity(
                    fastApiUrl,
                    requestEntity,
                    String.class
            );

            ObjectMapper objectMapper = new ObjectMapper();

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            String transcript = jsonNode.get("transcript").asText();

            // ===============================
            // SAVE TO DATABASE
            // ===============================

            CallRecord callRecord = CallRecord.builder()
                    .fileName(fileName)
                    .transcript(transcript)
                    .status("COMPLETED")
                    .build();

            callRecordService.saveCallRecord(callRecord);

            // ===============================
            // RETURN RESPONSE
            // ===============================

            return ResponseEntity.ok(transcript);

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
}