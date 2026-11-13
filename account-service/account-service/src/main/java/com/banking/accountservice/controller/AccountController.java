package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.response.ApiResponse;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).
                body(ApiResponse.success(
                        "Account Created Successfully",
                        accountService.createAccount(request)
                ));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByAccountNumber(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Account fetched Successfully!",
                        accountService.getAccountByAccountNumber(accountNumber)
                ));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(
            @Valid @PathVariable String accountNumber) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Balance fetched successfully",
                        accountService.getbalance(accountNumber)
                ));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<ApiResponse<Void>> blockAccount(@PathVariable String accountNumber) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Account blocked",
                        null
                ));
    }


    //Saga Step 1 - Deduct balance
    // Called from transaction service when transfer is initiated
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<ApiResponse<Void>> deductBalance(@RequestParam BigDecimal amount,
                                                           @PathVariable @Positive String accountNumber) {
        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok(ApiResponse.success(
                "Amount deducted Successfully",
                null)
        );

    }

    /**
     * Saga Step 4- compensating transaction endpoint
     * Called by transaction service in 2 scenario's
     * 1. Fraud Detected - Refund Sender --> Undo Step 1
     * 2. Transaction Completed- amount Credited to Receiver
     */
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<ApiResponse<Void>> creditBalance(@PathVariable String accountNumber,
                                                           @RequestParam @Positive BigDecimal amount) {

        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Amount credited Successfully",
                        null
                )
        );
    }

}
