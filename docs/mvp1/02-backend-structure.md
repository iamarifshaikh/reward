# MVP 1 — Backend Structure, API & Security Conventions

Companion to [01-database-design.md](01-database-design.md). This covers how the code is organised, how REST endpoints are written, how security is enforced, and how MVP 2 gets added without disturbing MVP 1.

**Stack:** Java 25 · Spring Boot 4.1 · Gradle · PostgreSQL 17 · Flyway · Spring Modulith.

---

## 1. Architecture in one paragraph

One deployable application, split into **modules by business capability**, not by technical layer. Each module owns its tables, exposes one public facade, and keeps everything else internal. Module boundaries are verified by a test, so a violation fails the build rather than being found in review six weeks later. Inside a module the flow is always `web → application → domain ← infrastructure`. Anything that talks to the outside world (SMS, S3) sits behind an interface owned by the module, with a fake implementation for local development and tests.

**Why package by module and not by layer:** a `controllers/`, `services/`, `repositories/` layout means every new feature edits all three folders, every developer touches the same files, and nothing stops the KYC service from reaching into the token repository. Grouping by capability keeps a change inside one folder and makes the dependency rules enforceable.

---

## 2. Modules in MVP 1

| Module | Owns | Tables | May depend on |
|---|---|---|---|
| `common` | Kernel: base entity, value objects, errors, security context, web helpers, crypto, config | — | nothing |
| `identity` | Login, OTP, tokens, sessions, the user record | `users`, `otp_challenges`, `refresh_tokens` | `common` |
| `organization` | Businesses, partner links, KYC review | `organizations`, `channel_relationships`, `kyc_documents` | `common`, `identity`, `storage` |
| `storage` | Presigned S3 upload and download | — | `common` |
| `audit` | The audit trail | `audit_logs` | `common` |

**Rules that keep this honest**

1. A module may call another module only through its root-package facade: `IdentityApi`, `OrgDirectory`, `StorageApi`, `AuditApi`. Never a repository, never an internal service.
2. No JPA relationships across modules. Reference other modules' rows by `UUID`. `KycDocument.reviewedBy` is a `UUID`, not a `User`.
3. `common` depends on nothing and contains no business rules.
4. Entities never leave their module. Controllers return DTOs.

```java
// organization/package-info.java
@org.springframework.modulith.ApplicationModule(
    displayName = "Organization",
    allowedDependencies = { "common", "identity", "storage" })
package com.intiq.reward.organization;
```

---

## 3. Folder structure

