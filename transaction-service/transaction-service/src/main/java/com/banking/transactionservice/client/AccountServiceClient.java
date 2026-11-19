package com.banking.transactionservice.client;

import jakarta.validation.constraints.Positive;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.banking.transactionservice.dto.AccountResponse;
import com.banking.transactionservice.response.ApiResponse;

import java.math.BigDecimal;

/**
 * Without FeignClient you would manually make an HTTP request using
 * RestTemplate or WebClient.
 */
@FeignClient(name = "account-service", url = "${account.service.url}")
public interface AccountServiceClient {

    @PutMapping("/api/v1/accounts/{accountNumber}/deduct")
    String deductBalance(@RequestParam @Positive BigDecimal amount, @PathVariable String accountNumber);

    @PutMapping("/api/v1/accounts/{accountNumber}/credit")
    String creditBalance(@PathVariable String accountNumber, @RequestParam @Positive BigDecimal amount);

    // Fetch account details, including customer email.
    @GetMapping("/api/v1/accounts/{accountNumber}")
    ApiResponse<AccountResponse> getAccountDetails(@PathVariable String accountNumber);

}
