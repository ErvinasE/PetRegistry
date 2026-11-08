# Pet Registry — Build Plan (LAB1 + LAB2)

I am building this app myself to learn. Work through the milestones in order, one at a time.
A milestone is done only when its **Done when** list passes.

## Project facts

- Java 21, JavaFX 21 (FXML + Scene Builder), Maven, `module-info.java`
- Root package: `com.example.petregistry`
- FXML files: `src/main/resources/com/example/petregistry/view/`
- Class diagram: `docs/lab1-classes.puml`. The model code must match it.
- Database (LAB2): MySQL / XAMPP, plain JDBC with `PreparedStatement`, schema in `docs/schema.sql`

## Grading

| Lab | Requirement | Points |
| --- | --- | --- |
| LAB1 | Class diagram (OOP, composition/aggregation) | 2 + 1 |
| LAB1 | Main window + pop-up (alert / file chooser) + extra window | 1 |
| LAB1 | Menu or tabs | 1 |
| LAB1 | Standard controls (fields, buttons) | 1 |
| LAB1 | Complex controls (table, choice list filled from data) | 1 |
| LAB1 | Input validation + error messages | 1 |
| LAB1 | CRUD in the GUI for every class | 2 |
| LAB2 | Connect / disconnect from the DB | 1.25 |
| LAB2 | Read from DB: all and filtered | 1.25 |
| LAB2 | Insert / delete / update in DB | 2.5 each |

"C" includes adding a part to a whole (a pet to an owner). "D" includes removing a part (a service from a company).

## Architecture

```
controller (JavaFX)  →  service (rules, validation)  →  repo interface  ←  repo.memory (LAB1) / repo.jdbc (LAB2)
                                  model classes are passed between all layers
```

- Controllers never touch storage or SQL. Services hold all rules, so they work unchanged in LAB2.
- `Repositories` (one class with static getters) decides which storage is in use.
  `useMemory()` in LAB1; `useDatabase()` after connecting in LAB2.

Packages: `model`, `repo`, `repo.memory`, `repo.jdbc`, `service`, `controller`, `util`, `db`.

---

## LAB1

### M1 · Project setup
- `pom.xml`: javafx-controls, javafx-fxml, jbcrypt, mysql-connector-j; compiler `release` 21.
- `module-info.java`: `requires javafx.controls, javafx.fxml, java.sql, jbcrypt`;
  `opens ...controller to javafx.fxml`; `opens ...model to javafx.base`; `exports com.example.petregistry`.
- `MainApp` opens an empty window titled "Pet Registry".

**Done when:** the app starts with no errors.

### M2 · Model classes
Write every class and enum from `docs/lab1-classes.puml`. Details the diagram doesn't show:
- `BaseEntity`: `id` (0 = not saved). `equals()`/`hashCode()` by id, so objects reloaded from the DB still match.
- Enums: a Lithuanian label in a constructor field, returned by `toString()` (shown in ComboBoxes).
- `toString()` on every class returns its name (ComboBoxes display it).
- Composition methods keep both sides in sync: `owner.addPet(pet)` also sets `pet.owner`.
  `getPets()` returns an unmodifiable list.
- `Reservation` status methods (`confirm()` etc.) throw `IllegalStateException` on a wrong transition (see Rules).
- `Rateable.getAverageRating()` returns a plain field (with a setter). It is **not** a database column.
  LAB1: `ReviewService.refreshAverage(target)` recalculates it from the target's active reviews. Call it after every
  review create / remove / deactivate, and after deleting a reservation that has reviews. LAB2: the JDBC repositories
  fill it with a SQL `AVG` over active reviews when they read a user or company. Set it nowhere else.
- Money is `BigDecimal`, dates are `java.time`.

**Done when:** everything compiles; a throwaway `main` adds 2 pets to an owner and prints them.

