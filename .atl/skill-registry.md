# Skill Registry

**Delegator use only.** Any agent that launches sub-agents reads this registry to resolve compact rules, then injects them directly into sub-agent prompts. Sub-agents do NOT read this registry or individual SKILL.md files.

Project: OdontoGestion (Spring Boot + Spring Security session, JPA/Hibernate, PostgreSQL + Flyway, JUnit 5 + Testcontainers strict TDD, TypeScript vanilla + Vite, Gradle).

## User Skills

| Trigger | Skill | Path |
|---------|-------|------|
| Adding/changing Spring Boot features: controllers, validation, ProblemDetail, config, caching, scheduling | java-springboot | C:/Users/thoma/.claude/skills/java-springboot/SKILL.md |
| JPA/Hibernate entities, relationships, N+1, transactions, auditing, pooling | jpa-patterns | C:/Users/thoma/.claude/skills/jpa-patterns/SKILL.md |
| Spring Data JPA repositories, @Query, pagination, auditing, UUID PKs | spring-data-jpa | C:/Users/thoma/.claude/skills/spring-data-jpa/SKILL.md |
| Spring Security authn/authz, validation, CSRF, secrets, headers, rate limiting | springboot-security | C:/Users/thoma/.claude/skills/springboot-security/SKILL.md |
| Adding features/fixing bugs/refactoring in Spring Boot with TDD | springboot-tdd | C:/Users/thoma/.claude/skills/springboot-tdd/SKILL.md |
| Writing tests, @Test, slices, Testcontainers in Spring Boot | spring-boot-test-patterns | C:/Users/thoma/.claude/skills/spring-boot-test-patterns/SKILL.md |
| Designing/reviewing PostgreSQL schema, types, indexes, constraints | postgresql-table-design | C:/Users/thoma/.claude/skills/postgresql-table-design/SKILL.md |
| Complex TypeScript types: generics, conditional, mapped, utility types | typescript-advanced-types | C:/Users/thoma/.claude/skills/typescript-advanced-types/SKILL.md |
| Writing schema/data migrations, concurrent indexes, backfills, rollback, zero-downtime | database-migrations | C:/Users/thoma/.claude/skills/database-migrations/SKILL.md |
| Schema transformations, data transformations, rollback strategies across ORMs | database-migration | C:/Users/thoma/.claude/skills/database-migration/SKILL.md |

Skipped (workflow/foundation, not coding skills): active-orchestrator, agent-instruction, find-skill, kb-creator, roadmap-generator, skill-creator, skill-registry.

## Compact Rules

### java-springboot
- Detect before writing: read build.gradle for Boot version, Java version, starters, migration tool, package layout; match existing conventions.
- Never upgrade Boot/Java/deps implicitly; no explicit versions for BOM-managed artifacts; ask before adding infra (broker, cache, scheduler).
- Package by feature; constructor injection; controllers return DTOs/records, never entities; services own the transaction.
- Bean Validation on request records with `@Valid`; one `@RestControllerAdvice` returning `ProblemDetail`.
- Boot 3 uses `spring-boot-starter-web` + Jackson 2 (`com.fasterxml.jackson.databind`); Boot 4 uses `starter-webmvc` + Jackson 3 (`tools.jackson.databind`).
- Put `@EnableJpaAuditing/@EnableCaching/@EnableAsync/@EnableScheduling` on dedicated `@Configuration` classes, not on `@SpringBootApplication` (slice tests break); `@Import` them in slices.
- `@Transactional/@Cacheable/@Async` are proxy-based: no effect on self-invocation or private/final methods.
- Verify with `./gradlew build`; Testcontainers need Docker; no secrets in code/properties/logs.

### jpa-patterns
- Default lazy loading; avoid EAGER on collections; use `JOIN FETCH`/`@EntityGraph` or DTO projections on read paths (N+1).
- `@Enumerated(EnumType.STRING)`; `@Column(nullable=false, length=...)`; auditing via `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate/@LastModifiedDate`.
- `@OneToMany(mappedBy=..., cascade=ALL, orphanRemoval=true)` only for true aggregates; keep both sides in sync.
- `@Transactional` on service methods, `readOnly=true` for reads; keep transactions short.
- Pagination via `PageRequest` + Sort; cursor-style with `id > :lastId`.
- Index FK/filter columns, composite indexes matching queries; batch writes with `saveAll` + `hibernate.jdbc.batch_size`; tune HikariCP.

