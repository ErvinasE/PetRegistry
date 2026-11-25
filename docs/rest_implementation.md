# Pet Registry — REST API Build Plan (LAB3)

I am building this service myself to learn. Work through the milestones in order, one at a time.
A milestone is done only when its **Done when** list passes. Explain every new Spring concept the first time it appears.

**Task:** *Darbo tikslas – susipažinti su internetinių puslapių ir paslaugų veikimo principais ir juos pritaikyti duomenų
mainams tarp kelių sistemų.* Build a RESTful web service that the Android app will use later. It shares one database with the JavaFX desktop app.

## Project facts

- Separate project: `pet-registry-api/`, next to the desktop app in the same repo
- Java 21, **Spring Boot 4.x** (latest from start.spring.io), Maven
- Root package: `com.example.petregistry.api`
- Database: **the same** MySQL / XAMPP `pet_registry` DB as LAB2, created by `docs/schema.sql` (`schema-lab.sql`)
- DB access: Spring Data JPA (Hibernate). The desktop app keeps using JDBC
- Test requests: `pet-registry-api/api-demo.http` (IntelliJ HTTP Client) or Postman

## Grading

| # | Requirement | Points | Where |
| --- | --- | --- | --- |
| 1 | The web service runs (you can send a request and get a response) | 1 | R1 |
| 2 | Data comes back as HTML, JSON or another format | 1 | R1 (HTML), every endpoint (JSON) |
| 3 | Parameters / data sent to the server: `@PathVariable`, `@RequestParam`, `@RequestBody` | 2 | R4, then everywhere |
| 4 | CRUD for the "vairuotojas" and the "klientas", i.e. what the Android app needs | 5 | R4–R8 |

---

## 0. Read this first — issues to settle before you write code

1. **"Vairuotojas ir klientas" comes from a template task (taxi app), not from your project.** In this project the
   Android app is used by **pet owners** (klientas) and **caretakers** (the one who does the job, the "vairuotojas").
   Admins and company managers use the desktop app. This plan maps it that way:
   **klientas = `PetOwner`, vairuotojas = `Caretaker`.** Confirm it with the lecturer in one sentence before R4.
2. **Spring Boot 4 is not what most tutorials show.** Boot 4.0 came out in November 2025. `spring-boot-starter-web`
   is now `spring-boot-starter-webmvc`, Jackson 3 replaced Jackson 2, and Java 21 is the minimum. Boot 3.5's free
   support ended in June 2026, so use Boot 4. If a tutorial says `spring-boot-starter-web` or
   imports `com.fasterxml.jackson.databind`, it's Boot 3. The controller annotations in this plan are the same in both.
3. **Hibernate must not touch the schema.** The desktop app's `schema.sql` owns the tables. Set
   `spring.jpa.hibernate.ddl-auto=none`. `update` or `create` would change or drop tables the desktop app depends on.
   `validate` sounds safe, but it fails on harmless differences (`CHAR(60)` vs `VARCHAR`, MySQL `ENUM` columns).
4. **Never return entities from controllers, return DTOs.** Entities have back-references (`owner.pets` → `pet.owner` → …),
   so Jackson either loops forever or hits a lazy-loading error. They also carry `passwordHash`, which must never leave the server.
5. **There is no real security in this lab.** The login endpoint checks the password, but any client can still call
   `GET /api/owners/5` directly. That's acceptable here. Don't call it secure in the report. Spring Security + tokens
   belong to a later stage.
6. **Same rules as the desktop app.** Validation, delete rules and status transitions are copied from `BUILD_PLAN.md`
   (see Rules below). Both systems write to the same DB, so they must refuse the same things.
   The rules exist twice (desktop + API). That's expected when two separate programs share a database.

### Design decisions

| Decision | Why |
| --- | --- |
| Separate Spring Boot project, same DB | That is "data exchange between systems": create an owner through the API and it shows up in the desktop app after Refresh |
| JPA entity for `Service` is called **`CareService`** | `Service` clashes with Spring's `@Service` annotation. The table stays `services` |
| `User` + 4 subclasses = **`SINGLE_TABLE` inheritance** on `role` | Matches the one `users` table. Map all 4 subclasses, even the ones the API doesn't serve (see R2) |
| Request and response DTOs are Java `record`s with a static `from(entity)` | Short, no extra library. You see exactly what goes into the JSON |
| Errors use Spring's `ProblemDetail` (standard JSON error format) | One error shape for the Android app to parse |
| Who's acting comes from the URL (`/api/caretakers/{id}/reservations/...`) | No login tokens yet. The service checks that the reservation really belongs to that caretaker/owner |

