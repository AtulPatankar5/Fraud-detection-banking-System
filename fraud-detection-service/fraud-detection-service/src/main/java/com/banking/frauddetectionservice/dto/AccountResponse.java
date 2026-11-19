package com.banking.frauddetectionservice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountResponse {

    private String accountNumber;

    private BigDecimal balance;

    private String accountHolderName;
}