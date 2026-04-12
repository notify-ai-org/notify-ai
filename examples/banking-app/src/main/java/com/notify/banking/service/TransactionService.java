package com.notify.banking.service;

import com.notify.agent.annotations.*;
import com.notify.agent.models.subject.EmailSubject;
import com.notify.agent.models.subject.SmsSubject;
import com.notify.agent.models.subject.Subject;
import com.notify.banking.model.*;
import com.notify.banking.store.AccountStore;
import com.notify.banking.store.TransactionStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core banking service that exercises all Notify SDK annotations.
 *
 * Events:
 *   - SUSPICIOUS_LOGIN       (critical SMS + email)
 *   - OTP_REQUESTED          (immediate SMS)
 *   - LARGE_TRANSFER         (email with fraud + velocity rules)
 *   - DAILY_BALANCE_SUMMARY  (scheduled/cron daily digest)
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountStore accounts;
    private final TransactionStore transactions;

    public TransactionService(AccountStore accounts, TransactionStore transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    // ═══════════════════════════════════════════
    // EVENTS
    // ═══════════════════════════════════════════

    @Event(
        key = "SUSPICIOUS_LOGIN",
        description = "Suspicious login attempt detected on an account",
        eventType = "static",
        scheduleIntent = "immediate",
        preferredTimeWindow = "00:00-23:59",
        priority = 10,
        payload = LoginPayload.class
    )
    public LoginPayload flagSuspiciousLogin(LoginPayload payload) {
        log.warn("Suspicious login detected for user {} from {} ({})", payload.getUserId(), payload.getIpAddress(), payload.getLocation());
        return payload;
    }

    @Event(
        key = "OTP_REQUESTED",
        description = "User requested a one-time password",
        eventType = "static",
        scheduleIntent = "immediate",
        preferredTimeWindow = "00:00-23:59",
        priority = 10,
        payload = OtpPayload.class
    )
    public OtpPayload requestOtp(OtpPayload payload) {
        log.info("OTP requested for user {} via {} — code: {}", payload.getUserId(), payload.getChannel(), payload.getOtpCode());
        return payload;
    }

    @Event(
        key = "LARGE_TRANSFER",
        description = "Large fund transfer initiated",
        eventType = "static",
        scheduleIntent = "immediate",
        preferredTimeWindow = "09:00-17:00",
        priority = 8,
        payload = TransactionPayload.class
    )
    public TransactionPayload processLargeTransfer(TransactionPayload payload) {
        log.info("Large transfer: {} -> {} — ${} {}", payload.getFromAccountId(), payload.getToAccountId(), payload.getAmount(), payload.getCurrency());
        transactions.save(payload);
        return payload;
    }

    @Event(
        key = "DAILY_BALANCE_SUMMARY",
        description = "Daily account balance summary for digest notifications",
        eventType = "deferred",
        scheduleIntent = "scheduled",
        preferredTimeWindow = "06:00-08:00",
        priority = 2,
        payload = BalanceSummaryPayload.class
    )
    public BalanceSummaryPayload generateDailySummary(BalanceSummaryPayload payload) {
        log.info("Daily summary for account {}: ${} {}", payload.getAccountId(), payload.getBalance(), payload.getCurrency());
        return payload;
    }

    // ═══════════════════════════════════════════
    // SUBJECT SUPPLIERS
    // ═══════════════════════════════════════════

    @SubjectSupplier(event = "SUSPICIOUS_LOGIN", description = "Resolves account holder to SMS + email for security alerts")
    public List<Subject> getSuspiciousLoginSubjects(LoginPayload payload) {
        Account acc = accounts.get(payload.getUserId());
        if (acc == null) return List.of();
        List<Subject> subjects = new ArrayList<>();
        // SMS for urgent alert
        subjects.add(new SmsSubject(acc.getId(), acc.getPhone(), null, Map.of("holderName", acc.getHolderName())));
        // Email for detailed report
        subjects.add(new EmailSubject(acc.getId(), acc.getEmail(), null, null, null, Map.of("holderName", acc.getHolderName())));
        return subjects;
    }

    @SubjectSupplier(event = "OTP_REQUESTED", description = "Resolves user to SMS for OTP delivery")
    public List<Subject> getOtpSubjects(OtpPayload payload) {
        Account acc = accounts.get(payload.getUserId());
        if (acc == null) return List.of();
        return List.of(new SmsSubject(acc.getId(), acc.getPhone(), null, Map.of("holderName", acc.getHolderName())));
    }

    @SubjectSupplier(event = "LARGE_TRANSFER", description = "Resolves sender account to email for transfer confirmation")
    public List<Subject> getTransferSubjects(TransactionPayload payload) {
        Account acc = accounts.get(payload.getFromAccountId());
        if (acc == null) return List.of();
        return List.of(new EmailSubject(acc.getId(), acc.getEmail(), null, null, null, Map.of("holderName", acc.getHolderName())));
    }

    @SubjectSupplier(event = "DAILY_BALANCE_SUMMARY", description = "Resolves account to email for daily digest")
    public List<Subject> getDailySummarySubjects(BalanceSummaryPayload payload) {
        Account acc = accounts.get(payload.getAccountId());
        if (acc == null) return List.of();
        return List.of(new EmailSubject(acc.getId(), acc.getEmail(), null, null, null, Map.of("holderName", acc.getHolderName())));
    }

    // ═══════════════════════════════════════════
    // VOCABULARY SUPPLIER
    // ═══════════════════════════════════════════

    @VocabularySupplier(event = "LARGE_TRANSFER", description = "Enriches transfer payload with sender and receiver names")
    public TransactionPayload transferVocabulary(TransactionPayload payload) {
        // Enrichment: the payload itself is used as vocabulary, but we can
        // add additional context by modifying it before it gets serialized
        Account sender = accounts.get(payload.getFromAccountId());
        Account receiver = accounts.get(payload.getToAccountId());
        log.info("Enriching transfer vocabulary: {} -> {}",
            sender != null ? sender.getHolderName() : "unknown",
            receiver != null ? receiver.getHolderName() : "unknown");
        return payload;
    }

    // ═══════════════════════════════════════════
    // RULES
    // ═══════════════════════════════════════════

    @Rule(name = "transfer-fraud-check", event = "LARGE_TRANSFER", description = "Blocks transfers over $50,000 as potential fraud")
    public boolean fraudCheckRule(TransactionPayload payload) {
        boolean passed = payload.getAmount() <= 50000.0;
        log.info("🔍 Fraud check for transfer {}: {} (amount=${})", payload.getTransactionId(), passed ? "PASSED" : "FLAGGED", payload.getAmount());
        return passed;
    }

    @Rule(name = "velocity-check", event = "LARGE_TRANSFER", description = "Blocks if sender has more than 5 recent transactions")
    public boolean velocityCheckRule(TransactionPayload payload) {
        long recentCount = transactions.countByFromAccount(payload.getFromAccountId());
        boolean passed = recentCount < 5;
        log.info("⚡ Velocity check for {}: {} ({} recent tx)", payload.getFromAccountId(), passed ? "PASSED" : "FLAGGED", recentCount);
        return passed;
    }

    // ═══════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════

    @Callback(event = "SUSPICIOUS_LOGIN", when = Callback.When.BEFORE)
    public void beforeSuspiciousLogin(LoginPayload payload) {
        log.warn("⏳ [BEFORE] Security alert processing for user {} from {}", payload.getUserId(), payload.getLocation());
    }

    @Callback(event = "LARGE_TRANSFER", when = Callback.When.AFTER)
    public void afterLargeTransfer(TransactionPayload payload) {
        log.info("✅ [AFTER] Audit trail recorded for transfer {} — ${} {}", payload.getTransactionId(), payload.getAmount(), payload.getCurrency());
    }
}
