package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/create")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).
                body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByAccountNumber(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getAccountByAccountNumber(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @Valid @PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getbalance(accountNumber));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(@PathVariable String accountNumber) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked");
    }


    //Saga Step 1 - Deduct balance
    // Called from transaction service when transfer is initiated
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(@RequestParam BigDecimal amount,
                                                @PathVariable String accountNumber) {
        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok("Amount deducted Successfully");

    }

    /**
     * Saga Step 4- compensating transaction endpoint
     * Called by transaction service in 2 scenario's
     * 1. Fraud Detected - Refund Sender --> Undo Step 1
     * 2. Transaction Completed- amount Credited to Receiver
     */
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(@PathVariable String accountNumber,
                                                @RequestParam BigDecimal amount) {

        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok("Amount credited Successfully");
    }

}