### M3 · Storage in memory
- `Repository<T extends BaseEntity>`: `findAll()`, `findById(int)`, `create(T)`, `update(T)`, `delete(int)`.
- `InMemoryRepository<T>`: one generic class with a `LinkedHashMap<Integer, T>` and an id counter. Works for every class.
- Add a `search(...)` method only where a tab has a filter (a `null` argument means "don't filter"):

| Repository | Extra method |
| --- | --- |
| Pet | `search(String name, Species species, Integer ownerId)` |
| User | `search(String text, Role role)` |
| Company | `search(String name, String city)` |
| Service | `search(ServiceType type, Integer companyId, BigDecimal minPrice, BigDecimal maxPrice)` |
| Reservation | `search(ReservationStatus status, Integer ownerId, LocalDate from, LocalDate to)` |
| Review | `findByReservation(int reservationId)`, `findByTarget(Rateable target)` |

- The M4 rules need lookups too, even where no tab has a filter. Without them a rule can't ask
  "does this pet have reservations?". In LAB2 these must be `COUNT(*)` / `WHERE` queries, not "load everything and loop":

| Repository | Extra method | Used by |
| --- | --- | --- |
| Reservation | `countByPet(int)`, `countByOwner(int)`, `countByCaretaker(int)`, `countByService(int)` | delete rules |
| User | `countManagersOf(int companyId)` | company delete rule |
| User | `findByUsername(String)`, `findByEmail(String)` | unique checks |
| User | `findOwners()`, `findCaretakers(Integer companyId)` (`null` = all) | choice lists (M6, M8, M9) |
| Company | `findByCode(String)` | unique company code |

- `Repositories.useMemory()` creates the repositories and adds seed data: 2 companies, 1 manager each,
  3 caretakers, 3 owners, 5 pets, 5 services, 5 reservations in different statuses, 2 reviews.

**Done when:** a console `main` prints the size of every `findAll()`, one `search` result and one `countBy...` result.

### M4 · Services (rules and validation)
- One service per class: `UserService`, `CompanyService`, `ServiceService` (for `Service`), `PetService`,
  `ReservationService`, `ReviewService`. Each has `findAll`, `search`, `create`, `update`, `delete`.
- `create`/`update` call `validate()`, which collects **all** errors into a
  `ValidationException` (a `Map<String, String>`: field name → message). Rules are in the Rules section.
- `delete` applies the delete rules and throws `BusinessRuleException` with a readable message.
- Passwords: store `BCrypt.hashpw(raw, BCrypt.gensalt())`. A blank password on edit keeps the old hash.

**Done when:** a console `main` triggers each delete rule and one validation error, and prints the messages.

### M5 · Main window
`main.fxml` (Scene Builder): `BorderPane`
- top: `MenuBar`: Failas (Išeiti), Duomenų bazė (Prisijungti…, Atsijungti; disabled until LAB2), Pagalba (Apie)
- center: `TabPane` with 5 tabs: Vartotojai, Įmonės, Paslaugos, Gyvūnai, Rezervacijos.
  Each tab is its own FXML + controller via `<fx:include fx:id="petsTab" source="pets-tab.fxml"/>`.
  In `MainController`, the injected field must be named `petsTabController` (fx:id + "Controller").
- bottom: status `Label` ("Duomenys atmintyje")
- Apie → information `Alert`; Išeiti → confirmation `Alert`.

**Done when:** all 5 tabs load, and both alerts work.

### M6 · Pets screen (the template for every other screen)
**Tab** (`pets-tab.fxml` + `PetsTabController`):
- filter bar: name `TextField`, species `ComboBox`, owner `ComboBox` (filled from `userService` data), Ieškoti, Rodyti visus
- `TableView` with columns for the pet fields + owner name (nested value → lambda cell factory)
- buttons: Naujas, Redaguoti (also double-click a row), Šalinti (confirmation alert)
- public `refresh()` reloads the table and the owner list

