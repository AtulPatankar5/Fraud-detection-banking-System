# 05. Interview Questions, Answers & Cheatsheet

Use this guide to confidently explain this project in technical interviews for **Backend Engineer**, **Java Microservices Developer**, or **System Design** roles.

---

## 🎙️ Section 1: The 2-Minute Opening Pitch

> **Interviewer:** *"Can you describe the most challenging project you worked on recently?"*
>
> **Your Answer:**
> *"I designed and built an event-driven, distributed Banking and Real-Time Fraud Detection System using Spring Boot, Spring Cloud Gateway, Apache Kafka, Redis, and MySQL.*
>
> *The system addresses two core challenges: guaranteeing **eventual data consistency** during peer-to-peer fund transfers across isolated microservice databases, and detecting fraudulent transactions in **sub-milliseconds** before money leaves the bank.*
>
> *I implemented a **SAGA pattern** where fund transfers begin by synchronously reserving funds from the sender and publishing a transaction event to Kafka. An independent Fraud Detection Service evaluates transactions against three in-memory rules using Redis: velocity frequency checks, spending spikes relative to exponential moving averages, and liquid balance ratios.*
>
> *If flagged, the system challenges the user with a two-factor OTP stored in Redis with an automated 5-minute TTL. If the OTP fails or times out, a SAGA compensating transaction automatically refunds the sender's account, freezes the compromised account, and notifies the customer.*
>
> *Through this project, I handled distributed rate limiting at the API Gateway, at-least-once Kafka event processing with idempotency guards, and payment gateway webhooks using Razorpay."*

---

## 💡 Section 2: Deep-Dive Technical Q&A

### Q1. Why did you use both OpenFeign (Synchronous) and Kafka (Asynchronous)? Why not Kafka for everything?
**Answer:**
- **Synchronous (OpenFeign) for immediate invariant verification:** In SAGA Step 1, we must immediately verify that the sender exists, is in an `ACTIVE` status, and possesses sufficient balance *before* accepting the transfer. If balance deduction fails, we want an immediate HTTP 400 error returned to the user without starting an asynchronous pipeline.
- **Asynchronous (Kafka) for decoupling & eventual consistency:** Fraud evaluation, crediting the recipient, sending email/SMS notifications, and logging are non-blocking operations. Decoupling them via Kafka prevents slow third-party notification APIs or fraud checks from slowing down user response times.

---

### Q2. How did you maintain data consistency without distributed transactions (2PC)?
**Answer:**
*"We adopted the **SAGA Pattern** with local database transactions per service. 
1. **Forward Step:** When a transfer begins, `transaction-service` calls `account-service` to deduct the sender's balance immediately and records the transaction as `PROCESSING`.
2. **Event Progression:** It publishes `transaction.initiated` to Kafka.
3. **Compensating Action:** If the fraud service flags the transaction and the user inputs a wrong OTP or times out, the `transaction-service` triggers a **compensating transaction** via Feign to credit the money back to the sender, updates status to `FLAGGED`, and publishes a `transaction.refunded` event."*

---

### Q3. How exactly is Redis used for Fraud Detection?
**Answer:**
*"Redis powers three distinct fraud detection mechanisms:
1. **Velocity Rate Check:** Key `fraud:velocity:{accountNumber}` tracks how many transfers happen within a 60-second sliding window using atomic `INCR`. If `count == 1`, we call `EXPIRE key 60`. If `count > 5`, it flags high-frequency brute-force transfers.
2. **Spending Spike Anomaly:** Key `fraud.avg_amount:{accountNumber}` stores the historical moving average. For each transfer, we compare the amount against a threshold ($5\times$ historical average). We then update the baseline in Redis using `newAvg = (avg + amount) / 2`.
3. **Sub-millisecond Latency:** By keeping velocity counts and averages in Redis RAM, the fraud service evaluates each transaction in under 2 milliseconds without querying MySQL."*

---

### Q4. How do you prevent duplicate message processing (Kafka Idempotency)?
**Answer:**
*"Because Kafka provides **at-least-once delivery**, consumers must be idempotent:
- In `TransactionService.processCleanResult()`, before completing a transaction, we check:
  ```java
  if (transaction.getStatus() != TransactionStatus.PROCESSING) {
      return; // Already processed! Ignore duplicate event
  }
  ```
- In `TransactionEventConsumer.consumerVerificationRequired()`, we check that the transaction is still in `PROCESSING` before generating an OTP.
- In `AccountService.consumeTransactionCompleted()`, if a credit event is re-delivered, idempotency can be enhanced by checking a unique transaction reference number in an audit ledger table."*

---

### Q5. What happens if an attacker enters the wrong OTP during verification?
**Answer:**
*"When the user hits `POST /api/v1/transactions/{txId}/verify?otp=...`:
1. The transaction service retrieves the OTP from Redis key `verification:otp{txId}`.
2. If the entered OTP does not match, the system immediately **deletes the OTP key** from Redis to prevent replay attacks.
3. It emits `fraud.detected` to Kafka; `AccountService` consumes this and immediately flips the account status to `BLOCKED`.
4. It calls `accountServiceClient.creditBalance()` to refund the deducted money back to the sender.
5. It marks the transaction status as `FLAGGED` in MySQL with an audit failure reason.
6. `NotificationService` alerts the legitimate account owner that an unauthorized attempt occurred."*

