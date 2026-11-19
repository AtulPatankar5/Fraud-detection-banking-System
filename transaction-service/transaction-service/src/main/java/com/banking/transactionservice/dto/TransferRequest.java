package com.banking.transactionservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@Data
@AllArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Sender account Number is required")
    private String senderAccountNumber;

    @NotBlank(message = "Receiver account Number is required")
    private String receiverAccountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount should be positive")
    private BigDecimal amount;

    @Email(message = "Invalid email format")
    private String email;


    private String description;
}
