package com.banking.transactionservice.dto;

import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@Data
@AllArgsConstructor
public class TransactionResponse {

    private String id;
    private String senderAccountNumber;

    private String receiverAccountNumber;

    private BigDecimal amount;

    private TransactionType type;

    private TransactionStatus status;

    private String description;

    private String failureReason;

    private String referenceNumber;

    private LocalDateTime creationAt;

    private LocalDateTime completedAt;

}
