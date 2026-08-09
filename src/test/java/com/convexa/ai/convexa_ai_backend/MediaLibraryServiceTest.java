package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.dto.MediaLibraryResponse;
import com.convexa.ai.convexa_ai_backend.entity.*;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.security.WorkspacePrincipal;
import com.convexa.ai.convexa_ai_backend.service.MediaLibraryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MediaLibraryService.
 *
 * Uses a real Spring context and @Transactional so every test rolls back,
 * ensuring clean isolation between test methods.
 */
@SpringBootTest
@Transactional
class MediaLibraryServiceTest {

    @Autowired
    private MediaLibraryService mediaLibraryService;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    private Company companyA;
    private Company companyB;
    private User    userA;
    private WorkspacePrincipal principalA;
    private WorkspacePrincipal principalB;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();

        companyA = companyRepository.save(Company.builder()
                .companyName("Media Lib Company A")
                .companySlug("ml-a-" + ts)
                .status(CompanyStatus.ACTIVE)
                .build());

        companyB = companyRepository.save(Company.builder()
                .companyName("Media Lib Company B")
                .companySlug("ml-b-" + ts)
                .status(CompanyStatus.ACTIVE)
                .build());

        userA = userRepository.save(User.builder()
                .email("owner.ml.a@" + ts + ".test")
                .name("Owner A")
                .password("Password@123")
                .role(Role.OWNER)
                .company(companyA)
                .build());

        principalA = WorkspacePrincipal.builder()
                .userId(userA.getId())
                .companyId(companyA.getId())
                .role(Role.OWNER)
                .email(userA.getEmail())
                .build();

        principalB = WorkspacePrincipal.builder()
                .userId(999999L)
                .companyId(companyB.getId())
                .role(Role.OWNER)
                .email("owner.b@test.com")
                .build();
    }

    /** Convenience builder for CallRecord with minimal required fields */
    private CallRecord buildCall(Company company, User user, Long fileSizeBytes) {
        return CallRecord.builder()
                .fileName("test_call_" + System.nanoTime() + ".mp3")
                .transcript("Hello world this is a test transcript.")
                .status("COMPLETED")
                .company(company)
                .user(user)
                .fileSizeBytes(fileSizeBytes)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: Company with 3 recordings — all with known file sizes
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void test_threeRecordingsWithKnownSizes_returnsCorrectAggregates() {
        callRecordRepository.save(buildCall(companyA, userA, 1_000_000L));   // 1 MB
        callRecordRepository.save(buildCall(companyA, userA, 2_000_000L));   // 2 MB
        callRecordRepository.save(buildCall(companyA, userA, 3_000_000L));   // 3 MB

        MediaLibraryResponse result = mediaLibraryService.getMediaLibrary(principalA);

        assertEquals(3,          result.getRecordingCount(),      "Should count 3 recordings");
        assertEquals(3,          result.getTrackedFileCount(),     "All 3 should be tracked");
        assertEquals(6_000_000L, result.getTrackedStorageBytes(),  "SUM = 6 MB");
        assertEquals(0,          result.getUnknownFileSizeCount(), "No unknown sizes");
        assertNotNull(result.getLastUploadAt(), "lastUploadAt should not be null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: Company with 0 recordings — empty state
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void test_noRecordings_returnsAllZerosAndNullTimestamp() {
        MediaLibraryResponse result = mediaLibraryService.getMediaLibrary(principalA);

        assertEquals(0,    result.getRecordingCount());
        assertEquals(0,    result.getTrackedFileCount());
        assertEquals(0L,   result.getTrackedStorageBytes());
        assertEquals(0,    result.getUnknownFileSizeCount());
        assertNull(result.getLastUploadAt(), "No recordings → lastUploadAt must be null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: All recordings have null fileSizeBytes (historical imports)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void test_allNullFileSizes_trackedCountIsZeroStorageBytesIsZero() {
        callRecordRepository.save(buildCall(companyA, userA, null));
        callRecordRepository.save(buildCall(companyA, userA, null));

        MediaLibraryResponse result = mediaLibraryService.getMediaLibrary(principalA);

        assertEquals(2, result.getRecordingCount(),       "2 recordings exist");
        assertEquals(0, result.getTrackedFileCount(),     "0 have a known size");
        assertEquals(0, result.getTrackedStorageBytes(),  "sum of known = 0");
        assertEquals(2, result.getUnknownFileSizeCount(), "2 are unknown");
        assertNotNull(result.getLastUploadAt());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: Mixed — some tracked, some null
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void test_mixedTrackedAndNull_correctPartialAggregates() {
        callRecordRepository.save(buildCall(companyA, userA, 5_000_000L));  // tracked
        callRecordRepository.save(buildCall(companyA, userA, null));         // unknown
        callRecordRepository.save(buildCall(companyA, userA, null));         // unknown

        MediaLibraryResponse result = mediaLibraryService.getMediaLibrary(principalA);

        assertEquals(3,          result.getRecordingCount());
        assertEquals(1,          result.getTrackedFileCount());
        assertEquals(5_000_000L, result.getTrackedStorageBytes());
        assertEquals(2,          result.getUnknownFileSizeCount());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5: Multi-tenant isolation — Company A cannot see Company B's data
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void test_multiTenantIsolation_companyACannotSeeCompanyBData() {
        // Company B has 2 recordings; Company A has 1
        User userB = userRepository.save(User.builder()
                .email("owner.b@" + System.currentTimeMillis() + ".test")
                .name("Owner B")
                .password("Password@123")
                .role(Role.OWNER)
                .company(companyB)
                .build());

        callRecordRepository.save(buildCall(companyB, userB, 10_000_000L));
        callRecordRepository.save(buildCall(companyB, userB, 20_000_000L));
        callRecordRepository.save(buildCall(companyA, userA, 1_000_000L));

        MediaLibraryResponse resultA = mediaLibraryService.getMediaLibrary(principalA);
        MediaLibraryResponse resultB = mediaLibraryService.getMediaLibrary(principalB);

        assertEquals(1, resultA.getRecordingCount(), "Company A should see only its 1 call");
        assertEquals(2, resultB.getRecordingCount(), "Company B should see only its 2 calls");
        assertEquals(1_000_000L,  resultA.getTrackedStorageBytes());
        assertEquals(30_000_000L, resultB.getTrackedStorageBytes());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6: Correct SUM — verify arithmetic
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void test_sumIsArithmeticallyCorrect() {
        callRecordRepository.save(buildCall(companyA, userA, 1_234_567L));
        callRecordRepository.save(buildCall(companyA, userA, 9_876_543L));

        MediaLibraryResponse result = mediaLibraryService.getMediaLibrary(principalA);

        assertEquals(11_111_110L, result.getTrackedStorageBytes(), "1234567 + 9876543 = 11111110");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7: Large file size — no integer overflow (BIGINT supports >2 GB)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void test_largeFileSizeDoesNotOverflow() {
        long fiveGigabytes = 5L * 1024L * 1024L * 1024L;  // 5,368,709,120 bytes
        callRecordRepository.save(buildCall(companyA, userA, fiveGigabytes));

        MediaLibraryResponse result = mediaLibraryService.getMediaLibrary(principalA);

        assertEquals(1, result.getRecordingCount());
        assertEquals(fiveGigabytes, result.getTrackedStorageBytes(),
                "5 GB should be stored and summed without overflow");
    }
}
