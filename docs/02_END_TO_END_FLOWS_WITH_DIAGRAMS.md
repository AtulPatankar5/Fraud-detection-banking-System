# 02. End-to-End Flows & Architecture Diagrams

This document contains detailed step-by-step walkthroughs and visual sequence diagrams for every core execution path in the Banking Fraud Detection system.

---

## 🧭 Master State Machine: Transaction Lifecycle

The `TransactionStatus` entity transitions through clearly defined states to maintain consistency:

```mermaid
stateDiagram-v2
    [*] --> PROCESSING: POST /api/v1/transactions/transfer<br/>(Sender debited via Feign)
    
    PROCESSING --> COMPLETED: Kafka: fraud.check.clean<br/>(Clean checks, receiver credited)
    
    PROCESSING --> PENDING_VERIFICATION: Kafka: verification.required<br/>(OTP stored in Redis with 5m TTL)
    
    PENDING_VERIFICATION --> COMPLETED: POST /verify (Correct OTP)<br/>(Receiver credited via Kafka)
    
    PENDING_VERIFICATION --> FLAGGED: POST /verify (Wrong OTP)<br/>(Compensating refund + Account Blocked)
    
    PENDING_VERIFICATION --> FLAGGED: POST /verify (OTP Expired)<br/>(Compensating refund to sender)
    
    COMPLETED --> [*]
    FLAGGED --> [*]
```

---

## 🟢 Flow 1: Happy Path Fund Transfer (Clean Transaction)

This flow executes when the transaction does not trigger any of the 3 fraud detection rules.

### Sequence Diagram:
```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User / Client
    participant GW as 🛡️ API Gateway (8080)
    participant TS as 💳 Transaction Service (8082)
    participant AS as 🏦 Account Service (8081)
    participant K as 📨 Kafka Broker (9092)
    participant FS as 🛡️ Fraud Service (8084)
    participant R as ⚡ Redis (6379)
    participant NS as 🔔 Notification Service (8085)

    User->>GW: POST /api/v1/transactions/transfer
    GW->>R: Rate Limit Token Bucket Check
    R-->>GW: Allowed
    GW->>TS: Forward Request

    Note over TS,AS: SAGA Step 1 - Deduct from Sender
    TS->>AS: PUT /api/v1/accounts/{sender}/deduct?amount=X (Feign)
    AS->>AS: Check balance >= X & Status ACTIVE
    AS-->>TS: Balance Deducted (200 OK)

    TS->>TS: Save Transaction (Status: PROCESSING)
    
    Note over TS,K: SAGA Step 2 - Publish Event for Fraud Check
    TS->>K: Send "transaction.initiated"
    TS-->>User: 201 Created (Status: PROCESSING)

    K->>FS: Consume "transaction.initiated"
    FS->>R: Check Velocity (INCR fraud:velocity:{acc})
    R-->>FS: Count <= 5
    FS->>R: Check Average (GET fraud.avg_amount:{acc})
    R-->>FS: Normal Amount
    FS->>AS: GET /api/v1/accounts/{sender}/balance (Feign)
    AS-->>FS: Return balance (Amount <= 90%)
    
    Note over FS,K: All Checks Passed!
    FS->>K: Send "fraud.check.clean"

    K->>TS: Consume "fraud.check.clean"
    TS->>TS: Idempotency Check (Status == PROCESSING)
    TS->>TS: Update Status = COMPLETED
    
    Note over TS,K: Publish Completion Event
    TS->>K: Send "transaction.completed"

    par Credit Receiver
        K->>AS: Consume "transaction.completed"
        AS->>AS: Credit Receiver Account
    and Notify Parties
        K->>NS: Consume "transaction.completed"
        NS->>NS: Send DEBIT Alert to Sender
        NS->>NS: Send CREDIT Alert to Receiver
    end
```

