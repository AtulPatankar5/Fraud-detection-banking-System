package com.banking.frauddetectionservice.client;

import com.banking.frauddetectionservice.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name="account-service", url="${account.service.url}")
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountNumber}/balance")
    ApiResponse<BigDecimal> getBalance(@Valid @PathVariable String accountNumber);
}
