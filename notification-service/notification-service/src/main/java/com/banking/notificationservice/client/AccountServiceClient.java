package com.banking.notificationservice.client;

import com.banking.notificationservice.dto.AccountResponse;
import com.banking.notificationservice.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "account-service",
        url = "${services.account-service.url}"
)
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountNumber}")
    ApiResponse<AccountResponse> getAccountDetails(
            @PathVariable("accountNumber") String accountNumber
    );
}