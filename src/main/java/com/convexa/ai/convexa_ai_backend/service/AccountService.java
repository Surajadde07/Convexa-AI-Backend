package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.ChangePasswordRequest;
import com.convexa.ai.convexa_ai_backend.dto.UpdateProfileRequest;
import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.entity.UserSettings;
import com.convexa.ai.convexa_ai_backend.service.CallRecordService;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ── Required additions to files this service depends on but that weren't  ──
 * ── part of this upload — add these before compiling:                     ──
 *
 * UserRepository (existing interface):
 *   boolean existsByEmail(String email);
 *
 * CallRecordRepository (existing interface — referenced by CallRecordService
 * /CallRecordController, so it already exists; only the two methods below
 * are new):
 *   java.util.List<CallRecord> findByUser(User user);
 *   void deleteByUser(User user);
 *
 * PasswordEncoder: this service assumes a PasswordEncoder bean (BCrypt) is
 * already defined — the same one UserService.register() uses to hash
 * passwords on signup. If none exists yet, add to your security config:
 *   @Bean
 *   public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 */
@Service
public class AccountService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public User updateProfile(User user, UpdateProfileRequest req) {
        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(req.getName().trim());
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String newEmail = req.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmail(newEmail)) {
                    throw new IllegalArgumentException("That email is already in use.");
                }
                user.setEmail(newEmail);
            }
        }
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest req) {
        if ("GOOGLE".equalsIgnoreCase(user.getProvider())) {
            throw new IllegalArgumentException(
                "Your account is signed in with Google and doesn't have a Convexa password to change. " +
                "Manage your password from your Google Account instead."
            );
        }
        if (req.getCurrentPassword() == null || req.getNewPassword() == null) {
            throw new IllegalArgumentException("Both current and new password are required.");
        }
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (req.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        // The user's existing JWT remains valid — it's a stateless, signed token
        // keyed by email/id, not by password hash, so changing the password does
        // not invalidate sessions already issued. If you want "change password"
        // to force re-login everywhere, that requires a server-side token
        // denylist/version field, which is a bigger change than this endpoint.
    }

    /**
     * Deletes the user and everything that belongs to them. Order matters:
     * dependent rows (call records, settings) go first, the User row last,
     * so we never violate a foreign key constraint mid-transaction.
     */
    @Transactional
    public void deleteAccount(User user) {

        List<CallRecord> calls = callRecordService.getCallsByUserId(user.getId());

        for (CallRecord call : calls) {
            callRecordService.deleteCallRecord(call.getId());
        }

        userSettingsRepository.deleteByUser(user);
        userRepository.delete(user);
    }

    /**
     * Full account export. Call records already carry every per-call
     * "analytics"/"scorecard"/"AI insight" field (overallScore, outcomeStatus,
     * buyingIntent, riskFlags, etc.) directly on CallRecord — so exporting
     * the full call list covers call history, analytics, scorecards, and AI
     * insights in one pass rather than re-deriving them from four services.
     */
    public Map<String, Object> exportAccountData(User user) {
        Map<String, Object> export = new LinkedHashMap<>();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("createdAt", user.getCreatedAt());
        export.put("profile", profile);

        UserSettings settings = userSettingsRepository.findByUser(user).orElse(null);
        if (settings != null) {
            Map<String, Object> settingsMap = new LinkedHashMap<>();
            settingsMap.put("theme", settings.getTheme());
            settingsMap.put("notifCallReady", settings.getNotifCallReady());
            settingsMap.put("notifNeedsAttention", settings.getNotifNeedsAttention());
            settingsMap.put("notifWeeklyDigest", settings.getNotifWeeklyDigest());
            settingsMap.put("defaultLandingPage", settings.getDefaultLandingPage());
            settingsMap.put("exportFormat", settings.getExportFormat());
            settingsMap.put("shareAnonymizedData", settings.getShareAnonymizedData());
            export.put("settings", settingsMap);
        }

        List<CallRecord> calls = callRecordService.getCallsByUserId(user.getId());
        export.put("callHistory", calls.stream().map(this::callRecordToMap).collect(Collectors.toList()));

        return export;
    }

    private Map<String, Object> callRecordToMap(CallRecord c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("fileName", c.getFileName());
        m.put("createdAt", c.getCreatedAt());
        m.put("status", c.getStatus());
        m.put("summary", c.getSummary());
        m.put("sentiment", c.getSentiment());
        m.put("overallScore", c.getOverallScore());
        m.put("communication", c.getCommunication());
        m.put("problemResolution", c.getProblemResolution());
        m.put("professionalism", c.getProfessionalism());
        m.put("customerSatisfaction", c.getCustomerSatisfaction());
        m.put("strengths", c.getStrengths());
        m.put("improvements", c.getImprovements());
        m.put("keywords", c.getKeywords());
        m.put("outcomeStatus", c.getOutcomeStatus());
        m.put("actionItems", c.getActionItems());
        m.put("riskFlags", c.getRiskFlags());
        m.put("followUpSuggestions", c.getFollowUpSuggestions());
        m.put("confidence", c.getConfidence());
        m.put("callType", c.getCallType());
        m.put("buyingIntent", c.getBuyingIntent());
        m.put("buyingSignals", c.getBuyingSignals());
        m.put("objections", c.getObjections());
        return m;
    }
}