### Step-by-Step Breakdown:
1. **Client Request:** User posts transfer request (`senderAccountNumber`, `receiverAccountNumber`, `amount`, `description`).
2. **Gateway Check:** Gateway checks Redis token bucket. If quota is available, it routes to `transaction-service`.
3. **Fund Reservation (SAGA Step 1):** `TransactionService` uses OpenFeign to invoke `AccountService.deductBalance()`.
4. **State Persistence:** Transaction is saved in MySQL with status `PROCESSING`.
5. **Event Emission (SAGA Step 2):** Publishes `TransactionInitiatedEvent` to Kafka topic `transaction.initiated`.
6. **Fraud Analysis:** `FraudDetectionService` executes:
   - Velocity counter in Redis (must be $\le 5$ in 60s).
   - Moving average check in Redis (must be $\le 5\times$ historical avg).
   - Balance check via Feign (amount must be $\le 90\%$ of balance).
7. **Clean Result:** Emits `fraud.check.clean` to Kafka.
8. **Completion:** `TransactionService` marks status as `COMPLETED` and emits `transaction.completed`.
9. **Final Settlement & Alerting:** `AccountService` credits receiver; `NotificationService` alerts both parties.

---

## 🟡 Flow 2: Suspicious Activity Flow (Triggered OTP & Successful Verification)

This flow occurs when a transaction triggers a fraud rule (e.g., velocity $> 5$ or amount $> 5\times$ average), but the legitimate user verifies their identity via OTP.

### Sequence Diagram:
```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User / Client
    participant TS as 💳 Transaction Service
    participant K as 📨 Kafka Broker
    participant FS as 🛡️ Fraud Service
    participant R as ⚡ Redis (6379)
    participant NS as 🔔 Notification Service
    participant AS as 🏦 Account Service

    K->>FS: Consume "transaction.initiated"
    FS->>R: Velocity or Amount Check Triggered!
    Note over FS: Anomaly Detected: e.g. "Velocity Limit Exceeded"
    FS->>K: Send "verification.required" (Reason, txId, amount)

    K->>TS: Consume "verification.required"
    TS->>TS: Generate 6-digit OTP (e.g. "548291")
    TS->>R: SET verification:otp{txId} = "548291" (TTL: 5 min)
    TS->>TS: Update Status = PENDING_VERIFICATION
    TS->>K: Send "transaction.otp.generated"

    K->>NS: Consume "transaction.otp.generated"
    NS->>User: 📲 Send SMS/Email with OTP & Suspicious Alert Reason

    Note over User,TS: User Verifies Challenge
    User->>TS: POST /api/v1/transactions/{txId}/verify?otp=548291
    TS->>R: GET verification:otp{txId}
    R-->>TS: Returns "548291"
    TS->>R: DEL verification:otp{txId}
    
    Note over TS: OTP Matches! Complete Transfer
    TS->>TS: Update Status = COMPLETED
    TS->>K: Send "transaction.completed"

    par Credit Receiver
        K->>AS: Consume "transaction.completed"
        AS->>AS: Credit Receiver Account
    and Notify Parties
        K->>NS: Consume "transaction.completed"
        NS->>NS: Send Debit/Credit Confirmation Alerts
    end
```

---

## 🔴 Flow 3: Fraudulent Attempt Flow (Wrong OTP ➔ SAGA Compensation & Account Freeze)

When an attacker enters the wrong OTP, the system executes a **SAGA Compensating Transaction** (refunding deducted money back to the account) and **freezes the sender's account** immediately.