```
reward/
├── build.gradle
├── settings.gradle
├── gradle/libs.versions.toml            # every dependency version in one place
├── docker/docker-compose.yml            # postgres:17, mailpit
├── docs/
│   ├── INTIQ_Rewards_HLD.md             # long-range background only
│   ├── mvp1/01-database-design.md
│   ├── mvp1/02-backend-structure.md     # this file
│   └── adr/                             # one file per significant decision
└── src
    ├── main
    │   ├── java/com/intiq/reward
    │   │   ├── RewardApplication.java
    │   │   │
    │   │   ├── common/
    │   │   │   ├── domain/
    │   │   │   │   ├── BaseEntity.java            # uuid v7 id + equals/hashCode by id
    │   │   │   │   ├── AuditableEntity.java       # createdAt/By, updatedAt/By, version
    │   │   │   │   ├── PhoneNumber.java           # E.164 normalisation + validation
    │   │   │   │   ├── EmailAddress.java
    │   │   │   │   ├── Gstin.java                 # format + checksum
    │   │   │   │   ├── Pan.java
    │   │   │   │   └── UuidV7.java
    │   │   │   ├── error/
    │   │   │   │   ├── ErrorCode.java             # enum: code + http status + message key
    │   │   │   │   ├── DomainException.java
    │   │   │   │   ├── NotFoundException.java
    │   │   │   │   ├── ConflictException.java
    │   │   │   │   └── GlobalExceptionHandler.java  # → RFC 9457 ProblemDetail
    │   │   │   ├── security/
    │   │   │   │   ├── AuthPrincipal.java         # userId, orgId, orgType, contextType
    │   │   │   │   ├── CurrentUser.java           # @CurrentUser argument annotation
    │   │   │   │   ├── OrgAccess.java             # bean "orgAccess" used in @PreAuthorize
    │   │   │   │   └── ActorType.java
    │   │   │   ├── crypto/
    │   │   │   │   ├── PiiEncryptor.java          # AES-256-GCM, key from config/secrets
    │   │   │   │   ├── Hashes.java                # sha256, hmacSha256
    │   │   │   │   └── Masking.java               # phone/email/PAN masking for logs & audit
    │   │   │   ├── web/
    │   │   │   │   ├── PageResponse.java
    │   │   │   │   ├── PageQuery.java
    │   │   │   │   ├── RequestIdFilter.java       # X-Request-Id → MDC → response header
    │   │   │   │   └── ApiPaths.java              # "/api/v1"
    │   │   │   └── config/
    │   │   │       ├── JacksonConfig.java
    │   │   │       ├── CorsConfig.java
    │   │   │       ├── OpenApiConfig.java
    │   │   │       ├── ClockConfig.java           # inject Clock everywhere, never now()
    │   │   │       └── JpaAuditingConfig.java     # fills created_by/updated_by from AuthPrincipal
    │   │   │
    │   │   ├── identity/
    │   │   │   ├── IdentityApi.java               # facade: findById, createBusinessLogin, findOrCreateConsumer, activate
    │   │   │   ├── UserActivatedEvent.java
    │   │   │   ├── web/
    │   │   │   │   ├── AuthController.java        # /auth/**
    │   │   │   │   ├── MeController.java          # /me
    │   │   │   │   └── dto/                       # OtpRequest, OtpVerifyRequest, TokenResponse, MeResponse …
    │   │   │   ├── application/
    │   │   │   │   ├── AuthService.java           # request otp → verify → issue tokens
    │   │   │   │   ├── OtpService.java            # generate, hash, rate-limit, verify
    │   │   │   │   ├── TokenService.java          # issue, rotate, revoke, reuse detection
    │   │   │   │   └── UserService.java           # profile reads/writes
    │   │   │   ├── domain/
    │   │   │   │   ├── User.java · UserStatus.java · ContextType.java
    │   │   │   │   ├── OtpChallenge.java · OtpChannel.java · OtpPurpose.java
    │   │   │   │   └── RefreshToken.java
    │   │   │   └── infrastructure/
    │   │   │       ├── persistence/               # UserRepository, OtpChallengeRepository, RefreshTokenRepository
    │   │   │       ├── security/
    │   │   │       │   ├── SecurityConfig.java    # filter chain, stateless, public paths
    │   │   │       │   ├── JwtIssuer.java         # RS256 sign
    │   │   │       │   ├── JwtProperties.java
    │   │   │       │   ├── JwtAuthConverter.java  # jwt → AuthPrincipal
    │   │   │       │   └── AuthRateLimiter.java   # bucket4j: per destination, per IP
    │   │   │       └── otp/
    │   │   │           ├── OtpSender.java         # PORT
    │   │   │           ├── LoggingOtpSender.java  # local/staging: logs the code
    │   │   │           ├── Msg91OtpSender.java    # production SMS
    │   │   │           └── SesEmailOtpSender.java # production email
    │   │   │
    │   │   ├── organization/
    │   │   │   ├── OrgDirectory.java              # facade: get, requireActive, isActivePartner
    │   │   │   ├── web/
    │   │   │   │   ├── OrganizationController.java      # /orgs/{orgId}
    │   │   │   │   ├── PartnerController.java           # /orgs/{orgId}/partners
    │   │   │   │   ├── KycController.java               # /orgs/{orgId}/kyc/**
    │   │   │   │   ├── AdminOrganizationController.java # /admin/organizations/**
    │   │   │   │   └── dto/
    │   │   │   ├── application/
    │   │   │   │   ├── OrganizationService.java · PartnerService.java · KycService.java
    │   │   │   │   ├── OrgCodeGenerator.java            # BRD-00042 from a sequence
    │   │   │   │   └── OrganizationMapper.java          # MapStruct
    │   │   │   ├── domain/
    │   │   │   │   ├── Organization.java · OrgType.java · OrgStatus.java · KycStatus.java
    │   │   │   │   ├── ChannelRelationship.java · PartnerType.java · ChannelStatus.java
    │   │   │   │   └── KycDocument.java · KycDocType.java · KycDocStatus.java
    │   │   │   └── infrastructure/persistence/
    │   │   │
    │   │   ├── storage/
    │   │   │   ├── StorageApi.java                # presignUpload, presignDownload
    │   │   │   ├── web/FileController.java        # /files/upload-url
    │   │   │   ├── application/ObjectKeyFactory.java
    │   │   │   └── infrastructure/
    │   │   │       ├── StoragePort.java · S3StorageAdapter.java · LocalStorageAdapter.java
    │   │   │       └── StorageProperties.java
    │   │   │
    │   │   └── audit/
    │   │       ├── AuditApi.java                  # record(action, entity, scope, changes)
    │   │       ├── web/AdminAuditController.java
    │   │       ├── application/AuditService.java
    │   │       ├── domain/AuditLog.java · AuditAction.java
    │   │       └── infrastructure/persistence/AuditLogRepository.java
    │   │
    │   └── resources/
    │       ├── application.yml                    # + application-{local,staging,prod}.yml
    │       └── db/migration/                      # V2026_09_23_1000__… (see 01-database-design §6)
    └── test/java/com/intiq/reward
        ├── ModularityTests.java                   # ApplicationModules.verify()
        ├── support/                               # AbstractIntegrationTest (Testcontainers), TestAuth, fixtures
        ├── identity/ · organization/ · storage/ · audit/
        └── e2e/                                   # full flows from 01-database-design §8
```

