package com.convexa.ai.convexa_ai_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for {@code GET /api/company/media-library}.
 *
 * <p>All fields are sourced from aggregation queries over {@code call_records} —
 * never from Cloudinary API calls at request time, and never fabricated.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code recordingCount}      — total call records for this workspace (COUNT(*))</li>
 *   <li>{@code trackedFileCount}    — recordings that have a known {@code fileSizeBytes} value</li>
 *   <li>{@code trackedStorageBytes} — SUM of {@code fileSizeBytes} for tracked files (never null; 0 if none)</li>
 *   <li>{@code unknownFileSizeCount}— recordings whose size is unknown (historical imports)</li>
 *   <li>{@code lastUploadAt}        — ISO-8601 timestamp of the most recent call record; null if no recordings</li>
 * </ul>
 *
 * <p>The frontend must render gracefully for all three valid states:
 * <ol>
 *   <li>recordingCount == 0                          → empty state</li>
 *   <li>recordingCount > 0 &amp;&amp; trackedFileCount == 0 → count shown, size unavailable</li>
 *   <li>recordingCount > 0 &amp;&amp; trackedFileCount > 0  → count + storage shown</li>
 * </ol>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class MediaLibraryResponse {

    private long   recordingCount;
    private long   trackedFileCount;
    private long   trackedStorageBytes;
    private long   unknownFileSizeCount;
    private String lastUploadAt;           // ISO-8601 string, or null when no recordings

    // ── constructors ─────────────────────────────────────────────────────────

    public MediaLibraryResponse() {}

    public MediaLibraryResponse(
            long   recordingCount,
            long   trackedFileCount,
            long   trackedStorageBytes,
            long   unknownFileSizeCount,
            String lastUploadAt
    ) {
        this.recordingCount       = recordingCount;
        this.trackedFileCount     = trackedFileCount;
        this.trackedStorageBytes  = trackedStorageBytes;
        this.unknownFileSizeCount = unknownFileSizeCount;
        this.lastUploadAt         = lastUploadAt;
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public long getRecordingCount()       { return recordingCount; }
    public void setRecordingCount(long v) { this.recordingCount = v; }

    public long getTrackedFileCount()       { return trackedFileCount; }
    public void setTrackedFileCount(long v) { this.trackedFileCount = v; }

    public long getTrackedStorageBytes()       { return trackedStorageBytes; }
    public void setTrackedStorageBytes(long v) { this.trackedStorageBytes = v; }

    public long getUnknownFileSizeCount()       { return unknownFileSizeCount; }
    public void setUnknownFileSizeCount(long v) { this.unknownFileSizeCount = v; }

    public String getLastUploadAt()            { return lastUploadAt; }
    public void   setLastUploadAt(String v)    { this.lastUploadAt = v; }
}
