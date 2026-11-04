package com.banking.transactionservice.entity;

/**
 * Transaction LifeCycle Flow
 * <p>
 * PENDING --> PROCESSING --> A. COMPLETED(clean transaction)
 *                        --> B. PENDING_VERIFICATION(suspicious detected)
 *                              --> i. COMPLETED (verified)
 *                              --> ii. FLAGGED (SAGA REFUND)
 *                        --> C. FAILED
 *                        --> D. FLAGGED
 * /
 **/
public enum TransactionStatus {

    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED

}
