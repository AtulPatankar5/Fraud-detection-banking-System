package com.banking.transactionservice.controller;

import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.response.ApiResponse;
import com.banking.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Slf4j
@RequiredArgsConstructor
@Validated
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(@Valid @RequestBody TransferRequest transferRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transaction initiated successfully", transactionService.transfer(transferRequest)));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable @NotBlank String transactionId) {
        return ResponseEntity.ok(ApiResponse.success("Transaction retrieved successfully", transactionService.getTransaction(transactionId)));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(@PathVariable @NotBlank String accountNumber) {
        return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved successfully", transactionService.getTransactionHistory(accountNumber)));
    }

    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<ApiResponse<TransactionResponse>> verifyOTP(@PathVariable @NotBlank String transactionId, @RequestParam @NotBlank String otp) {
        log.info("Verify OTP request for transaction {}", transactionId);
        return ResponseEntity.ok(ApiResponse.success("Transaction verification processed successfully", transactionService.verifyOtp(transactionId, otp)));
    }


}