**Form** (`pet-form.fxml` + `PetFormController`), opened as a modal `Stage` with `showAndWait()`:
- name, species, size, birth date (`DatePicker`), weight, owner, notes (`TextArea`), photo (`FileChooser` + `ImageView` preview)
- Save: parse numbers (catch `NumberFormatException`), call the service, show all `ValidationException`
  messages in one error alert, and mark the bad fields red (CSS class `error`, use `-fx-effect` so the layout doesn't move).

**Done when:** create / edit / delete / filter work; empty or bad input shows errors; deleting a pet
with reservations shows the rule's message.

### M7 · Users screen
- One form for all 4 roles. The role `ComboBox` is enabled only when creating (an object can't change its class).
- Each role's extra fields sit in their own `VBox`; show the right one with `setVisible()` + `setManaged()`.
- Owner (edit mode): `ListView` of their pets with Add (opens the M6 form with the owner fixed) and Remove.
- Tab button "Blokuoti / Atblokuoti" toggles `blocked`.

**Done when:** one user of each role can be created; a manager without a company is refused.

### M8 · Companies and Services screens
- Company form = window with inner tabs: Duomenys; Paslaugos (list + add/remove services); Prižiūrėtojai
  (assigned list + ComboBox of unassigned caretakers + Priskirti / Atsieti). Inner tabs are disabled until the company is saved.
- Service form: company, title, description, type, price, duration (`Spinner`, step 15), location, status (edit only),
  species and size `CheckBox`es generated in code from `Species.values()` / `PetSize.values()`.
- Service tab button "Laikinai neteikiama" toggles `ACTIVE` ↔ `TEMPORARILY_UNAVAILABLE`.

**Done when:** services can be added inside a company; releasing a caretaker keeps them in Users;
deleting a booked service turns it `INACTIVE` with an info alert.

### M9 · Reservations and Reviews
- Form: owner → pet `ComboBox` shows only that owner's pets (listener on `ownerBox.valueProperty()`);
  service (ACTIVE only) → caretaker `ComboBox` shows that company's caretakers; date (`DatePicker`, past days disabled)
    + time (`ComboBox<LocalTime>`, 08:00–20:00 every 30 min); end time and price shown as labels.
  On create, `ReservationService` sets `end = start + service.durationMinutes` and copies `service.price` into `reservation.price`,
  so a later price change doesn't rewrite old bookings.
- Tab button "Keisti būseną" offers only the transitions allowed from the current status.
- Reviews section inside the reservation window (edit mode): list + Add (enabled only when `COMPLETED`) + Remove.
  Author = owner or caretaker. Owner picks the target (the caretaker or the company); caretaker's target is the owner,
  and they also give `petBehaviorRating`.

**Done when:** a reservation can go PENDING → CONFIRMED → IN_PROGRESS → COMPLETED and get a review;
an illegal transition and a second review by the same author are refused.

### M10 · LAB1 finish
- Press every button with nothing selected and save every form empty: no stack traces, only alerts.
- Diagram PNG matches the code.

---

## LAB2

### M11 · Database and connect/disconnect
- Load `docs/schema.sql` into MySQL / XAMPP.
- `db.DatabaseManager`: one shared `Connection`; `connect(url, user, password)`, `disconnect()`, `isConnected()`, `getConnection()`.
- Menu Prisijungti… → `Dialog` with URL / user / password → connect → `Repositories.useDatabase()` → refresh all tabs
  → status bar + menu items change. Atsijungti → disconnect → `useMemory()` → refresh. `MainApp.stop()` disconnects.

**Done when:** a wrong password shows an error; connect and disconnect both change the status bar.

### M12 · JDBC repositories
One `Jdbc...Repository` per class, implementing the same interfaces as M3. Order: User, Company, Service, Pet, Reservation, Review.
- Use `PreparedStatement` for everything. try-with-resources on statements and result sets only, **never** on the shared connection.
- `search(...)` builds the `WHERE` from non-null arguments. The filtering must happen in SQL.
- `create` uses `RETURN_GENERATED_KEYS` and sets the id on the object.
- The lists inside objects are not columns, so `mapRow` leaves them empty. Fill them after reading, with one extra query each:
  `PetOwner.pets` (`pets` by `owner_id`), `Company.services` (`services` by `company_id`),
  `Company.caretakers` (`users` with this `company_id` and role `CARETAKER`), `Reservation.reviews` (`reviews` by `reservation_id`).
  Use the add methods (`addPet`, `addService`, `hireCaretaker`, `addReview`) so both sides stay in sync.
  Without this, the lists in M7, M8 and M9 look empty in LAB2.
- Catch `SQLIntegrityConstraintViolationException` (duplicate value, or delete blocked by a foreign key) and rethrow with a readable message.

Java ↔ table mapping that isn't obvious:

| Java | Database |
| --- | --- |
| `User` + 4 subclasses | one `users` table; the `role` column picks the subclass in `mapRow` |
| `Service.allowedSpecies` / `allowedSizes` | `service_species` / `service_sizes` (delete + reinsert on save) |
| `Reservation.start` / `end` | `start_at` / `end_at` (`start` and `end` are SQL keywords) |
| `Review.target` | `target_company_id` if it's a `Company`, else `target_user_id` |
| enums | `setString(e.name())` / `Enum.valueOf(rs.getString(...))` |
| `LocalDate` / `LocalDateTime` | `Date.valueOf` / `Timestamp.valueOf`, and back with `toLocalDate()` / `toLocalDateTime()` |

**Done when:** a console `main` creates, reads, updates, searches and deletes one object of each class in the DB.

### M13 · Switch, transactions, LAB2 finish
- Services fetch repositories through `Repositories` on every call, so they follow the switch.
- Multi-step changes run in one transaction (`setAutoCommit(false)` → `commit()` / `rollback()` →
  `setAutoCommit(true)` in `finally`): saving a service with its species/sizes, deleting an owner with pets,
  deleting a company with services, deleting a reservation with reviews.
- Always call `refresh()` after a save or delete (the table now holds copies, not the same objects).

**Done when:** every tab does create / edit / delete / filter against the DB, and the data is still there after restarting the app.

---

## Rules

### Validation (messages in Lithuanian, all errors shown at once)

| Class | Field | Rule |
| --- | --- | --- |
| all | required fields | not empty after `trim()` |
| User | username | 3–30 chars, letters/digits/_; unique |
| User | email | valid format; unique |
| User | phone | empty or `+370` + 8 digits |
| User | password | create: at least 8 chars, both password fields equal |
| User | company | required for a manager |
| Company | companyCode | exactly 9 digits; unique |
| Service | price | a number, 0–10 000 |
| Service | durationMinutes | 15–1440, multiple of 15 |
| Service | allowedSpecies / allowedSizes | at least one of each |
| Pet | weightKg | a number, > 0 and ≤ 200 |
| Pet | birthDate | not in the future |
| Reservation | pet | belongs to the owner; `service.accepts(pet)` |
| Reservation | service | `ACTIVE` |
| Reservation | caretaker | works for the service's company |
| Reservation | start | in the future |
| Review | – | reservation `COMPLETED`; author is owner or caretaker; one review per author; rating 1–5 |

### Delete rules

| Delete | Rule |
| --- | --- |
| Pet owner | has reservations → refuse; else delete their pets, then the owner |
| Pet | has reservations → refuse; else `owner.removePet(pet)`, then delete |
| Caretaker | has reservations → refuse; else release from company, then delete |
| Company | has managers or booked services → refuse; else delete services, release caretakers, delete company |
| Service | has reservations → `deactivate()` instead; else `company.removeService()`, then delete |
| Reservation | delete its reviews, then the reservation |

### Reservation status transitions

| From | Allowed to |
| --- | --- |
| PENDING | CONFIRMED, REJECTED, CANCELLED |
| CONFIRMED | IN_PROGRESS, CANCELLED |
| IN_PROGRESS | COMPLETED |
| COMPLETED, CANCELLED, REJECTED | none |