## Architecture

```
HTTP client (Postman / .http / Android)
        │  JSON
        ▼
controller (@RestController)  →  service (@Service, rules, @Transactional)  →  repository (JpaRepository)  →  MySQL pet_registry
        ▲                                   │                                                                    ▲
        └──────── DTO records ◄─────────────┘                                                                    │
                                                                                     JavaFX desktop app (JDBC) ──┘
```

- Controllers only read parameters, call one service method, and return a DTO plus a status code. No rules, no repositories.
- Services hold all rules and convert entities ↔ DTOs **inside** the transaction.

Packages: `entity`, `repository`, `dto`, `service`, `controller`, `exception`.

## HTTP status codes used

| Code | When |
| --- | --- |
| 200 OK | GET, PUT, PATCH succeeded |
| 201 Created | POST succeeded. Also send a `Location: /api/owners/7` header |
| 204 No Content | DELETE succeeded |
| 400 Bad Request | Validation failed (body holds a `fields` map: field → message), bad JSON, bad enum value |
| 401 / 403 | Login: wrong password / blocked or wrong role |
| 404 Not Found | No object with that id (or it doesn't belong to the owner/caretaker in the URL) |
| 409 Conflict | A business rule refuses it: delete blocked by reservations, illegal status change |

---

## R0 · Project setup

- start.spring.io: Maven, Java 21, latest Spring Boot **4.x**, group `com.example`, artifact `pet-registry-api`,
  package `com.example.petregistry.api`.
  Dependencies: **Spring Web**, **Spring Data JPA**, **Validation**, **MySQL Driver**. Then add `org.mindrot:jbcrypt:0.4` by hand
  (the same BCrypt the desktop uses, so passwords from either app work in the other).
- Don't add Spring Security. It locks every endpoint behind a login page by default.
- `src/main/resources/application.properties`:

```properties
spring.application.name=pet-registry-api
server.port=8080

spring.datasource.url=jdbc:mysql://localhost:3306/pet_registry?serverTimezone=Europe/Vilnius
spring.datasource.username=root
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.show-sql=true
```

`open-in-view=false` forces all DB work to finish inside the service. That's why DTOs are built there.

**Done when:** XAMPP is running, the DB exists from `schema.sql`, and `mvn spring-boot:run` starts with
`Tomcat started on port 8080` and no errors.

## R1 · The service answers: JSON and HTML (req. 1, 2)

- `HealthController`: `GET /api/health` → `{"status":"UP","time":"2026-11-18T10:15:00","owners":3}`.
  The count comes from the DB, which shows the service and the DB connection both work.
- `HtmlController`:
  - `GET /` → a small HTML page: title, the health status, and links to the endpoints.
  - `GET /view/services` → an HTML `<table>` of all services from the DB (title, type, price, company).
  - Both use `@GetMapping(value = "...", produces = MediaType.TEXT_HTML_VALUE)` and return a `String`. Build the table with a
    `StringBuilder` and escape text with `HtmlUtils.htmlEscape(...)` so a service title can't break the page.
- Add `api-demo.http` with the first 3 requests. Keep adding to this file in every milestone. It's the grading demo.

**Done when:** the browser shows `/` and `/view/services` as pages; `/api/health` shows JSON; in the `.http` response
`Content-Type` is `text/html` for the first two and `application/json` for the third.

## R2 · Entities (JPA mapping of the existing tables)

Write the entities from `docs/lab1-classes.puml`, with only the fields the tables have.

- **Column names map themselves.** Spring Boot turns `firstName` into `first_name` automatically. Use `@Column(name=...)`
  only for the exceptions in the table below.
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;` (`Integer`, so `null` = not saved yet).
- Enums: `@Enumerated(EnumType.STRING)`. The default (`ORDINAL`) would store 0, 1, 2… and break the desktop app.
- Relations: `@ManyToOne(fetch = FetchType.LAZY)` everywhere. The `@OneToMany(mappedBy = ...)` lists
  (`PetOwner.pets`, `Company.caretakers`) are only for reading.
- `createdAt`: set it in a `@PrePersist` method (`createdAt = LocalDateTime.now()`), so the response after POST already has it.

| Java | Database | Mapping |
| --- | --- | --- |
| `User` + `PetOwner`, `Caretaker`, `CompanyManager`, `Administrator` | `users` + `role` column | `@Inheritance(strategy = SINGLE_TABLE)`, `@DiscriminatorColumn(name = "role")`, `@DiscriminatorValue("PET_OWNER")` etc. |
| `CareService` | `services` | `@Entity @Table(name = "services")` |
| `CareService.allowedSpecies` / `allowedSizes` | `service_species` / `service_sizes` | `@ElementCollection` + `@CollectionTable(name = "service_species", joinColumns = @JoinColumn(name = "service_id"))` + `@Column(name = "species")` + `@Enumerated(STRING)` |
| `Reservation.start` / `end` | `start_at` / `end_at` | `@Column(name = "start_at")` |
| `Review` | – | not needed in this lab; skip it |

```java
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING)
public abstract class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String username;
    private String passwordHash;          // → password_hash automatically
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean blocked;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
    // getters / setters
}