---

### Q6. How does the API Gateway Rate Limiting work?
**Answer:**
*"We use Spring Cloud Gateway's `RequestRateLimiter` filter powered by Redis and the Token Bucket Algorithm.
- We defined a custom `KeyResolver` bean that extracts the client IP address (`exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()`).
- In `application.yaml`, we set `replenishRate: 10` and `burstCapacity: 20` for accounts and transactions, and `5 / 10` for payments.
- Redis uses Lua scripts internally to atomically decrement tokens. When tokens hit 0, the gateway rejects excess requests with HTTP 429 Too Many Requests."*

---

### Q7. How does the Razorpay Payment Gateway integration work?
**Answer:**
*"In `payment-service`:
1. When a client calls `/api/v1/payments/create-order`, the service uses the Razorpay Java SDK (`RazorpayClient`) to create an order on Razorpay servers with the amount converted into paise/cents (`amount * 100`).
2. We persist the payment in MySQL with status `CREATED`.
3. The frontend displays the Razorpay checkout widget.
4. When the user finishes the payment, Razorpay sends an asynchronous HTTP POST webhook to `/api/v1/payments/webhook`.
5. We parse the payload: if `payment.captured`, we set status to `COMPLETED` and publish `payment.completed` to Kafka for notification; if `payment.failed`, we set status to `FAILED` and publish `payment.failed`."*

---

### Q8. What is the Database Strategy in this project?
**Answer:**
*"We strictly followed the **Database-per-Service** design pattern:
- `a_account_service_db`: Owns customer accounts, credentials, and balances.
- `b_transaction_service_db`: Owns transaction state, references, and audit logs.
- `c_payment_service_db`: Owns payment gateway order IDs and webhook records.
No service has direct database access to another service's tables. All cross-domain operations happen exclusively through REST APIs or Kafka events."*

---

### Q9. What happens if Kafka goes down? How would you make this system more resilient in production?
**Answer:**
*"In our current setup, if Kafka is down, event publishing fails. To make this production-ready, I would implement:
1. **Transactional Outbox Pattern:** Instead of directly publishing to Kafka inside the service method, we write the event into an `outbox_table` inside the same local MySQL ACID transaction as the business entity. A Debezium CDC (Change Data Capture) connector or background poller then reliably streams the outbox table records to Kafka.
2. **Dead Letter Queue (DLQ):** If a consumer fails to process a message after 3 retry attempts, Kafka routes it to a `.DLQ` topic for manual inspection without halting the partition consumer.
3. **Circuit Breakers (Resilience4j):** Wrap the OpenFeign calls to Account Service with circuit breakers and fallback responses."*

---

### Q10. How would you scale this system to handle 10,000 TPS?
**Answer:**
1. **Kafka Partitioning:** Increase topic partitions (e.g., 30 partitions) and scale out consumer instances in `fraud-service-group` and `transaction-service-group`. Partitioning by `accountNumber` ensures all transactions for the same account remain strictly serialized and in-order.
2. **Redis Cluster:** Deploy Redis in a multi-node Cluster configuration with master-replica replication to distribute velocity keys across shards.
3. **Database Read/Write Splitting & Connection Pooling:** Use HikariCP with optimized pool sizing, configure MySQL read-replicas for statement queries (`findBySenderAccountNumber`), and reserve the primary DB for updates.
4. **Caching Account Profiles:** Cache read-heavy account details in Redis and invalidate upon balance modification.

---

## ⚡ Section 3: High-Frequency Quick-Fire Q&A

| Question | Rapid-Fire Answer |
| :--- | :--- |
| **Why Spring Cloud Gateway instead of Zuul?** | Zuul 1.x uses blocking I/O (servlet-per-thread). Spring Cloud Gateway is non-blocking and reactive (Netty/Project Reactor), handling thousands of concurrent connections with minimal memory. |
| **Why use `BigDecimal` for currency instead of `double` or `float`?** | `float` and `double` use binary floating-point representation, causing precision errors (e.g. `0.1 + 0.2 = 0.30000000000000004`). `BigDecimal` provides exact arbitrary-precision arithmetic. |
| **What is the default TTL for the fraud velocity key?** | `60 seconds`. Set using `redisTemplate.expire(key, 60, TimeUnit.SECONDS)` on the first increment. |
| **What is the OTP expiration window?** | `5 minutes`. Set using `redisTemplate.opsForValue().set(key, otp, 5, TimeUnit.MINUTES)`. |
| **Why delete the OTP immediately upon verification?** | To ensure single-use replay protection. Even if an attacker intercepts the OTP, it cannot be submitted twice. |
| **What does `auto-offset-reset: earliest` do?** | When a consumer group connects to a partition without an existing committed offset, it reads from the earliest available offset instead of ignoring older messages. |