---

## 4. Layers inside a module

| Layer | Contains | Rules |
|---|---|---|
| `web` | Controllers, request/response DTOs | Thin. Validate input, call one application service, map to DTO. **No** `@Transactional`, no business rules, no entities in signatures |
| `application` | Use-case services, mappers | Owns `@Transactional`. Orchestrates domain + repositories + other modules' facades. Publishes events |
| `domain` | Entities, enums, value objects, domain rules | Pure Java plus JPA annotations. State transitions live on the entity: `user.activate(clock)`, not `user.setStatus(ACTIVE)` |
| `infrastructure` | Repositories, external adapters, security wiring | Implements ports defined by the module. Nothing else may reference these classes |

**Setters are the enemy.** Entities expose intent-revealing methods and keep setters package-private or absent. `organization.approve(adminId, clock)` sets the status, timestamp and approver together, so there is no path that sets one and forgets the others.

---

## 5. REST API conventions

| Topic | Convention |
|---|---|
| Base path | `/api/v1` |
| Namespaces | `/auth/**` public · `/me/**` self · `/orgs/{orgId}/**` tenant-scoped · `/admin/**` platform · `/files/**` |
| Naming | Plural nouns, kebab-case paths, camelCase JSON. Non-CRUD actions are sub-resources: `POST /orgs/{id}/kyc/submit` |
| Methods | `GET` read · `POST` create or action · `PATCH` partial update · `DELETE` unused, status changes instead |
| Success codes | 200, 201 with `Location`, 204 for no body |
| Errors | RFC 9457 `ProblemDetail` with extensions `code`, `traceId`, `errors[]` |
| Error codes | `ErrorCode` enum: `AUTH_OTP_EXPIRED`, `ORG_GSTIN_DUPLICATE`, `KYC_INVALID_STATE`, `COMMON_VALIDATION` … |
| Lists | `PageResponse<T>` = `{items, page, size, totalItems, totalPages}`. `?page=0&size=20&sort=createdAt,desc`, max size 100 |
| Filtering | Explicit params (`status`, `type`, `q`), bound to a `Specification` |
| Validation | Jakarta Validation on DTO records, always `@Valid`. Phone and GSTIN validated by value objects, not regex scattered in services |
| Nulls in PATCH | Absent field = don't change. Use `Optional<T>` fields or an explicit `JsonNullable` wrapper, never "null means clear" by accident |
| Versioning | `/v1` in the path. Additive changes only. A breaking change creates `/v2` for that route alone |
| Docs | springdoc generates OpenAPI. The spec is exported per release and drives the frontend's typed client |
| Time & IDs | ISO-8601 UTC. UUIDs in bodies, never database sequence numbers |
| Tracing | `X-Request-Id` accepted or generated, echoed in the response, in every log line and in `audit_logs.request_id` |

