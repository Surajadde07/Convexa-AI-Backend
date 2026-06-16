package com.convexa.ai.convexa_ai_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * CloudinaryService
 *
 * Handles all Cloudinary operations for Convexa AI:
 *   • uploadAudio  — uploads a MultipartFile to Cloudinary and returns
 *                    the secure URL and public_id.
 *   • deleteAudio  — deletes a previously uploaded file by its public_id.
 *
 * All files are stored in the "convexa-ai-recordings" folder on Cloudinary
 * and uploaded as resource_type "video" (Cloudinary's type for audio files).
 *
 * Constructor injection is used instead of @Autowired field injection
 * following Spring Boot best practices for testability and immutability.
 */
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Upload
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Uploads an audio MultipartFile to Cloudinary.
     *
     * @param  file  the audio file received from the HTTP multipart request
     * @return       {@link CloudinaryUploadResult} containing the secure HTTPS
     *               URL and the public_id needed for future deletion
     * @throws CloudinaryUploadException if the upload fails for any reason
     */
    public CloudinaryUploadResult uploadAudio(MultipartFile file) {
        try {
            // Cloudinary requires a byte array or a File object — we pass bytes
            // directly so no temporary file is written to local disk.
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            // Cloudinary treats audio files as "video" resource type
                            "resource_type", "video",

                            // Folder in Cloudinary where the file is stored
                            "folder",        "convexa-ai-recordings",

                            // Use the sanitised original filename (without extension)
                            // as the public_id so the file is human-readable in the
                            // Cloudinary media library.
                            "public_id",     buildPublicId(file.getOriginalFilename()),

                            // Overwrite if a file with the same public_id already exists
                            "overwrite",     true,

                            // Always use HTTPS
                            "secure",        true
                    )
            );

            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId  = (String) uploadResult.get("public_id");

            if (secureUrl == null || secureUrl.isBlank()) {
                throw new CloudinaryUploadException("Cloudinary returned an empty URL");
            }

            return new CloudinaryUploadResult(secureUrl, publicId);

        } catch (IOException e) {
            throw new CloudinaryUploadException(
                    "Failed to read audio file bytes: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new CloudinaryUploadException(
                    "Cloudinary upload failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Delete
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a previously uploaded audio file from Cloudinary.
     *
     * @param publicId the Cloudinary public_id stored in CallRecord.cloudinaryPublicId
     */
    public void deleteAudio(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;   // nothing to delete — silently ignore
        }
        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", "video")
            );
        } catch (Exception e) {
            // Log but do not rethrow — deletion failure must not block the
            // database record deletion. The orphaned Cloudinary asset can be
            // cleaned up manually from the media library if needed.
            System.err.println("[Cloudinary] deleteAudio failed for public_id="
                    + publicId + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Derive a URL-safe public_id from the original filename.
     * The folder prefix is NOT included here — Cloudinary prepends it via
     * the "folder" parameter in the upload call.
     */
    private String buildPublicId(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "recording_" + System.currentTimeMillis();
        }
        // Remove the file extension — Cloudinary adds it back automatically
        int dotIdx = originalFilename.lastIndexOf('.');
        String stem = dotIdx > 0 ? originalFilename.substring(0, dotIdx) : originalFilename;

        // Replace every unsafe character with underscore, collapse runs, trim edges
        String safe = stem.replaceAll("[^A-Za-z0-9.\\-]", "_")
                          .replaceAll("_+", "_")
                          .replaceAll("^_+|_+$", "");

        return safe.isEmpty() ? "recording_" + System.currentTimeMillis() : safe;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Result type
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Value object returned by {@link #uploadAudio}.
     */
    public record CloudinaryUploadResult(String secureUrl, String publicId) {}

    // ─────────────────────────────────────────────────────────────────────────
    //  Exception type
    // ─────────────────────────────────────────────────────────────────────────

    public static class CloudinaryUploadException extends RuntimeException {
        public CloudinaryUploadException(String message) {
            super(message);
        }
        public CloudinaryUploadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
