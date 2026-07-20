package com.convexa.ai.convexa_ai_backend.controller;

import com.convexa.ai.convexa_ai_backend.dto.AcceptInvitationRequest;
import com.convexa.ai.convexa_ai_backend.dto.InvitationResponse;
import com.convexa.ai.convexa_ai_backend.service.InvitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.convexa.ai.convexa_ai_backend.exception.SeatLimitExceededException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/invitations")
@CrossOrigin("*")
public class PublicInvitationController {

    @Autowired
    private InvitationService invitationService;

    @GetMapping("/{token}")
    public ResponseEntity<InvitationResponse> getInvitationByToken(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.getInvitationByToken(token));
    }

    @PostMapping("/accept")
    public ResponseEntity<?> acceptInvitation(@RequestBody AcceptInvitationRequest req) {
        invitationService.acceptInvitation(req);
        return ResponseEntity.ok(Map.of("message", "Invitation accepted successfully, user created"));
    }

    @ExceptionHandler(SeatLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleSeatLimitExceeded(SeatLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
