# Banking & Fraud Detection Microservices System - Interview Notes & System Design Guide

Welcome to the comprehensive technical documentation and interview preparation guide for the **Distributed Banking & Real-Time Fraud Detection System**.

This folder contains a complete reference to explain this project with authority and clarity during System Design and Java/Microservices technical interviews.

---

## 📚 Document Index

| Document | Purpose & Key Topics Covered |
| :--- | :--- |
| **[01. Services Architecture](file:///d:/Java%20Projects/Banking-system/interview-notes/01_SERVICES_ARCHITECTURE.md)** | Deep-dive into all 6 microservices (`api-gateway`, `account-service`, `transaction-service`, `fraud-detection-service`, `notification-service`, `payment-service`), REST APIs, DB models, and Feign clients. |
| **[02. End-to-End Flows & Diagrams](file:///d:/Java%20Projects/Banking-system/interview-notes/02_END_TO_END_FLOWS_WITH_DIAGRAMS.md)** | Visual Mermaid sequence and state diagrams for Happy Path transfer, OTP Verification, Fraud Detection & SAGA Rollback/Compensation, Razorpay Webhook flow. |
| **[03. Kafka & Redis Deep Dive](file:///d:/Java%20Projects/Banking-system/interview-notes/03_KAFKA_AND_REDIS_DEEP_DIVE.md)** | Complete event streaming specs: 9 Kafka topics, payloads, consumer groups, plus 4 Redis use cases (Rate limiting, Velocity counting, Moving averages, OTP TTL). |
| **[04. SAGA Pattern & Distributed Resiliency](file:///d:/Java%20Projects/Banking-system/interview-notes/04_SAGA_TRANSACTIONS_AND_RESILIENCY.md)** | Dual-write problem, Choreography vs. Orchestration SAGA, Forward steps vs Compensating transactions, Idempotency guarantees, and ACID in distributed systems. |
| **[05. Interview Questions & Answers (Cheat Sheet)](file:///d:/Java%20Projects/Banking-system/interview-notes/05_INTERVIEW_QUESTIONS_AND_ANSWERS.md)** | 25+ real-world interview questions with concise answers, 2-minute elevator pitch script, whiteboard tips, and production trade-offs. |

---

## 🎯 60-Second Elevator Pitch for Interviewers

> *"I designed and built an event-driven, distributed Banking and Fraud Detection microservices ecosystem using Spring Boot 3, Spring Cloud Gateway, Apache Kafka, Redis, and MySQL.*
>
> *The system handles high-throughput peer-to-peer fund transfers with real-time fraud mitigation. When a user initiates a transfer, the API Gateway applies token-bucket rate limiting via Redis. The Transaction Service begins a SAGA workflow by synchronously reserving funds from the sender via OpenFeign and publishing a transaction event to Kafka.*
>
> *An independent Fraud Detection Service asynchronously consumes this event and performs multi-tiered algorithmic checks in sub-milliseconds using Redis in-memory counters: velocity checks (transactions per minute), spending anomaly checks using exponential moving averages, and account balance ratio limits.*
>
> *If flagged as suspicious, the system triggers a two-factor challenge: generating a secure OTP stored in Redis with a 5-minute TTL and notifying the user. If verified, funds are credited; if the OTP fails or expires, a SAGA compensating transaction automatically refunds the sender's account and flags or blocks the offending account. In addition, external payment gateway integration via Razorpay handles card/UPI top-ups via webhook callbacks."*

---

## 🏛️ High-Level System Architecture Diagram

```mermaid
flowchart TB
    Client(["🌐 Client / Web / Mobile App"])

    subgraph Edge ["🛡️ Edge & Gateway Layer"]
        Gateway["API Gateway (Port 8080)<br/>• Route Routing<br/>• Redis Rate Limiter"]
    end

    subgraph Ingest ["⚡ Infrastructure & Storage"]
        Redis[("⚡ Redis (Port 6379)<br/>• Gateway Rate Limiter<br/>• Velocity Tracker (TTL 60s)<br/>• Moving Average Amount<br/>• OTP Store (TTL 5m)")]
        Kafka[("📨 Apache Kafka (Port 9092)<br/>Event Streaming Backbone")]
        MySQL[("🗄️ MySQL 8.0 (Port 3306)<br/>Database-per-Service:<br/>• a_account_service_db<br/>• b_transaction_service_db<br/>• c_payment_service_db")]
    end

    subgraph Services ["📦 Microservices Ecosystem"]
        AccountSvc["🏦 Account Service (Port 8081)<br/>• Balance CRUD<br/>• Deduct & Credit<br/>• Account Blocking"]
        TxnSvc["💳 Transaction Service (Port 8082)<br/>• SAGA Coordinator<br/>• Transfer Orchestration<br/>• OTP Generation & Verify"]
        FraudSvc["🛡️ Fraud Detection Service (Port 8084)<br/>• Velocity Check<br/>• Moving Avg Anomaly<br/>• Balance Ratio Check"]
        NotifSvc["🔔 Notification Service (Port 8085)<br/>• Debit / Credit Alerts<br/>• Fraud Warning<br/>• OTP Alerts<br/>• Refund Alerts"]
        PaymentSvc["💰 Payment Service (Port 8083)<br/>• Razorpay Orders<br/>• Webhook Ingestion"]
    end

    Client -->|HTTP REST| Gateway
    Gateway -->|Rate Limited Proxy| AccountSvc
    Gateway -->|Rate Limited Proxy| TxnSvc
    Gateway -->|Rate Limited Proxy| PaymentSvc

    Gateway -. Rate Limit Check .-> Redis

    TxnSvc -->|1. Sync Deduct (Feign)| AccountSvc
    TxnSvc -->|2. Publish transaction.initiated| Kafka

    Kafka -->|Consume transaction.initiated| FraudSvc
    FraudSvc -. Read/Write Velocity & Avg .-> Redis
    FraudSvc -->|Sync Balance Check (Feign)| AccountSvc

    FraudSvc -->|Clean: fraud.check.clean| Kafka
    FraudSvc -->|Suspicious: verification.required| Kafka

    Kafka -->|Consume fraud.check.clean| TxnSvc
    Kafka -->|Consume verification.required| TxnSvc

    TxnSvc -. Store / Verify OTP .-> Redis
    TxnSvc -->|Compensate: Sync Credit (Feign)| AccountSvc
    TxnSvc -->|Publish transaction.completed| Kafka
    TxnSvc -->|Publish fraud.detected| Kafka
    TxnSvc -->|Publish transaction.refunded| Kafka
    TxnSvc -->|Publish transaction.otp.generated| Kafka

    Kafka -->|Consume transaction.completed| AccountSvc
    Kafka -->|Consume fraud.detected| AccountSvc

    Kafka -->|Consume ALL Alert Topics| NotifSvc

    PaymentSvc -->|Publish payment.completed / failed| Kafka
```

---

## 🔌 Service Port & Database Matrix

| Service | Port | Database Name | Redis Usage | Kafka Role |
| :--- | :---: | :---: | :---: | :---: |
| **API Gateway** | `8080` | None (Stateless) | Rate Limiter Tokens | None |
| **Account Service** | `8081` | `a_account_service_db` | None | Consumer (`transaction.completed`, `fraud.detected`) |
| **Transaction Service** | `8082` | `b_transaction_service_db` | OTP Storage (`verification:otp{txId}`) | Producer & Consumer (SAGA initiator & event dispatcher) |
| **Payment Service** | `8083` | `c_payment_service_db` | None | Producer (`payment.completed`, `payment.failed`) |
| **Fraud Detection Service** | `8084` | None (In-Memory Redis State) | Velocity count (`60s`), Moving Avg | Consumer (`transaction.initiated`) & Producer (`fraud.check.clean`, `verification.required`) |
| **Notification Service** | `8085` | None (Stateless) | None | Pure Consumer (All business notification topics) |
| **Redis** | `6379` | Memory | N/A | Rate limiting, Fraud window state, OTP cache |
| **Kafka Broker** | `9092` | Log files | N/A | Distributed Event Broker |
| **Zookeeper** | `2181` | Metadata | N/A | Kafka cluster coordinator |

---

## 🚀 Quick Navigation to Read Next
- Read **[01_SERVICES_ARCHITECTURE.md](file:///d:/Java%20Projects/Banking-system/interview-notes/01_SERVICES_ARCHITECTURE.md)** for a line-by-line understanding of all microservices.
- Read **[02_END_TO_END_FLOWS_WITH_DIAGRAMS.md](file:///d:/Java%20Projects/Banking-system/interview-notes/02_END_TO_END_FLOWS_WITH_DIAGRAMS.md)** to visualize all transaction flows.