**Controller shape**

```java
@RestController
@RequestMapping(ApiPaths.V1 + "/orgs/{orgId}/partners")
@RequiredArgsConstructor
class PartnerController {

    private final PartnerService partnerService;

    @PostMapping
    @PreAuthorize("@orgAccess.canManagePartners(#orgId)")
    ResponseEntity<PartnerResponse> create(@PathVariable UUID orgId,
                                           @Valid @RequestBody CreatePartnerRequest request,
                                           @CurrentUser AuthPrincipal actor) {
        var created = partnerService.create(orgId, request, actor);
        return ResponseEntity.created(URI.create(...)).body(created);
    }
}
```

---

## 6. Security

**Authentication**

- OTP only. `POST /auth/otp` then `POST /auth/otp/verify`.
- Codes are 6 digits from `SecureRandom`, stored as HMAC-SHA256, valid 5 minutes, max 5 attempts, then the challenge is burned.
- Rate limits (bucket4j): 3 requests per destination per 10 minutes, 20 per IP per hour, 30-second resend cooldown.
- **No user enumeration.** `POST /auth/otp` returns the same 200 whether or not the number exists. The code is only sent if it does.
- Access token: JWT RS256, 15 minutes, claims `sub`, `org`, `otype`, `ctx`, `jti`. Signing keys come from the environment or Secrets Manager, never the repo.
- Refresh token: 30 days, opaque random string, stored hashed, rotated on every use, with family revocation on reuse (§`refresh_tokens.family_id`).
- Refresh token travels in an `httpOnly; Secure; SameSite=Strict` cookie scoped to the refresh path. The access token stays in browser memory.

**Authorisation — two independent checks on every tenant route**

1. **Type check:** `JwtAuthConverter` turns `otype` into an authority such as `ROLE_BRAND`. Admin-only routes require `ROLE_PLATFORM`.
2. **Scope check:** `@PreAuthorize("@orgAccess.canManagePartners(#orgId)")` asserts the path's `orgId` matches the token's `org`, or that the caller is a platform role.

Repositories reinforce it: tenant data is fetched with `findByIdAndOrgId(...)`, so a cross-tenant id returns **404, not 403**, and ids can't be probed. Every endpoint gets a negative test proving brand A cannot read brand B.

**Data protection**

- PAN encrypted with AES-256-GCM (`PiiEncryptor`), plus `pan_hash` for duplicate detection. Masked at the edge for display.
- KYC files live in a private S3 bucket. Upload via presigned PUT valid 5 minutes, with content-type and size limits enforced on the presign request **and** verified after upload. Download via presigned GET valid 2 minutes, only after an access check.
- Logs and `audit_logs.changes` mask phone, email and PAN. Tokens and OTP codes are never logged.
- HTTPS only, HSTS, CORS restricted to known origins, standard security headers, request size caps.

