# 01. Services Architecture Deep Dive

This document details all 6 microservices comprising the Banking Fraud Detection system, their internal component structure, communication interfaces, and persistence strategies.

---

## 1. API Gateway Service (`api-gateway`)
- **Port:** `8080`
- **Framework:** Spring Cloud Gateway (Reactive, built on Project Reactor & Netty)
- **Configuration:** [`application.yaml`](file:///d:/Java%20Projects/Banking-system/api-gateway/api-gateway/src/main/resources/application.yaml)
- **Primary Source:** [`RateLimiterConfig.java`](file:///d:/Java%20Projects/Banking-system/api-gateway/api-gateway/src/main/java/com/banking/apigateway/config/RateLimiterConfig.java)

### Responsibilities:
1. **Single Entry Point:** Reverse proxies all incoming client HTTP requests to appropriate downstream microservices.
2. **Distributed Rate Limiting:** Enforces token-bucket rate limits per client IP using Redis to prevent DoS attacks, brute force attempts, and API abuse.
3. **Traffic Decoupling:** Shields internal microservice IP addresses and internal port layouts (`8081`, `8082`, `8083`) from clients.

### Routing Table & Rate Limits:
| Route ID | Path Predicate | Target URI | Replenish Rate | Burst Capacity | Key Resolver |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `account-service` | `/api/v1/accounts/**` | `http://localhost:8081` | 10 req/sec | 20 tokens | Client Host IP (`userKeyResolver`) |
| `transaction-service` | `/api/v1/transactions/**` | `http://localhost:8082` | 10 req/sec | 20 tokens | Client Host IP (`userKeyResolver`) |
| `payment-service` | `/api/v1/payments/**` | `http://localhost:8083` | 5 req/sec | 10 tokens | Client Host IP (`userKeyResolver`) |

### Key Code Concept:
```java
// Extracts client IP address for per-client rate limit token bucket in Redis
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> Mono.just(
        exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
    );
}
```

---

## 2. Account Service (`account-service`)
- **Port:** `8081`
- **Database:** MySQL (`a_account_service_db`)
- **Primary Sources:**
  - Controller: [`AccountController.java`](file:///d:/Java%20Projects/Banking-system/account-service/account-service/src/main/java/com/banking/accountservice/controller/AccountController.java)
  - Service: [`AccountService.java`](file:///d:/Java%20Projects/Banking-system/account-service/account-service/src/main/java/com/banking/accountservice/service/AccountService.java)
  - Kafka Listener: [`AccountEventConsumer.java`](file:///d:/Java%20Projects/Banking-system/account-service/account-service/src/main/java/com/banking/accountservice/service/AccountEventConsumer.java)
  - Entity: [`Account.java`](file:///d:/Java%20Projects/Banking-system/account-service/account-service/src/main/java/com/banking/accountservice/entity/Account.java)

### Responsibilities:
1. Manages bank customer accounts, balances, and lifecycle states (`ACTIVE`, `BLOCKED`, `SUSPENDED`).
2. Generates unique 12-digit random account numbers.
3. Provides synchronous balance deduction and credit endpoints used in SAGA steps.
4. Asynchronously consumes Kafka events to credit receivers or freeze compromised accounts.

### Data Model (`accounts` Table):
- `id` (UUID, Primary Key)
- `accountNumber` (String, Unique, 12 digits)
- `accountHolderName` (String)
- `email` (String, Unique)
- `phone` (String)
- `accountType` (Enum: `SAVINGS`, `CURRENT`)
- `accountStatus` (Enum: `ACTIVE`, `BLOCKED`, `SUSPENDED`, `CLOSED`)
- `balance` (BigDecimal, precision 15, scale 2)
- `dailyTransactionLimit` (BigDecimal: ₹100,000 for SAVINGS, ₹500,000 for CURRENT)
- `createdAt`, `updatedAt` (Timestamps)

### REST Endpoints:
| Method | Endpoint | Description | SAGA / Flow Role |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/accounts/create` | Open new bank account with initial deposit | User onboarding |
| `GET` | `/api/v1/accounts/{accountNumber}` | Get account profile details | Read query |
| `GET` | `/api/v1/accounts/{accountNumber}/balance`| Get current balance | Checked by Fraud Service & UI |
| `PUT` | `/api/v1/accounts/{accountNumber}/block` | Set account status to `BLOCKED` | Triggered on confirmed fraud |
| `PUT` | `/api/v1/accounts/{accountNumber}/deduct?amount=...` | Deduct balance from sender | **SAGA Step 1 (Sync via Feign)** |
| `PUT` | `/api/v1/accounts/{accountNumber}/credit?amount=...` | Credit balance to account | **SAGA Compensation / Step 4 (Sync)** |

### Kafka Listeners:
1. **`transaction.completed`**: Credits receiver account with transferred amount (`accountService.creditBalance(...)`).
2. **`fraud.detected`**: Automatically sets the account status to `BLOCKED` (`accountService.blockAccount(...)`).

---

## 3. Transaction Service (`transaction-service`)
- **Port:** `8082`
- **Database:** MySQL (`b_transaction_service_db`)
- **In-Memory Store:** Redis (`localhost:6379`)
- **Primary Sources:**
  - Controller: [`TransactionController.java`](file:///d:/Java%20Projects/Banking-system/transaction-service/transaction-service/src/main/java/com/banking/transactionservice/controller/TransactionController.java)
  - Service: [`TransactionService.java`](file:///d:/Java%20Projects/Banking-system/transaction-service/transaction-service/src/main/java/com/banking/transactionservice/service/TransactionService.java)
  - Kafka Listener: [`TransactionEventConsumer.java`](file:///d:/Java%20Projects/Banking-system/transaction-service/transaction-service/src/main/java/com/banking/transactionservice/service/TransactionEventConsumer.java)
  - Feign Client: [`AccountServiceClient.java`](file:///d:/Java%20Projects/Banking-system/transaction-service/transaction-service/src/main/java/com/banking/transactionservice/client/AccountServiceClient.java)
  - Entity: [`Transaction.java`](file:///d:/Java%20Projects/Banking-system/transaction-service/transaction-service/src/main/java/com/banking/transactionservice/entity/Transaction.java)

### Responsibilities:
1. **SAGA Coordinator:** Drives the distributed fund transfer flow between accounts.
2. **State Management:** Records transactions with lifecycle statuses:
   - `PENDING` ➔ `PROCESSING` ➔ `COMPLETED` / `PENDING_VERIFICATION` ➔ `FLAGGED` / `FAILED`.
3. **Synchronous Fund Reservation:** Calls Account Service via OpenFeign to deduct sender balance upfront.
4. **Fraud Orchestration:** Publishes `transaction.initiated` to Kafka and listens for fraud verdicts.
5. **Two-Factor Challenge (OTP):** Generates 6-digit OTP, stores it in Redis with a 5-minute TTL, verifies user input, and triggers compensating refund if wrong or expired.

### Data Model (`transactions` Table):
- `id` (UUID, Primary Key)
- `senderAccountNumber` (String)
- `receiverAccountNumber` (String)
- `amount` (BigDecimal, precision 15, scale 2)
- `type` (Enum: `TRANSFER`, `DEPOSIT`, `WITHDRAW`, `PAYMENT`)
- `status` (Enum: `PENDING`, `PROCESSING`, `PENDING_VERIFICATION`, `COMPLETED`, `FAILED`, `FLAGGED`)
- `description` (String)
- `failureReason` (String)
- `referenceNumber` (UUID String)
- `createdAt`, `completedAt` (Timestamps)

### REST Endpoints:
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/transactions/transfer` | Initiates fund transfer (SAGA Step 1 & 2) |
| `GET` | `/api/v1/transactions/{transactionId}` | Fetch transaction details by ID |
| `GET` | `/api/v1/transactions/account/{accountNumber}` | Get statement / transaction history |
| `POST` | `/api/v1/transactions/{transactionId}/verify?otp=...` | Verifies OTP for flagged transactions |

### Kafka Topics Handled:
- **Produces:** `transaction.initiated`, `transaction.completed`, `fraud.detected`, `transaction.refunded`, `transaction.otp.generated`
- **Consumes:** `fraud.check.clean`, `verification.required`

---

## 4. Fraud Detection Service (`fraud-detection-service`)
- **Port:** `8084`
- **State Store:** Redis (`localhost:6379`)
- **Primary Sources:**
  - Service: [`FraudDetectionService.java`](file:///d:/Java%20Projects/Banking-system/fraud-detection-service/fraud-detection-service/src/main/java/com/banking/frauddetectionservice/service/FraudDetectionService.java)
  - Kafka Listener: [`FraudDetectionEventConsumer.java`](file:///d:/Java%20Projects/Banking-system/fraud-detection-service/fraud-detection-service/src/main/java/com/banking/frauddetectionservice/service/FraudDetectionEventConsumer.java)
  - Feign Client: [`AccountServiceClient.java`](file:///d:/Java%20Projects/Banking-system/fraud-detection-service/fraud-detection-service/src/main/java/com/banking/frauddetectionservice/client/AccountServiceClient.java)
  - Result DTO: [`FraudCheckResult.java`](file:///d:/Java%20Projects/Banking-system/fraud-detection-service/fraud-detection-service/src/main/java/com/banking/frauddetectionservice/model/FraudCheckResult.java)

### Responsibilities:
1. Real-time stream processing of all `transaction.initiated` events.
2. Evaluates transactions against 3 fraud detection rules using Redis in-memory storage for high-speed evaluations without database bottlenecks.
3. Synchronously verifies real-time balance from Account Service via OpenFeign.
4. Emits either `fraud.check.clean` or `verification.required`.

### 3 Fraud Detection Algorithms:
```mermaid
graph TD
    A[Incoming Transaction Event] --> 
    
    B{Rule 1: Velocity Check}
    B -->|Count > 5 in 60s| F[🚨 Flag: Velocity Limit Exceeded]
    B -->|Pass| 
    
    C{Rule 2: Amount Spikes}
    C -->|Amount > 5x Moving Avg| F[🚨 Flag: Unusual Transaction Amount]
    C -->|Pass| 
    
    D{Rule 3: Balance Drain}
    D -->|Amount > 90% Account Balance| F[🚨 Flag: Exceeds 90% Balance]
    D -->|Pass| 
    
    G[✅ Emit: fraud.check.clean]
    
    F --> H[⚠️ Emit: verification.required]
```

1. **Velocity Check:** Tracks transaction frequency within a sliding 60-second window using Redis `INCR` and `EXPIRE`. Max limit: `5` tx/min.
2. **Unusual Amount Check:** Maintains an exponential moving average per account in Redis (`fraud.avg_amount:{accountNumber}`). Flags transactions exceeding `5x` (configurable) average.
3. **High Balance Drain Check:** Uses OpenFeign to check current balance. Flags transfers requesting more than `90%` of total liquid balance.

---

## 5. Notification Service (`notification-service`)
- **Port:** `8085`
- **Primary Source:** [`NotificationService.java`](file:///d:/Java%20Projects/Banking-system/notification-service/notification-service/src/main/java/com/banking/notificationservice/service/NotificationService.java)

### Responsibilities:
1. **Pure Event Consumer:** Operates completely decoupled from core transaction pipelines.
2. **Unified Omnichannel Alerting:** Consumes events from multiple services and routes alerts (Email, SMS, Push notification simulation).
3. **Handles 6 Distinct Event Topics:**
   - `transaction.otp.generated` ➔ Dispatches OTP code and warning reason.
   - `transaction.completed` ➔ Sends Debit alert to sender and Credit alert to receiver.
   - `fraud.detected` ➔ Dispatches security alert informing user that account has been blocked.
   - `transaction.refunded` ➔ Informs sender of cancellation and refunded amount.
   - `payment.completed` ➔ Sends receipt with Razorpay payment ID.
   - `payment.failed` ➔ Sends payment failure alert.

---

## 6. Payment Service (`payment-service`)
- **Port:** `8083`
- **Database:** MySQL (`c_payment_service_db`)
- **External Integration:** Razorpay Java SDK (`com.razorpay.RazorpayClient`)
- **Primary Sources:**
  - Controller: [`PaymentController.java`](file:///d:/Java%20Projects/Banking-system/payment-service/payment-service/src/main/java/com/banking/paymentservice/controller/PaymentController.java)
  - Service: [`PaymentService.java`](file:///d:/Java%20Projects/Banking-system/payment-service/payment-service/src/main/java/com/banking/paymentservice/service/PaymentService.java)
  - Entity: [`Payment.java`](file:///d:/Java%20Projects/Banking-system/payment-service/payment-service/src/main/java/com/banking/paymentservice/entity/Payment.java)

### Responsibilities:
1. Integrates with Razorpay Payment Gateway for card/UPI/net-banking deposits and checkout.
2. Converts amounts from decimal currency to paise/cents (`amount * 100`).
3. Handles Razorpay Webhook callbacks (`payment.captured`, `payment.failed`) with idempotency.
4. Emits `payment.completed` and `payment.failed` events to Kafka.

### Data Model (`payments` Table):
- `id` (UUID or Razorpay Order ID)
- `razorpayOrderId` (String)
- `razorpayPaymentId` (String)
- `accountNumber` (String)
- `amount` (BigDecimal)
- `currency` (String, e.g. "USD" or "INR")
- `status` (Enum: `CREATED`, `PENDING`, `COMPLETED`, `FAILED`)
- `description` (String)
- `paymentFailureReason` (String)
- `createdAt`, `updatedAt` (Timestamps)