@Entity
@DiscriminatorValue("PET_OWNER")
public class PetOwner extends User {
    private String address;

    @OneToMany(mappedBy = "owner")
    private List<Pet> pets = new ArrayList<>();
}
```

**Why map all 4 user subclasses:** `UserRepository.existsByUsername(...)` searches every role. If Hibernate finds
`role = 'ADMIN'` and there is no `@DiscriminatorValue("ADMIN")` class, the query fails.

- Repositories: one `interface XxxRepository extends JpaRepository<Xxx, Integer>` each for `User`, `PetOwner`, `Caretaker`,
  `Company`, `CareService`, `Pet`, `Reservation`. `JpaRepository<PetOwner, Integer>` already adds `WHERE role = 'PET_OWNER'`.

**Done when:** a temporary `CommandLineRunner` bean prints the number of owners, caretakers, services and pets, and
the numbers match what the desktop app shows. Delete the runner afterwards.

## R3 · DTOs and error handling

**DTOs** (`dto` package), one pair per resource:

```java
public record OwnerRequest(
        @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "\\w+") String username,
        String password,                                   // checked in the service (create vs update differ)
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Pattern(regexp = "^$|\\+370\\d{8}") String phone,
        String address) {}

public record OwnerResponse(Integer id, String username, String email, String firstName,
                            String lastName, String phone, String address, boolean blocked,
                            LocalDateTime createdAt) {
    public static OwnerResponse from(PetOwner o) {
        return new OwnerResponse(o.getId(), o.getUsername(), o.getEmail(), o.getFirstName(),
                o.getLastName(), o.getPhone(), o.getAddress(), o.isBlocked(), o.getCreatedAt());
    }
}
```

- Put the Lithuanian messages on the annotations: `@NotBlank(message = "Privalomas laukas")`.
- Response DTOs show related objects as **id + name** (`ownerId`, `ownerName`), never as nested entities.

**Exceptions** (`exception` package): `NotFoundException` (404), `BusinessRuleException` (409),
`InvalidInputException` (400, holds a `Map<String, String>` field → message).
Don't call the last one `ValidationException`, because `jakarta.validation.ValidationException` already exists and the imports get mixed up.

**`ApiExceptionHandler`** (`@RestControllerAdvice`) turns them into one JSON shape:

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail conflict(BusinessRuleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidInputException.class)
    public ProblemDetail invalid(InvalidInputException e) {
        return withFields(e.getErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)       // @Valid on a @RequestBody failed
    public ProblemDetail beanValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return withFields(fields);
    }

    private ProblemDetail withFields(Map<String, String> fields) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Neteisingi duomenys");
        pd.setProperty("fields", fields);
        return pd;
    }
}
```

Also handle `HttpMessageNotReadableException` (broken JSON) and `MethodArgumentTypeMismatchException`
(`?species=DOGG`, `/api/owners/abc`) as 400 with a short message.

**Done when:** a request with an empty body returns 400 with a `fields` map listing all bad fields at once, and no stack trace reaches the client.

## R4 · Owners CRUD — the template for every other resource (req. 3, 4)

This milestone uses all three annotations. Copy its pattern in R5–R8.

