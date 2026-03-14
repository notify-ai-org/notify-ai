package com.example.banking.controller;

import com.example.banking.model.*;
import com.example.banking.service.TransactionService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller exposing endpoints to trigger banking events for testing.
 */
@RestController
@RequestMapping("/api/banking")
public class BankingController {

    private final TransactionService transactionService;

    public BankingController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/suspicious-login")
    public ResponseEntity<Map<String, Object>> suspiciousLogin(@RequestBody LoginPayload payload) {
        transactionService.flagSuspiciousLogin(payload);
        return ResponseEntity.ok(Map.of(
            "status", "SUSPICIOUS_LOGIN_FLAGGED",
            "userId", payload.getUserId(),
            "location", payload.getLocation()
        ));
    }

    @PostMapping("/request-otp")
    public ResponseEntity<Map<String, Object>> requestOtp(@RequestBody OtpPayload payload) {
        transactionService.requestOtp(payload);
        return ResponseEntity.ok(Map.of(
            "status", "OTP_SENT",
            "userId", payload.getUserId(),
            "channel", payload.getChannel()
        ));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody TransactionPayload payload) {
        transactionService.processLargeTransfer(payload);
        return ResponseEntity.ok(Map.of(
            "status", "TRANSFER_INITIATED",
            "transactionId", payload.getTransactionId(),
            "amount", payload.getAmount()
        ));
    }

    @PostMapping("/daily-summary")
    public ResponseEntity<Map<String, Object>> dailySummary(@RequestBody BalanceSummaryPayload payload) {
        transactionService.generateDailySummary(payload);
        return ResponseEntity.ok(Map.of(
            "status", "DAILY_SUMMARY_GENERATED",
            "accountId", payload.getAccountId(),
            "balance", payload.getBalance()
        ));
    }
}
