package com.convexa.ai.convexa_ai_backend.config;

import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.entity.Role;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.Subscription;
import com.convexa.ai.convexa_ai_backend.entity.SubscriptionPlan;
import com.convexa.ai.convexa_ai_backend.entity.SubscriptionStatus;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ── Development-only demo data seeder ─────────────────────────────────────
 *
 * Only ever runs when the "dev" Spring profile is active
 * (--spring.profiles.active=dev, or an application-dev.properties on the
 * classpath). If no profile — or any profile other than "dev" — is active,
 * Spring never even constructs this bean; @Profile is evaluated before
 * CommandLineRunner.run() could ever execute, so production is safe by
 * construction, not by a runtime "if" check that could be forgotten.
 *
 * Idempotency (second safety layer, independent of the profile check):
 * every seeded user's email ends in "@convexa.demo". On startup this class
 * checks whether any such user already exists; if so, it does nothing.
 * That means restarting the dev server repeatedly never creates duplicate
 * demo data, and it never touches your one real employee or their calls —
 * it only ever looks for its own previously-seeded rows.
 *
 * Reuses, rather than duplicates:
 *   - UserRepository / CallRecordRepository — both already provide every
 *     method needed (findAll, save, count) via JpaRepository; no new
 *     repository methods were added.
 *   - User / Role / CallRecord entities — used exactly as they exist today.
 *     No new entity, no schema change.
 *   - PasswordEncoder bean — the same BCryptPasswordEncoder already defined
 *     in SecurityConfig, autowired here rather than re-instantiated.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String SEED_EMAIL_DOMAIN = "@convexa.demo";
    private static final String SEED_PASSWORD = "Demo@12345"; // shared, so any seeded account can be logged into for manual QA

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Random random = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Data pools — plain hand-written realism, no new dependency added ──
    private static final String[] FIRST_NAMES = {
            "Ava", "Liam", "Maya", "Noah", "Priya", "Ethan", "Zara", "Mason",
            "Layla", "Kabir", "Sophia", "Diego"
    };
    private static final String[] LAST_NAMES = {
            "Bennett", "Kapoor", "Nguyen", "Ortiz", "Sharma", "Klein", "Reyes",
            "Patel", "Coleman", "Iyer", "Fischer", "Rossi"
    };
    private static final String[] OUTCOME_STATUSES = { "Won", "Lost", "Follow Up Required", "Escalated", "Pending" };
    private static final String[] BUYING_INTENTS = { "High", "Medium", "Low", "None", "N/A" };
    private static final String[] CALL_TYPES = {
            "Inbound Support", "Outbound Sales", "Renewal", "Onboarding", "Escalation",
            "Collections", "Follow-Up", "Discovery", "Demo", "Negotiation"
    };
    private static final String[] KEYWORD_POOL = {
            "pricing", "renewal", "competitor", "discount", "onboarding", "integration",
            "support ticket", "upsell", "churn risk", "contract", "feature request",
            "billing", "escalation", "SLA", "roadmap"
    };
    private static final String[] SUMMARY_TEMPLATES = {
            "Customer asked about %s; rep walked through options and next steps.",
            "Call focused on %s — customer seemed engaged throughout.",
            "Discussion centered on %s, with some hesitation from the customer.",
            "Rep addressed concerns around %s and proposed a follow-up.",
            "Customer raised %s as a blocker; rep offered a workaround.",
    };
    private static final String[] FOLLOWUP_POOL = {
            "Send updated pricing sheet", "Schedule a technical demo", "Loop in account manager",
            "Share case study", "Follow up on contract redlines", "Confirm renewal date",
            "Send integration documentation", "Escalate to support team",
    };
    private static final String[] RISK_MESSAGES = {
            "Customer mentioned evaluating a competitor.", "Budget approval still pending.",
            "Champion may be leaving the company.", "Multiple unresolved support tickets.",
            "Contract renewal date is approaching with no commitment yet.",
    };

    @Override
    public void run(String... args) {
        boolean alreadySeeded = userRepository.findAll().stream()
                .anyMatch(u -> u.getEmail() != null && u.getEmail().endsWith(SEED_EMAIL_DOMAIN));

        if (alreadySeeded) {
            log.info("[DevDataSeeder] Demo data already present (found a user ending in {}) — skipping.", SEED_EMAIL_DOMAIN);
            return;
        }

        log.info("[DevDataSeeder] No demo data found — seeding employees and call records for local development.");

        List<User> employees = createEmployees();
        int totalCalls = 0;
        for (int i = 0; i < employees.size(); i++) {
            totalCalls += seedCallsFor(employees.get(i), performanceTierFor(i, employees.size()));
        }

        log.info("[DevDataSeeder] Done — seeded {} employees and {} call records.", employees.size(), totalCalls);
    }

    // ── Employees: 1 OWNER, 2 MANAGER, rest USER (10 total, within the 8-12 range) ──
    private List<User> createEmployees() {
        // 1. Create a Seed Company
        Company company = Company.builder()
                .companyName("Convexa Demo Org")
                .companySlug("convexa-demo")
                .industry("Technology")
                .website("https://convexa.ai")
                .companySize("50-200")
                .timezone("America/New_York")
                .status("ACTIVE")
                .onboardingCompleted(true)
                .profileCompletionPercentage(100)
                .build();
        Company savedCompany = companyRepository.save(company);

        // 2. Create Trial Subscription (Business, 25 seat limit, status TRIALING)
        LocalDateTime now = LocalDateTime.now();
        Subscription sub = Subscription.builder()
                .company(savedCompany)
                .plan(SubscriptionPlan.BUSINESS)
                .status(SubscriptionStatus.TRIALING)
                .trialStart(now.minusDays(2))
                .trialEnd(now.plusDays(12))
                .currentPeriodStart(now.minusDays(2))
                .currentPeriodEnd(now.plusDays(12))
                .seatLimit(25)
                .currentSeatCount(10)
                .trialReminderSent(false)
                .trialExpiredReminderSent(false)
                .build();
        subscriptionRepository.save(sub);
        savedCompany.setSubscription(sub);

        int employeeCount = 10;
        List<User> employees = new ArrayList<>();
        String encodedPassword = passwordEncoder.encode(SEED_PASSWORD);

        for (int i = 0; i < employeeCount; i++) {
            Role role = i == 0 ? Role.OWNER : (i == 1 || i == 2) ? Role.MANAGER : Role.USER;
            String first = FIRST_NAMES[i % FIRST_NAMES.length];
            String last = LAST_NAMES[i % LAST_NAMES.length];
            String name = first + " " + last;
            String email = (first + "." + last + i).toLowerCase() + SEED_EMAIL_DOMAIN;

            User user = User.builder()
                    .name(name)
                    .email(email)
                    .password(encodedPassword)
                    .role(role)
                    .company(savedCompany)
                    .provider("LOCAL")
                    .build();

            employees.add(userRepository.save(user));
        }
        return employees;
    }

    /** 0 = excellent, 1 = average, 2 = needs coaching — spread roughly a third each. */
    private int performanceTierFor(int index, int total) {
        double fraction = (double) index / total;
        if (fraction < 0.3) return 0;
        if (fraction < 0.7) return 1;
        return 2;
    }

    private int seedCallsFor(User employee, int tier) {
        int callCount = 15 + random.nextInt(26); // 15–40 inclusive

        for (int i = 0; i < callCount; i++) {
            CallRecord call = buildCall(employee, tier, i);
            callRecordRepository.save(call);
        }
        return callCount;
    }

    private CallRecord buildCall(User employee, int tier, int index) {
        int overallScore = scoreForTier(tier);
        String sentiment = sentimentForTier(tier);
        String outcomeStatus = pick(OUTCOME_STATUSES);
        String buyingIntent = pick(BUYING_INTENTS);
        String callType = pick(CALL_TYPES);
        String keyword = pick(KEYWORD_POOL);

        // createdAt spread across the last 30 days, random hour — see the
        // CallRecord.onCreate() null-check fix that makes this stick.
        LocalDateTime createdAt = LocalDateTime.now()
                .minusDays(random.nextInt(30))
                .minusHours(random.nextInt(24))
                .minusMinutes(random.nextInt(60));

        String fileName = String.format("call-%s-%03d.mp3", employee.getName().toLowerCase().replace(" ", "-"), index);
        String transcript = "[DEMO DATA] Transcript omitted for seeded call. See the summary field for a representative overview of this conversation.";
        String summary = String.format(pick(SUMMARY_TEMPLATES), keyword);

        return CallRecord.builder()
                .fileName(fileName)
                .transcript(transcript)
                .summary(summary)
                .sentiment(sentiment)
                .overallScore(overallScore)
                .communication(jitter(overallScore))
                .problemResolution(jitter(overallScore))
                .professionalism(jitter(overallScore))
                .customerSatisfaction(jitter(overallScore))
                .strengths(tier == 0 ? "Strong rapport building; clear articulation of value." : "Maintained professionalism under pressure.")
                .improvements(tier == 2 ? "Needs to listen more actively and slow down the pitch." : "Could tighten up next-steps summary at call close.")
                .keywords(String.join(", ", pickN(KEYWORD_POOL, 2 + random.nextInt(3))))
                .outcomeStatus(outcomeStatus)
                .actionItems(toJson(randomActionItems()))
                .riskFlags(toJson(randomRiskFlags(tier)))
                .followUpSuggestions(toJson(pickN(FOLLOWUP_POOL, 1 + random.nextInt(2))))
                .confidence(80 + random.nextInt(19))
                .callType(callType)
                .buyingIntent(buyingIntent)
                .buyingSignals(toJson(pickN(KEYWORD_POOL, 1 + random.nextInt(2))))
                .objections(toJson(randomObjections(tier)))
                .status("COMPLETED")
                .createdAt(createdAt)
                .user(employee)
                .build();
    }

    private int scoreForTier(int tier) {
        return switch (tier) {
            case 0 -> 90 + random.nextInt(9);   // 90–98
            case 1 -> 70 + random.nextInt(16);  // 70–85
            default -> 40 + random.nextInt(25); // 40–64
        };
    }

    private String sentimentForTier(int tier) {
        int roll = random.nextInt(100);
        return switch (tier) {
            case 0 -> roll < 75 ? "POSITIVE" : roll < 95 ? "NEUTRAL" : "NEGATIVE";
            case 1 -> roll < 45 ? "POSITIVE" : roll < 85 ? "NEUTRAL" : "NEGATIVE";
            default -> roll < 15 ? "POSITIVE" : roll < 45 ? "NEUTRAL" : "NEGATIVE";
        };
    }

    private int jitter(int base) {
        int value = base + (random.nextInt(21) - 10); // ± 10
        return Math.max(0, Math.min(100, value));
    }

    private List<Map<String, Object>> randomActionItems() {
        int count = 1 + random.nextInt(3);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", pick(FOLLOWUP_POOL));
            item.put("completed", random.nextBoolean());
            items.add(item);
        }
        return items;
    }

    private List<Map<String, String>> randomRiskFlags(int tier) {
        // Coaching-tier employees have more/higher-severity flags than top performers.
        int chance = switch (tier) { case 0 -> 20; case 1 -> 45; default -> 75; };
        if (random.nextInt(100) >= chance) return List.of();

        int count = 1 + (tier == 2 ? random.nextInt(2) : 0);
        List<Map<String, String>> flags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, String> flag = new LinkedHashMap<>();
            flag.put("severity", tier == 2 ? pick(new String[]{"High", "Medium"}) : pick(new String[]{"Low", "Medium"}));
            flag.put("message", pick(RISK_MESSAGES));
            flags.add(flag);
        }
        return flags;
    }

    private List<Map<String, Object>> randomObjections(int tier) {
        if (random.nextInt(100) >= 40) return List.of();
        Map<String, Object> objection = new LinkedHashMap<>();
        objection.put("objection", "Price is higher than expected for the feature set.");
        objection.put("resolved", tier != 2 && random.nextBoolean());
        return List.of(objection);
    }

    private String pick(String[] pool) {
        return pool[random.nextInt(pool.length)];
    }

    private List<String> pickN(String[] pool, int n) {
        List<String> copy = new ArrayList<>(Arrays.asList(pool));
        Collections.shuffle(copy, random);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[DevDataSeeder] Failed to serialize demo JSON field, storing empty array.", e);
            return "[]";
        }
    }
}