| Method | Path | Input | OK | Errors |
| --- | --- | --- | --- | --- |
| GET | `/api/owners` | `@RequestParam` `search`, `blocked` (both optional) | 200 list | – |
| GET | `/api/owners/{id}` | `@PathVariable` `id` | 200 | 404 |
| POST | `/api/owners` | `@RequestBody` `OwnerRequest` | 201 + `Location` | 400 |
| PUT | `/api/owners/{id}` | `@PathVariable` + `@RequestBody` | 200 | 400, 404 |
| DELETE | `/api/owners/{id}` | `@PathVariable` | 204 | 404, 409 |

```java
@RestController
@RequestMapping("/api/owners")
public class OwnerController {

    private final OwnerService ownerService;

    public OwnerController(OwnerService ownerService) {          // constructor injection, no @Autowired needed
        this.ownerService = ownerService;
    }

    @GetMapping
    public List<OwnerResponse> list(@RequestParam(required = false) String search,
                                    @RequestParam(required = false) Boolean blocked) {
        return ownerService.search(search, blocked);
    }

    @GetMapping("/{id}")
    public OwnerResponse get(@PathVariable int id) {
        return ownerService.get(id);
    }

    @PostMapping
    public ResponseEntity<OwnerResponse> create(@Valid @RequestBody OwnerRequest body) {
        OwnerResponse created = ownerService.create(body);
        return ResponseEntity.created(URI.create("/api/owners/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public OwnerResponse update(@PathVariable int id, @Valid @RequestBody OwnerRequest body) {
        return ownerService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        ownerService.delete(id);
    }
}
```

**`OwnerService`** (`@Service`, `@Transactional` on writes, `@Transactional(readOnly = true)` on reads):
- `create`: username and email unique (`userRepository.existsByUsername/Email`), password required and at least 8 chars,
  store `BCrypt.hashpw(raw, BCrypt.gensalt())`. Collect every problem into one `InvalidInputException`.
- `update`: same checks, but uniqueness ignores this owner (`existsByEmailAndIdNot`). A blank password keeps the old hash.
- `delete`: the delete rule (see Rules) → `BusinessRuleException` with a readable Lithuanian message.
- **Filtering happens in SQL**, as in LAB2. A `null` parameter means "don't filter":

```java
@Query("""
       SELECT o FROM PetOwner o
       WHERE (:text IS NULL
              OR LOWER(o.firstName) LIKE LOWER(CONCAT('%', :text, '%'))
              OR LOWER(o.lastName)  LIKE LOWER(CONCAT('%', :text, '%'))
              OR LOWER(o.email)     LIKE LOWER(CONCAT('%', :text, '%')))
         AND (:blocked IS NULL OR o.blocked = :blocked)
       ORDER BY o.lastName, o.firstName""")
List<PetOwner> search(@Param("text") String text, @Param("blocked") Boolean blocked);
```

**Done when** (every case is a request in `api-demo.http`):
- POST creates an owner → 201, `Location` header, and **the owner appears in the desktop app after Refresh**
- GET by id, GET with `?search=jon`, GET with `?blocked=false` all return the right owners
- PUT changes the phone; a blank password keeps the old one (log in through the desktop app to check)
- a duplicate email → 400 `fields.email`; a missing id → 404
- DELETE an owner with reservations → 409; DELETE one without → 204, and their pets are gone too

## R5 · Owner's pets (nested resource)

| Method | Path | Input | OK | Errors |
| --- | --- | --- | --- | --- |
| GET | `/api/owners/{ownerId}/pets` | `@PathVariable` + `@RequestParam` `species` (optional enum) | 200 | 404 owner |
| POST | `/api/owners/{ownerId}/pets` | `@PathVariable` + `@RequestBody` `PetRequest` | 201 | 400, 404 |
| GET | `/api/pets/{id}` | `@PathVariable` | 200 | 404 |
| PUT | `/api/pets/{id}` | `@PathVariable` + `@RequestBody` | 200 | 400, 404 |
| DELETE | `/api/pets/{id}` | `@PathVariable` | 204 | 404, 409 |

- `PetRequest`: `name`, `species`, `size`, `birthDate`, `weightKg`, `notes`. The owner comes from the URL, not the body.
- An enum `@RequestParam` (`Species species`) is converted by Spring. A wrong value → 400 (from R3).
- No photo upload in this lab. `photoPath` is returned read-only.

**Done when:** an owner's pets can be listed, filtered by species, added, edited and deleted; a pet with reservations → 409.