### Sequence Diagram:
```mermaid
sequenceDiagram
    autonumber
    actor Attacker as 🚨 Unauthorized User
    participant TS as 💳 Transaction Service
    participant R as ⚡ Redis (6379)
    participant AS as 🏦 Account Service
    participant K as 📨 Kafka Broker
    participant NS as 🔔 Notification Service

    Attacker->>TS: POST /api/v1/transactions/{txId}/verify?otp=000000 (Wrong OTP)
    TS->>R: GET verification:otp{txId}
    R-->>TS: Stored OTP is "548291" (MISMATCH!)
    TS->>R: DEL verification:otp{txId}

    Note over TS,K: Step 1: Security Alert - Freeze Account
    TS->>K: Send "fraud.detected" (accountNumber, reason)
    
    Note over TS,AS: Step 2: SAGA Compensating Transaction (Refund)
    TS->>AS: PUT /api/v1/accounts/{sender}/credit?amount=X (Feign Refund)
    AS->>AS: Balance Credited Back (+X)
    AS-->>TS: 200 OK Amount Credited

    TS->>TS: Update Status = FLAGGED<br/>failureReason: "Wrong OTP entered"
    TS->>K: Send "transaction.refunded"

    par Block Account
        K->>AS: Consume "fraud.detected"
        AS->>AS: Set AccountStatus = BLOCKED
    and Security Alerts
        K->>NS: Consume "fraud.detected"
        NS->>NS: 🚨 Alert: "Account has been blocked due to suspicious activity"
    and Refund Alert
        K->>NS: Consume "transaction.refunded"
        NS->>NS: 💰 Alert: "Transaction cancelled. Amount refunded to account"
    end
```

---

## ⏱️ Flow 4: OTP Expired Flow (Timeout ➔ Automatic Refund)

If the user fails to provide an OTP within the 5-minute TTL window, Redis automatically evicts the key:

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant TS as 💳 Transaction Service
    participant R as ⚡ Redis
    participant AS as 🏦 Account Service
    participant K as 📨 Kafka Broker
    participant NS as 🔔 Notification Service

    Note over R: 5 Minutes Pass... Redis auto-deletes key: verification:otp{txId}
    User->>TS: POST /api/v1/transactions/{txId}/verify?otp=548291
    TS->>R: GET verification:otp{txId}
    R-->>TS: null (Key expired)

    Note over TS,AS: SAGA Compensating Action
    TS->>AS: PUT /api/v1/accounts/{sender}/credit?amount=X (Feign Refund)
    AS-->>TS: 200 OK Refunded

    TS->>TS: Update Status = FLAGGED<br/>failureReason: "OTP expired - Transaction cancelled and amount refunded"
    TS->>K: Send "transaction.refunded"

    K->>NS: Consume "transaction.refunded"
    NS->>User: 📲 Alert: "Transaction expired and funds refunded"
```

---

## 💳 Flow 5: Payment Gateway Flow (Razorpay Order & Webhook)

For deposits, wallet recharges, and payment gateway interactions:

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User / Frontend
    participant PS as 💰 Payment Service (8083)
    participant RZP as 🌐 Razorpay API
    participant K as 📨 Kafka Broker
    participant NS as 🔔 Notification Service

    User->>PS: POST /api/v1/payments/create-order (account, amount)
    PS->>PS: Convert Amount to Cents/Paise (amount * 100)
    PS->>RZP: razorpayClient.orders.create({amount, currency: "USD", receipt})
    RZP-->>PS: Return Razorpay Order Object (id: "order_xyz123")
    PS->>PS: Save Payment in DB (Status: CREATED)
    PS-->>User: Return {orderId, amount, keyId}

    User->>RZP: Frontend Razorpay Checkout Modal (User pays via Card/UPI)
    RZP->>RZP: Process Payment Gateway Transaction

    Note over RZP,PS: Webhook Asynchronous Callback
    RZP->>PS: POST /api/v1/payments/webhook<br/>Payload: {event: "payment.captured", payment: {...}}
    PS->>PS: Extract order_id & payment_id
    PS->>PS: Update Payment DB (Status = COMPLETED, razorpayPaymentId = id)
    PS->>K: Send "payment.completed"
    PS-->>RZP: 200 OK "WebHook Processed"

    K->>NS: Consume "payment.completed"
    NS->>User: 📲 Alert: "Payment of $X completed successfully. Razorpay ID: ..."
```

