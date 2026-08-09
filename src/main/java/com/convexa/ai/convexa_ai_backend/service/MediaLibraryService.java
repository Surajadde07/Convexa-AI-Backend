package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.dto.MediaLibraryResponse;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * MediaLibraryService
 *
 * <p>Computes real, database-derived media library statistics for the
 * authenticated company workspace.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>All aggregation is done in a single JPQL query at the repository layer —
 *       no per-row Java iteration, no N+1 queries.</li>
 *   <li>File sizes come from metadata stored in {@code call_records.file_size_bytes}
 *       at upload time — Cloudinary's Admin API is never called at request time.</li>
 *   <li>Company isolation is enforced by always scoping to
 *       {@code principal.getCompanyId()} — the frontend never passes a company ID.</li>
 * </ul>
 */
@Service
public class MediaLibraryService {

    private final CallRecordRepository callRecordRepository;

    @Autowired
    public MediaLibraryService(CallRecordRepository callRecordRepository) {
        this.callRecordRepository = callRecordRepository;
    }

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Returns aggregated media library statistics for the workspace identified
     * by the authenticated principal's company ID.
     *
     * @param principal JWT-resolved workspace principal — must not be null.
     * @return {@link MediaLibraryResponse} with real database-derived values.
     */
    public MediaLibraryResponse getMediaLibrary(WorkspacePrincipal principal) {
        Long companyId = principal.getCompanyId();

        // Spring JPA returns List<Object[]> for multi-projection JPQL queries.
        // Our aggregation has no GROUP BY, so it always produces exactly one row.
        // We retrieve that row via get(0).
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = (java.util.List<Object[]>)
                (java.util.List<?>) callRecordRepository.getMediaLibraryStats(companyId);

        // If the result list is somehow empty (should never happen — COUNT(*) always returns a row),
        // return a safe zero-valued response rather than throwing.
        if (rows == null || rows.isEmpty()) {
            return new MediaLibraryResponse(0L, 0L, 0L, 0L, null);
        }

        Object[] row = rows.get(0);

        // All four columns are always present in the result array.
        // COUNT(*) returns Long 0 for empty tables, never null.
        long recordingCount      = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long trackedStorageBytes = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long trackedFileCount    = row[2] != null ? ((Number) row[2]).longValue() : 0L;

        // MAX(createdAt) returns null if the table is empty — this is correct and expected.
        String lastUploadAt = null;
        if (row[3] instanceof java.time.LocalDateTime ldt) {
            lastUploadAt = ldt.format(ISO_FORMATTER);
        }

        long unknownFileSizeCount = recordingCount - trackedFileCount;

        return new MediaLibraryResponse(
                recordingCount,
                trackedFileCount,
                trackedStorageBytes,
                unknownFileSizeCount,
                lastUploadAt
        );
    }

}