**Auditing**

Every mutating service call records an audit row through `AuditApi` in the same transaction as the change, so a rollback drops both. Login success and failure are recorded too.

---

## 7. Designed for change

| Likely change | What it touches |
|---|---|
| Real SMS or email OTP provider | A new `OtpSender` implementation plus a config property. `AuthService` is untouched |
| Second login per business, with roles | More `users` rows with the same `org_id`, plus a `role` column and one extra check in `OrgAccess` |
| Points, schemes, wallet (MVP 2) | New modules with their own tables, calling `OrgDirectory` and `IdentityApi`. No MVP 1 file changes |
| Runtime settings | A `platform` module with `platform_settings`, read through a cached `SettingsApi` |
| Notifications (MVP 4) | A `notification` module listening to domain events. Nothing publishes to it directly |
| Extract a module into a service | Its facade is already the only entry point, and its tables are already exclusively owned |

**Ports and adapters are used only where the outside world is involved** (`OtpSender`, `StoragePort`). Internal services do not get gratuitous interfaces: one implementation, one class.

**Schema evolution** is additive. Never edit a merged migration. Renames go expand → backfill → contract across two releases.

---

## 8. Build dependencies for MVP 1

Add: `spring-boot-starter-webmvc`, `-validation`, `-data-jpa`, `-security`, `-oauth2-resource-server`, `-actuator`, `-flyway` + `flyway-database-postgresql`, `postgresql`, `springdoc-openapi-starter-webmvc-ui`, `spring-modulith-starter-core` + `-starter-test`, MapStruct + Lombok (+ binding), `bucket4j-core`, `uuid-creator`, AWS SDK v2 `s3`, Testcontainers (`postgresql`, `junit-jupiter`).

Remove from the current `build.gradle`: `spring-boot-starter-session-jdbc` (server-side sessions, replaced by stateless JWT) and `spring-boot-starter-data-jdbc` (two persistence styles in one app invites confusion; `JdbcClient` is still available through the JPA starter for reporting).

Enable virtual threads: `spring.threads.virtual.enabled=true`.

---

## 9. Testing

| Level | What | Tooling |
|---|---|---|
| Unit | Value objects, entity state transitions, OTP hashing and expiry, token rotation rules | JUnit 5, AssertJ |
| Module | Services against a real Postgres, including constraint behaviour | `@SpringBootTest` + Testcontainers |
| Architecture | Module boundaries and cycles | `ApplicationModules.verify()` |
| API | Status codes, validation, ProblemDetail shape, **one cross-tenant negative test per route** | MockMvcTester |
| Flow | The five flows in 01-database-design §8, end to end | Testcontainers |

No mocked repositories in service tests. A real database catches the constraint violations that mocks hide.

---

## 10. Conventions

- Records for DTOs, commands and events. Lombok only on entities, never `@Data`.
- Constructor injection, `final` fields, no field injection.
- Package-private classes by default. `public` is a deliberate act.
- `Clock` injected everywhere, so time-dependent tests are deterministic.
- `@ConfigurationProperties` records per module (`intiq.otp.*`, `intiq.jwt.*`, `intiq.storage.*`). No `@Value` scattered around.
- Conventional commits, short-lived branches, squash merge.
- Definition of done: tests including the negative auth test, migration, OpenAPI annotations, audit hook, deployed to staging.

---

## 11. Build order for MVP 1

1. `common` kernel, `build.gradle`, `application.yml`, docker-compose, `ModularityTests`.
2. Flyway migrations for all 7 tables.
3. `identity`: entities, repositories, OTP with the logging sender, JWT issue and refresh, `/auth/**`, `/me`.
4. `organization`: entities, admin creates brand, brand creates partner, partner list, profile edit.
5. `storage` + KYC upload, submit, admin review.
6. `audit`: aspect and admin listing, wired to every mutation above.
7. Staging deploy, then the end-to-end flow tests.
