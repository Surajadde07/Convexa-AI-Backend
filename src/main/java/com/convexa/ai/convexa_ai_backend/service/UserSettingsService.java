package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.UserSettingsResponse;
import com.convexa.ai.convexa_ai_backend.dto.UserSettingsUpdateRequest;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.entity.UserSettings;
import com.convexa.ai.convexa_ai_backend.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class UserSettingsService {

    private static final Set<String> VALID_THEMES = Set.of("dark", "light");
    private static final Set<String> VALID_EXPORT_FORMATS = Set.of("csv", "json");

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    /**
     * Every user gets a settings row lazily, on first read or write — there's
     * no need to create one at registration time, and this keeps User.java
     * untouched (no new required relationship to manage on signup).
     */
    @Transactional
    public UserSettings getOrCreate(User user) {
        return userSettingsRepository.findByUser(user)
                .orElseGet(() -> userSettingsRepository.save(
                        UserSettings.builder().user(user).build()
                ));
    }

    public UserSettingsResponse getSettings(User user) {
        return toResponse(getOrCreate(user));
    }

    @Transactional
    public UserSettingsResponse updateSettings(User user, UserSettingsUpdateRequest req) {
        UserSettings settings = getOrCreate(user);

        if (req.getTheme() != null) {
            String theme = req.getTheme().toLowerCase();
            if (!VALID_THEMES.contains(theme)) {
                throw new IllegalArgumentException("theme must be 'dark' or 'light'");
            }
            settings.setTheme(theme);
        }
        if (req.getNotifCallReady() != null) settings.setNotifCallReady(req.getNotifCallReady());
        if (req.getNotifNeedsAttention() != null) settings.setNotifNeedsAttention(req.getNotifNeedsAttention());
        if (req.getNotifWeeklyDigest() != null) settings.setNotifWeeklyDigest(req.getNotifWeeklyDigest());
        if (req.getDefaultLandingPage() != null) settings.setDefaultLandingPage(req.getDefaultLandingPage());
        if (req.getExportFormat() != null) {
            String fmt = req.getExportFormat().toLowerCase();
            if (!VALID_EXPORT_FORMATS.contains(fmt)) {
                throw new IllegalArgumentException("exportFormat must be 'csv' or 'json'");
            }
            settings.setExportFormat(fmt);
        }
        if (req.getShareAnonymizedData() != null) settings.setShareAnonymizedData(req.getShareAnonymizedData());

        return toResponse(userSettingsRepository.save(settings));
    }

    @Transactional
    public void deleteForUser(User user) {
        userSettingsRepository.findByUser(user).ifPresent(userSettingsRepository::delete);
    }

    private UserSettingsResponse toResponse(UserSettings s) {
        return UserSettingsResponse.builder()
                .theme(s.getTheme())
                .notifCallReady(s.getNotifCallReady())
                .notifNeedsAttention(s.getNotifNeedsAttention())
                .notifWeeklyDigest(s.getNotifWeeklyDigest())
                .defaultLandingPage(s.getDefaultLandingPage())
                .exportFormat(s.getExportFormat())
                .shareAnonymizedData(s.getShareAnonymizedData())
                .build();
    }
}
