package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.Subscription;
import com.convexa.ai.convexa_ai_backend.entity.SubscriptionPlan;
import com.convexa.ai.convexa_ai_backend.entity.SubscriptionStatus;
import com.convexa.ai.convexa_ai_backend.repository.SubscriptionRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import com.convexa.ai.convexa_ai_backend.exception.SeatLimitExceededException;

@Service
public class SubscriptionService {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${convexa.subscription.trial-days:14}")
    private int trialDays;

    @Transactional
    public Subscription createTrialSubscription(Company company) {
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.builder()
                .company(company)
                .plan(SubscriptionPlan.BUSINESS)
                .status(SubscriptionStatus.TRIALING)
                .trialStart(now)
                .trialEnd(now.plusDays(trialDays))
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plusDays(trialDays))
                .seatLimit(25)
                .currentSeatCount(1)
                .trialReminderSent(false)
                .trialExpiredReminderSent(false)
                .build();

        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public void activateSubscription(Long subscriptionId, SubscriptionPlan plan, int seatLimit) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setSeatLimit(seatLimit);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void cancelSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void updateSeatCount(Long subscriptionId, int seatCount) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        subscription.setCurrentSeatCount(seatCount);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void incrementSeatCount(Long companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Subscription not found for company ID: " + companyId));
        if (subscription.getCurrentSeatCount() >= subscription.getSeatLimit()) {
            throw new SeatLimitExceededException("Workspace seat limit of " + subscription.getSeatLimit() + " has been reached. Upgrade your plan to add more members.");
        }
        subscription.setCurrentSeatCount(subscription.getCurrentSeatCount() + 1);
        subscriptionRepository.save(subscription);
    }

    /**
     * Proactive seat availability check — call this before creating an invitation.
     * Throws SeatLimitExceededException if the workspace has no remaining seats.
     * This prevents sending an email that cannot be accepted, protecting the employee
     * from a confusing UX and enforcing the Company-First billing model.
     */
    public void checkSeatAvailability(Long companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Subscription not found for company ID: " + companyId));
        if (subscription.getCurrentSeatCount() >= subscription.getSeatLimit()) {
            throw new SeatLimitExceededException(
                "This workspace has reached its member limit (" + subscription.getSeatLimit() +
                " seats). Upgrade your subscription to invite more members.");
        }
    }

    @Transactional
    public void decrementSeatCount(Long companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Subscription not found for company ID: " + companyId));
        subscription.setCurrentSeatCount(Math.max(0, subscription.getCurrentSeatCount() - 1));
        subscriptionRepository.save(subscription);
    }

    /**
     * Synchronizes currentSeatCount with the actual number of users
     * currently associated with the company.
     * Call this to correct stale values left by historical bugs
     * or any operation that bypassed the increment/decrement helpers.
     */
    @Transactional
    public int syncSeatCount(Long companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Subscription not found for company ID: " + companyId));
        int actualCount = (int) userRepository.countByCompanyId(companyId);
        subscription.setCurrentSeatCount(actualCount);
        subscriptionRepository.save(subscription);
        return actualCount;
    }
}