## R6 · Caretakers CRUD

Same pattern as R4, at `/api/caretakers`.

- `CaretakerRequest`: the user fields + `bio`, `experienceYears`, `companyId` (optional: `null` = independent).
- `GET /api/caretakers?search=&companyId=`: both optional.
- `CaretakerResponse` contains `companyId` and `companyName` (both `null` if independent).
- `companyId` that doesn't exist → 400 `fields.companyId`.

**Done when:** the same checklist as R4 passes for caretakers; a caretaker created with `companyId` shows up in that
company's Prižiūrėtojai tab in the desktop app.

## R7 · Login and services (read-only, for the Android app)

**Login:** `POST /api/auth/login` with `@RequestBody {"username":"...","password":"..."}`
- wrong username or password → 401 with the same message for both (don't reveal which usernames exist)
- blocked → 403; role is not `PET_OWNER` or `CARETAKER` → 403 "Naudokite kompiuterio programą"
- OK → 200 `{"id":5,"role":"PET_OWNER","fullName":"Jonas Jonaitis"}`. The Android app keeps the id and role.

**Services:** owners need to browse them before booking.

| Method | Path | Input |
| --- | --- | --- |
| GET | `/api/services` | `@RequestParam` (all optional): `type`, `species`, `size`, `maxPrice`, `city` (company's city) |
| GET | `/api/services/{id}` | `@PathVariable` |
| GET | `/api/services/{id}/caretakers` | `@PathVariable`: caretakers of that service's company (to choose one when booking) |

- Only `ACTIVE` services of active companies are returned.
- Filtering by species/size uses `JOIN s.allowedSpecies sp WHERE (:species IS NULL OR sp = :species)` plus `SELECT DISTINCT`.

**Done when:** an owner created in R4 can log in; admin gets 403; `?type=GROOMING&maxPrice=30` returns only matching services.

## R8 · Reservations (what each Android user does with them)

The actor is in the URL. The service first checks the reservation belongs to that owner/caretaker, otherwise 404.

**Owner (klientas):**

| Method | Path | Input | OK | Errors |
| --- | --- | --- | --- | --- |
| GET | `/api/owners/{ownerId}/reservations` | `@RequestParam` `status` (optional) | 200 | 404 |
| POST | `/api/owners/{ownerId}/reservations` | `@RequestBody` `{petId, serviceId, caretakerId, start}` | 201 | 400, 404 |
| PATCH | `/api/owners/{ownerId}/reservations/{id}/cancel` | two `@PathVariable`s | 200 | 404, 409 |

**Caretaker (vairuotojas):**

| Method | Path | Input | OK | Errors |
| --- | --- | --- | --- | --- |
| GET | `/api/caretakers/{caretakerId}/reservations` | `@RequestParam` `status`, `from`, `to` (all optional) | 200 | 404 |
| PATCH | `/api/caretakers/{caretakerId}/reservations/{id}/status` | `@RequestParam` `to` (e.g. `?to=CONFIRMED`) | 200 | 404, 409 |

**Both:** `GET /api/reservations/{id}`.

- Create: `end = start + service.durationMinutes`, `price = service.price`, `status = PENDING`. Server-side values are
  never taken from the body (otherwise the app could send its own price).
- Dates in JSON are ISO strings: `"start": "2026-11-25T10:00:00"`. For `from`/`to` query params, add
  `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from`. Without it Spring can't parse `2026-11-25`.
- `ReservationResponse`: `id, status, start, end, price, ownerId, ownerName, petId, petName, serviceId,
  serviceTitle, caretakerId, caretakerName`.

**Done when:** the owner books → the caretaker sees it → CONFIRMED → IN_PROGRESS → COMPLETED, all through HTTP, and
the desktop Rezervacijos tab shows each status after Refresh. PENDING → COMPLETED → 409. Cancelling another owner's reservation → 404.

## R9 · Finish

- `api-demo.http` has one section per grading row, in this order:
  1. health (JSON) + `/` and `/view/services` (HTML)
  2. one request each for `@PathVariable`, `@RequestParam` and `@RequestBody`, with a comment naming which is which
  3. owners CRUD, caretakers CRUD, pets, login, services, reservations, including the error cases
- **Cross-system demo:** create an owner in the API → Refresh in the desktop → edit them in the desktop → `GET` in the API shows the change.
- Send every error case: no stack trace, no `passwordHash` anywhere in any response.
- Short README section: how to start (XAMPP → `schema.sql` → `mvn spring-boot:run`) and the endpoint list.

---

## Rules (same as the desktop app, `BUILD_PLAN.md`)

### Validation (messages in Lithuanian, all errors returned at once in `fields`)

| Resource | Field | Rule | Checked by |
| --- | --- | --- | --- |
| Owner, Caretaker | username | 3–30 chars, letters/digits/_; unique across all users | annotation + service |
| Owner, Caretaker | email | valid format; unique across all users | annotation + service |
| Owner, Caretaker | phone | empty or `+370` + 8 digits | annotation |
| Owner, Caretaker | password | create: at least 8 chars. Update: blank = keep old | service |
| Caretaker | experienceYears | 0–60 | annotation |
| Caretaker | companyId | empty, or an existing company | service |
| Pet | name, species, size | required | annotation |
| Pet | weightKg | > 0 and ≤ 200 | annotation (`@Positive`, `@DecimalMax("200")`) |
| Pet | birthDate | not in the future | annotation (`@PastOrPresent`) |
| Reservation | pet | belongs to the owner in the URL; `service.accepts(pet)` (species + size) | service |
| Reservation | service | `ACTIVE` | service |
| Reservation | caretaker | optional; if given, works for the service's company | service |
| Reservation | start | in the future (`@Future`) | annotation |

### Delete rules

| Delete | Rule |
| --- | --- |
| Owner | has reservations → 409; else delete their pets, then the owner (one `@Transactional` method) |
| Pet | has reservations → 409; else delete |
| Caretaker | has reservations → 409; else delete (the company link is a column on their row, so it goes with them) |

### Reservation status changes in the API

| Who (URL) | From | Allowed to |
| --- | --- | --- |
| Owner | PENDING, CONFIRMED | CANCELLED (the `/cancel` endpoint) |
| Caretaker | PENDING | CONFIRMED, REJECTED |
| Caretaker | CONFIRMED | IN_PROGRESS |
| Caretaker | IN_PROGRESS | COMPLETED |
| – | COMPLETED, CANCELLED, REJECTED | none |

Put the general transition table in the `ReservationStatus` enum (`canTransitionTo`), like the desktop. The "who"
check sits in `ReservationService`.

---

## For the Android app later

- Emulator → `http://10.0.2.2:8080` (that's your PC's `localhost` as seen from the emulator).
  Real phone → your PC's Wi-Fi IP, same network, and allow port 8080 in the Windows firewall.
- Android blocks plain `http://` by default. For development, add `android:usesCleartextTraffic="true"` to the manifest.
- Retrofit + Gson/Moshi can parse the DTOs as they are. Write the Android model classes from the response JSON in `api-demo.http`.

## Common pitfalls

| Symptom | Cause / fix |
| --- | --- |
| Every request redirects to a login page | Spring Security got added. Remove it (R0) |
| `Infinite recursion` / `LazyInitializationException` in a response | An entity was returned. Return a DTO built inside the service |
| `Unknown discriminator value 'ADMIN'` | A `User` subclass is missing. Map all 4 (R2) |
| `Table 'pet_registry.care_service' doesn't exist` | Missing `@Table(name = "services")` on `CareService` |
| Tables changed or emptied after a start | `ddl-auto` isn't `none`. Fix it and reload `schema.sql` |
| Enum column holds `0`, `1`, … / desktop app crashes reading it | Missing `@Enumerated(EnumType.STRING)` |
| `Name for argument of type [int] not specified` | The project doesn't use the Spring Boot parent POM (it compiles with `-parameters`). Keep the generated `pom.xml`, or write `@PathVariable("id")` |
| `Failed to convert value of type 'String' to required type 'LocalDate'` | Add `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` |
| `@Valid` seems ignored | Missing `spring-boot-starter-validation`, or `@Valid` isn't on the `@RequestBody` parameter |
| `Communications link failure` | XAMPP MySQL isn't running, or the URL/port is wrong |
| Times are off by 2–3 hours | Keep `?serverTimezone=Europe/Vilnius` in the URL; use `LocalDateTime` everywhere |
| Tutorial code doesn't compile (`com.fasterxml.jackson.databind…`, `spring-boot-starter-web`) | It's Spring Boot 3. See issue 2 in section 0 |
