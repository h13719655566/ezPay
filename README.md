# 🚀 ezPay

Secure card encryption • Webhooks • Retry Queue • HMAC Signature • OpenAPI 3.0

![Java](https://img.shields.io/badge/Java-22-007396?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.x-6DB33F?logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql)
![OpenAPI](https://img.shields.io/badge/OpenAPI-3.0-85EA2D?logo=openapi-initiative)
![Architecture](https://img.shields.io/badge/Architecture-Event--Driven-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

A lightweight payment processing demo featuring encrypted card storage, event-driven architecture, reliable webhooks, and exponential-backoff retry.

## ✨ Overview
ezPay demonstrates a clean and extensible workflow for modern payment systems:
- AES-encrypted card handling
- Event-driven design using `@TransactionalEventListener`
- Outbox-style webhook task generation
- Guaranteed webhook dispatch with HMAC-SHA256 signatures
- Exponential backoff retry strategy
- Persistent logging of webhook attempts
- Fully documented API via OpenAPI 3.0.1 (as required)

## 🏗 Architecture Overview

ezPay follows a clean, event-driven and transaction-safe workflow inspired by modern payment platforms.

---

## 1. Payment Creation Flow

1. **Client calls** `POST /api/payments`.

2. `PaymentService`:
   - Encrypts the card number using **AES-GCM**.
   - Extracts the last 4 digits for display.
   - Saves the payment using `PaymentPersistenceService`  
     (runs in **REQUIRES_NEW** to avoid marking the main transaction as rollback-only during retries).

3. After saving successfully, a `PaymentCreatedEvent` is published.

---

## 2. AFTER_COMMIT Webhook Task Creation

- `WebhookEventListener` receives the event **after the main transaction commits**.
- `WebhookTaskService` loads all registered webhook endpoints and inserts one row into the `webhook_delivery` table for each endpoint.

---

## 3. Scheduled Webhook Dispatcher

A scheduler runs every 5 seconds:

1. Loads tasks where  
   `success = false` **and** `nextRetryAt < now`.

2. The dispatcher generates an **HMAC-SHA256** signature using the endpoint’s Base64-encoded secret key.
   The signed message is constructed as: `<timestamp>.<json_payload>`
   The signature is placed in the HTTP header using the following format: 
   `Ezpay-Signature: t=<timestamp>, v1=<hex_signature>`

3. Sends the POST request with:
- JSON payload  
- HMAC signature header

4. Logs results:
- HTTP status code  
- response body  
- attempt count  
- exponential-backoff `nextRetryAt`  
- success flag

---

## 4. Reliability Guarantees

- Webhook tasks are persisted before dispatch, ensuring message durability.
- Retries use exponential backoff with a maximum attempt limit.
- Merchant-side failures **do not affect** the main payment transaction.
- Each endpoint has its own stored secret for secure webhook verification.

```mermaid
flowchart TD

%% =========================
%% Client → Payment Creation
%% =========================
Client[[Client]] 
    -->|POST /api/payments| PC[PaymentController]
PC --> PS[PaymentService]

%% Encrypt + Save (with REQUIRES_NEW)
PS -->|Encrypt card (AES-GCM)\nExtract last4| ENC[CryptoUtil / CardUtil]
PS -->|Save with retry| PERS[PaymentPersistenceService]
PERS -->|saveAndFlush| DB1[(payment)]

%% Publish Event
PS -->|publishEvent(...)| EVT[PaymentCreatedEvent]

%% =========================
%% AFTER_COMMIT → Create Tasks
%% =========================
EVT -->|AFTER_COMMIT| L[WebhookEventListener]
L --> TS[WebhookTaskService]

TS -->|Select all endpoints| EPDB[(webhook_endpoint)]
TS -->|Insert webhook tasks| DB2[(webhook_delivery)]

%% =========================
%% Scheduled Dispatcher
%% =========================
SCHED[WebhookScheduler\n@Scheduled(fixedDelay=5s)]
    --> DISPATCH[WebhookDispatcher]

DISPATCH -->|Load pending tasks\n(success=false AND nextRetryAt < now)| DB2
DISPATCH -->|POST Webhook\nJSON + HMAC-SHA256| MERCHANT[[Merchant Webhook]]

MERCHANT -->|HTTP response| DISPATCH

%% Update Logs
DISPATCH -->|Update attempt, statusCode,\nresponseBody, nextRetryAt| DB2
```

## ⚙️ Tech Stack

### Language
- Java 22

### Framework
- Spring Boot 3

### Database & Persistence
- MySQL 8
- Spring Data JPA (Hibernate)

### API & Documentation
- Springdoc OpenAPI (Swagger UI)

### Security
- AES Encryption
- HMAC-SHA256 (Webhook Signature)

### Architecture
- Event-Driven Design  
- Outbox Pattern (Webhook Queue)
- Scheduled Dispatcher with Exponential Backoff

## 📦 Setup
ezPay uses JPA `ddl-auto=update`; tables are generated automatically.

### 1. Clone repository
```bash
git clone git@github.com:h13719655566/ezyPay.git
cd ezpay
```

### 2. Create database
```sql
CREATE DATABASE ezpay DEFAULT CHARACTER SET utf8mb4;
```

### 3. Configure application.properties
```
Create or edit `src/main/resources/application.properties` and fill in your own database credentials and encryption key:

```properties
spring.application.name=ezpay

# ===============================
# Database Configuration (MySQL)
# ===============================
spring.datasource.url=jdbc:mysql://localhost:3306/ezpay?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ===============================
# JPA / Hibernate Configuration
# ===============================
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# ==========================================
# Webhook Dispatcher settings
# ==========================================
# Interval for webhook retry jobs (ms)
app.webhook.dispatcher.fixedDelayMillis=5000

# ===============================
# AES-256 Encryption Key
# ===============================
# Must be exactly 32 bytes (256-bit)
app.encryption.key=YOUR_32_BYTE_AES_KEY
```

### 4. Run
```bash
mvn spring-boot:run
```

## 🔌 API Endpoints

### Create Payment
`POST /api/payments`
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "zipCode": "2500",
  "cardNumber": "4111111111111111",
  "amount": 1000,
  "currency": "AUD"
}
```

### Register Webhook
`POST /webhooks/register`
```json
{
  "url": "https://merchant.com/webhook"
}
```

## 📬 Webhook Delivery

### Payload
```json
{
  "paymentId": "pay_xxx",
  "amount": 1000,
  "currency": "AUD"
}
```

### Headers
```
Ezpay-Signature: t=<timestamp>, v1=<hmac>
Content-Type: application/json
```

### Signature Algorithm
```
message = timestamp + "." + payload
signature = HMAC_SHA256(secret, message)
```

## 🔁 Retry Strategy
Webhook retry uses exponential backoff:

| Attempt | Delay |
|--------|--------|
| 1 | 5s |
| 2 | 10s |
| 3 | 20s |
| 4 | 40s |
| 5 | 80s |

Retries stop after max attempts or on any 2xx success.

## 🗄 Database Schema (auto-generated)

### payment
- paymentId  
- encryptedCardNumber  
- last4  
- amount  
- currency  
- createdAt  

### webhook_endpoint
- id  
- url  
- secret  
- failureCount  
- createdAt  

### webhook_delivery
- endpointId  
- paymentId  
- payload  
- attempt  
- statusCode  
- responseBody  
- success  
- nextRetryAt  
- createdAt  

## 📘 API Documentation

The project complies with **OpenAPI 3.0.1** specifications.

**Swagger UI:**  
```
http://localhost:8080/swagger-ui.html
```

**JSON Spec (Live):**  
```
http://localhost:8080/v3/api-docs
```

**Static Spec:**  
A static `openapi.json` file is included at the root of this repository.

## 🤖 AI Assistance Disclosure
As required by the challenge instructions, AI-assisted tools (ChatGPT) were used during the development of this solution.  
A transcript of prompts and iterations is included with the submission.

## 📄 License
MIT