### spring-data-jpa
- Repositories extend `JpaRepository<T,ID>`; derived queries for simple cases, `@Query` with `@Param` for complex; return `Optional<T>` for single results.
- `@Modifying` for update/delete queries; `@Transactional(readOnly=true)` for reads, explicit transactions for writes.
- Use `@EntityGraph` against N+1; `Pageable` for pagination on indexed columns; `EXPLAIN ANALYZE` large queries.
- Verify cascade/orphanRemoval behavior in a test before relying on it; auditing via `@CreatedDate` (`updatable=false`).

### springboot-security
- Browser session app: keep CSRF ENABLED and send token via header/form (disable only for stateless Bearer APIs).
- Session cookies: `httpOnly`, `Secure`, `SameSite=Strict`; deny by default; `@EnableMethodSecurity` + `@PreAuthorize` for authorization.
- Passwords: `PasswordEncoder` bean (BCrypt/Argon2), never plaintext.
- `@Valid` + Bean Validation on all DTOs; parameterized queries only, never concatenate SQL.
- CORS at security filter level, no `*` origins in production; configure security headers; rate limit login/expensive endpoints (429).
- Secrets from env/placeholders, never in application.yml; never log secrets/tokens/PII (patient data).
- Scan dependencies (OWASP/Snyk) and keep Boot/Security supported.

### springboot-tdd
- Red -> Green -> Refactor; write failing test first; enforce coverage (JaCoCo, target 80%+).
- Unit: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), Arrange-Act-Assert, explicit stubbing, `@ParameterizedTest` for variants.
- Web layer: `@WebMvcTest` + MockMvc; persistence: `@DataJpaTest` with `Replace.NONE` against Testcontainers Postgres; full: `@SpringBootTest @AutoConfigureMockMvc`.
- Testcontainers: reusable Postgres container, wire via `@DynamicPropertySource` (or `@ServiceConnection`).

### spring-boot-test-patterns
- Right test type: plain Mockito for unit, `@DataJpaTest` repos, `@WebMvcTest` controllers, `@SpringBootTest` sparingly.
- Prefer `@ServiceConnection` (Boot 3.5+) over `@DynamicPropertySource` for containers; reuse containers (`withReuse(true)`).
- Never `@DirtiesContext` unless unavoidable; avoid varying `@MockBean`/`@TestPropertySource` configs (context cache misses).
- Tests independent, no order reliance, no static mutable state; init data explicitly in `@BeforeEach`.
- Mock external services, real DB only when needed; Docker required in CI.

### postgresql-table-design
- PK: `BIGINT GENERATED ALWAYS AS IDENTITY` (UUID only if opaque/distributed); normalize to 3NF.
- Types: `TIMESTAMPTZ` for time, `NUMERIC` for money, `TEXT` (+CHECK for length), `BOOLEAN NOT NULL`, snake_case names; enums only for small stable sets, else TEXT+CHECK or lookup table.
- NOT NULL wherever required, DEFAULTs for common values; FK with explicit ON DELETE; PostgreSQL does NOT auto-index FKs: index them manually.
- UNIQUE allows multiple NULLs: use `NULLS NOT DISTINCT` (PG15+) when needed.
- `EXCLUDE USING gist` prevents overlapping ranges (e.g. double-booked appointments; needs btree_gist for equality columns).
- Partial/expression/composite indexes matching real queries; JSONB only for semi-structured attributes.

### typescript-advanced-types
- Use generics with constraints (`T extends ...`) for reusable typed helpers; prefer `unknown` over `any`; enable `strict`.
- Use mapped/conditional/template-literal/utility types (`Pick`, `Omit`, `Partial`, `Record`, `ReturnType`) to derive types instead of duplicating.
- Discriminated unions + type guards for API/state modeling; type API responses (DTOs) shared with REST contract.
- Avoid deeply nested conditional types and deep recursion (compiler performance); keep types simple.

### database-migrations
- Forward-only in production; every migration reversible or explicitly marked irreversible; document rollback plan.
- New columns on populated tables: nullable or with compatible DEFAULT; create indexes `CONCURRENTLY` (outside a transaction block; Flyway needs `executeInTransaction=false` for that script).
- Backfills in a separate migration from schema change, batched; never mix DDL and large data changes.
- Rename/remove via expand-contract (add new, dual-write, backfill, switch, drop later).
- Review lock modes; test against production-like data.

### database-migration
- Zero-downtime changes: add column with default, rename via add-copy-drop, change types via new column + backfill.
- Wrap migrations in transactions where possible; keep checkpoints/backups for rollback; write explicit down/rollback steps.
- Validate data after transformation (row counts/constraints) before dropping old structures.

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| (none) | - | Project CLAUDE.md / AGENTS.md do not exist yet. Foundation artifacts: knowledge-base/ (13 files), CHANGES.md, openspec/ |

Read the convention files listed above for project-specific patterns and rules. All referenced paths have been extracted — no need to read index files to discover more.
