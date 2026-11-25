# Explanation plan: M9 – M13

This file explains the most important parts of every milestone after M8.
For each milestone: **what** was built, **why**, and **how it runs** (which method calls which,
what objects exist, what happens on an error). At the end of each part there is
"How to check it yourself" and a few lab-defense questions about real behaviour.

The architecture stays the same everywhere:

```
controller (JavaFX)  →  service (rules)  →  repository interface  ←  in-memory repo (LAB1)
                                                                  ←  JDBC repo     (LAB2)  →  DatabaseManager  →  MySQL
```

---

## 0. Small changes to older code (and why)

These are not new features. Later milestones needed them.

| Change | Where | Why |
| --- | --- | --- |
| **Link first, then save** | `PetService.create`, `ServiceService.create`, `ReviewService.create` | In LAB2 the `INSERT` needs the parent's id (`owner_id`, `company_id`, `reservation_id`). Before, the code saved first and linked after, so the parent was still `null` during the INSERT. In memory the order didn't matter, so the bug was hidden. |
| `ReservationService.validate` is now `public` | service | The reservation form uses the same "draft" pattern as the other forms (check a copy first). |
| `Rateable.setAverageRating` added to the diagram | `lab1-classes.puml` | The code had it, the diagram didn't. **The PNG must be regenerated** (IntelliJ PlantUML plugin → export), because PlantUML is not installed here. |
| `M5Check` expectation | `temp_tests` | In LAB1 both DB menu items were disabled. Since M11, "Prisijungti…" is enabled. |
| Demo data in `schema.sql` | docs | So the tabs aren't empty after connecting. Same data as `SeedData`; dates use `CURDATE()`, so future bookings stay in the future. |

---

## M9 · Reservations and reviews

### What
- **Rezervacijos tab** (`reservations-tab.fxml`, `ReservationsTabController`): filter (status, owner, date from/to),
  table, buttons Nauja / Redaguoti / Šalinti / **Keisti būseną**.
- **Reservation form** (`reservation-form.fxml`, `ReservationFormController`): owner → pet, service → caretaker,
  date + time, calculated end and price, and a **reviews section** in edit mode.
- **Review form** (`review-form.fxml`, `ReviewFormController`).
- Service layer: `ReservationService.changeStatus(...)`, `ReviewService.allowedTargets(...)`, `ServiceService.findActive()`.

### Dependent choice lists: listeners
A **listener** is a function that JavaFX calls every time a value changes.
It is like `addEventListener("change", ...)` in TypeScript.

```java
ownerBox.valueProperty().addListener((observable, oldOwner, newOwner) -> loadPets(newOwner));
serviceBox.valueProperty().addListener((observable, oldService, newService) -> {
    loadCaretakers(newService);
    updateCalculated();
});
```

How it runs when the user picks "Anna" in the owner box:
1. JavaFX changes `ownerBox.value` → calls our lambda with `newOwner = Anna`.
2. `loadPets(Anna)` → `petService.search(null, null, Anna.getId())` → the repository → only Anna's pets.
3. If the pet that was chosen before is not Anna's, the pet box is cleared.

Why `petService.search(...)` and not `anna.getPets()`? Both work in memory. But the search goes through
the repository, so it works the same way in LAB2 (it becomes a `WHERE owner_id = ?` query).

**Order matters in edit mode.** `fill()` sets the owner FIRST (this fills the pet list), then the pet.
In the other order, the listener would clear the pet you just set.

### Past days disabled: a cell factory
A **cell factory** is a function that creates the small "cell" objects a control draws
(here: one cell per day in the calendar). We create our own cell that turns itself off for past days:

```java
datePicker.setDayCellFactory(picker -> new DateCell() {
    @Override
    public void updateItem(LocalDate date, boolean empty) {
        super.updateItem(date, empty);
        setDisable(empty || date.isBefore(LocalDate.now()));
    }
});
```

The service still checks "start in the future". Today is clickable, but today 08:00 may already be past.
**The UI helps the user, but the service is the real guard.**

### End time and price: calculated, not typed
- On **create**, `ReservationService.create` sets `end = start + duration` and **copies** `service.price`
  into `reservation.price`.
- On **update**, `end` follows the new start, but the price stays the same.

Why copy the price? A reservation is like a receipt. If the company raises the price next month,
an old booking must still show what the client agreed to pay. `M9Check` tests exactly this:
it changes the service price to 99.00 and the booking still says 15.00.

In edit mode the **owner and service are locked**. If you could change the service, the
copied price would no longer match it. To change the service: cancel and book again.
A reservation that is already IN_PROGRESS / COMPLETED / … is fully locked: it is history.

### Keisti būseną: the transition table
The rule table ("PENDING → CONFIRMED, REJECTED, CANCELLED" …) lives in ONE place:
`ReservationStatus.nextStatuses()`. Everyone uses it:

```
Tab button  → reservation.getStatus().nextStatuses()  → the dialog offers only these
Dialog "Gerai" → reservationService.changeStatus(r, target)
               → r.confirm() / r.start() / ...       (the model checks canMoveTo again)
               → Repositories.reservations().update(r)
```

So the check happens twice: the UI shows only legal moves, and the model still refuses an illegal one
(`IllegalStateException`, which the service turns into `BusinessRuleException` for the UI).
Why twice? Because the service can be called without the UI (tests, a future REST API).
`changeStatus` does **not** call `validate()`: an IN_PROGRESS visit has a start in the past,
and "start must be in the future" would wrongly block it.

`Dialogs.choose(...)` is a new reusable helper (a `ChoiceDialog` with Lithuanian buttons "Gerai" / "Atšaukti").

### Reviews
Rules (in `ReviewService.validate`):
- the reservation is COMPLETED;
- the author is the owner or the caretaker of THIS reservation;
- one review per author per reservation;
- rating 1–5;
- **target:** owner → the caretaker or the company; caretaker → the owner (`allowedTargets`);
- a caretaker must also rate the pet's behaviour.

`allowedTargets(author, reservation)` is used by **both** the form (to fill the target box) and `validate`.
One rule, one place: the form can't offer a target that the service would refuse.

The averages: after every create / delete / deactivate, `refreshAverage(target)` recalculates the
average from the target's **active** reviews. Deleting a reservation deletes its reviews and then
refreshes the affected averages.

### How to check it yourself
- Run the app → Rezervacijos → Nauja. Pick an owner and watch the pet list change. Pick a service:
  the caretaker list and the price change. Pick a date and time: the end time appears.
- Select the new row → Keisti būseną three times → Įvykdyta. Press it again: "galutinės būsenos".
- Redaguoti → Pridėti atsiliepimą twice with the same author: the second one is refused.
- Automatic: `temp_tests/M9Check` (83 checks).

### Questions a lecturer may ask
1. *A PENDING reservation for tomorrow 10:00 is edited to 11:00 after the service price changed from 15 to 99.
   What are `end` and `price` after saving?* (end = 12:00 for a 60-min service, price stays 15.)
2. *Where is the transition table, and what stops `PENDING → COMPLETED` if someone calls the service directly?*

---

## M10 · LAB1 finish: no stack traces, only alerts

### What
1. A **safety net** in `MainApp.start()`:
   ```java
   Thread.currentThread().setUncaughtExceptionHandler((thread, error) ->
           Dialogs.error(stage, "Netikėta klaida", messageOf(error)));
   ```
2. A check, `M10Check`, that presses every button with nothing selected, saves every form empty, and
   presses the buttons inside edit forms ("Atsieti", "Pašalinti gyvūną"…) with nothing selected.

### Why
Every button handler runs on the **JavaFX thread** (the one thread that draws the window and handles clicks).
If a handler throws and nobody catches it, JavaFX prints a stack trace and the click "does nothing".
In LAB1 our code catches every expected error (`ValidationException`, `BusinessRuleException`).
But in LAB2 new, unexpected errors can happen, e.g. MySQL is stopped in XAMPP while the app is open.
The safety net turns any such error into an alert.

### How `messageOf` works
JavaFX wraps errors from FXML handlers in other exceptions (`InvocationTargetException`…).
`messageOf` walks the **cause chain** (`e.getCause().getCause()...`, like Go's `errors.Unwrap`).
The first exception from **our** package wins, because ours already have Lithuanian messages.
Otherwise the deepest message is used.

### How to check it yourself
- `temp_tests/M10Check` (memory) and `M10Check db` (connected): 49 / 50 checks, "no uncaught errors".

---

## M11 · Database and connect / disconnect

### What
- `db.DatabaseManager`: ONE shared `Connection` with `connect`, `disconnect`, `isConnected`, `getConnection`, `inTransaction`.
- `db.DataAccessException`: an unchecked exception with a readable Lithuanian message.
- `controller.ConnectDialog`: URL / user / password dialog (XAMPP defaults).
- Menu "Duomenų bazė", status bar, `MainApp.stop()` disconnects.

### Terms
- **JDBC** = Java's standard API for SQL databases. Like Go's `database/sql`: one API, a **driver** per database.
  Our driver is `mysql-connector-j` (it also works with XAMPP's MariaDB).
- **Connection** = an open session with the database server (network + login). Opening one is slow,
  so we open it once and reuse it.
- **Unchecked exception** = extends `RuntimeException`, so you don't have to write `throws` (Java's checked
  exceptions are like Go's returned `error` that you must handle; unchecked ones are like a panic you may catch).

### How "Prisijungti…" runs
```
MainController.onConnect()
  └ ConnectDialog.show(window)
      └ user presses "Prisijungti"
          └ event FILTER → DatabaseManager.connect(url, user, password)
               ├ OK   → dialog closes, returns the URL
               └ fail → DataAccessException → error alert → event.consume() → dialog STAYS open
  └ Repositories.useDatabase()        (the 6 repositories are now JDBC ones)
  └ refreshAllTabs()                   (every tab reloads from MySQL)
  └ status "Prisijungta prie DB: …", Prisijungti… off, Atsijungti on
```
An **event filter** runs *before* the button's normal action (which closes the dialog).
`event.consume()` means "stop here", so a wrong password doesn't close the dialog.
The user fixes the password and tries again.

If the tables don't exist (`schema.sql` was not loaded), the first query in `refreshAllTabs()` fails →
we go back to memory and show an error. The app never stays half-connected.

**Atsijungti:** `disconnect()` → `Repositories.useMemory()` (fresh seed data) → refresh all tabs → status
"Duomenys atmintyje".

### Why the services didn't change for the switch
Services call `Repositories.pets()` **on every call**. They never keep a repository in a field.
So after `useDatabase()` the very next call already goes to MySQL. This is the payoff of the
"repository interface" design from M3: the switch is 6 lines in one class.

### How to check it yourself
- XAMPP → MySQL Start. Load `schema.sql` (phpMyAdmin → Import, or
  `C:\xampp\mysql\bin\mysql.exe -u root < docs\schema.sql`). **Done already** on your PC: `pet_registry` exists
  with the demo data.
- Run the app → Duomenų bazė → Prisijungti… → type a wrong password: an error, the dialog stays open.
  Clear the password (XAMPP root has none) → the status bar changes, the Users tab shows the 9 users from the database.
- Automatic: `M12Check` (first part) and `M13Check`.

---

## M12 · JDBC repositories

### What
`repo/jdbc/`: `JdbcUserRepository`, `JdbcCompanyRepository`, `JdbcServiceRepository`, `JdbcPetRepository`,
`JdbcReservationRepository`, `JdbcReviewRepository`, and the helper class `Sql`.
They implement **the same interfaces** as the in-memory ones (M3).

### `Sql`: the JDBC code, written once
Every repository needs the same steps: prepare → put values into `?` → run → read rows → close.
`Sql.list / one / count / execute / insert` do this, so a repository method is short:

```java
public List<Pet> search(String name, Species species, Integer ownerId) {
    StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
    List<Object> params = new ArrayList<>();
    if (!Sql.blank(name))   { sql.append(" AND p.name LIKE ?");   params.add(Sql.contains(name)); }
    if (species != null)    { sql.append(" AND p.species = ?");   params.add(species); }
    if (ownerId != null)    { sql.append(" AND p.owner_id = ?");  params.add(ownerId); }
    return Sql.list(sql.toString(), JdbcPetRepository::mapFull, params.toArray());
}
```

- **`PreparedStatement`**: the SQL text has `?`, the values are sent separately. So `' OR 1=1 --` typed in
  a search box is just text, never SQL (**SQL injection** is impossible). `M12Check` tests this.
- **`WHERE 1 = 1`**: a trick, so every filter can simply add `AND ...`.
- **The filtering happens in SQL**, not in Java. The DB sends only matching rows.
- `Sql.contains("a_b")` escapes `%` and `_`, so the user's `_` is not a LIKE wildcard.
- **try-with-resources** (`try (PreparedStatement ps = ...)`) closes the statement and the result set,
  like Go's `defer rows.Close()`. It is **never** used on the shared connection: closing it would
  log the whole app out of the database.
- `Sql.insert` uses `RETURN_GENERATED_KEYS` to get the id that `AUTO_INCREMENT` gave the new row,
  and the repository writes it into the object (`pet.setId(id)`).
- `Sql.bind` picks the right `setXxx` for each value. Enums are saved by `name()` (`'DOG'`), dates with
  `Date.valueOf` / `Timestamp.valueOf`. `null` becomes `setNull`.

### Java ↔ table mapping
| Java | Database | Where |
| --- | --- | --- |
| `User` + 4 subclasses | one `users` table. `role` picks the class in `mapRow` (`switch (role) → new Caretaker()`) | `JdbcUserRepository.mapRow` |
| `Service.allowedSpecies` / `allowedSizes` (a `Set`) | `service_species` / `service_sizes`, one row per value. Save = delete old rows + insert new ones | `JdbcServiceRepository` |
| `Reservation.start` / `end` | `start_at` / `end_at` (`start`, `end` are SQL keywords) | `JdbcReservationRepository` |
| `Review.target` (`Rateable`) | `target_company_id` if it is a `Company`, else `target_user_id` | `JdbcReviewRepository` |
| `averageRating` | **not a column**: a sub-query `(SELECT AVG(rating) FROM reviews WHERE ... AND active)` in every SELECT | user + company repo |
| nullable INT | `getInt()` returns 0 for NULL → `wasNull()` tells them apart (`Sql.nullableInt`) | `Sql` |

### The hardest part: lists and links ("plain" vs "full")
A table row has **ids** (`owner_id`), but our objects hold **objects** (`pet.getOwner()`) and **lists**
(`owner.getPets()`). After reading a row we must load the linked objects with more queries.

The danger is **loading in circles**: a caretaker → their company → the company's caretakers → their
company → … forever. The solution has two kinds of "row → object" methods:

- **`mapRow` (plain):** only the row's own columns. No links. Used when the object is a *part* of something else.
- **`mapFull`:** `mapRow` + links, loaded **plain**. Used by the public methods (`findAll`, `findById`, `search`).

| Loading (full) … | … also loads (plain) | linked with |
| --- | --- | --- |
| a pet owner | their pets | `owner.addPet(pet)` |
| a caretaker | their company | `company.hireCaretaker(c)` |
| a manager | their company | `setCompany` |
| a company | its services + its caretakers | `addService`, `hireCaretaker` |
| a service | its company | `company.addService(s)` |
| a pet | its owner | `owner.addPet(pet)` |
| a reservation | owner, pet, service, caretaker (full), reviews | `addReview` |
| a review | author, target | setters |

Because plain objects never load links, the chain always stops after one step.
We use the model's **add methods**, not setters: `addPet` sets **both** sides (`owner.pets` and `pet.owner`).
That's also the only way, because `Pet.setOwner` is package-private on purpose.

**Trade-off (be honest at the defense):** this is the **"N+1 queries" problem**. One reservation row
causes about 10 more small queries. For a lab (tens of rows on localhost) it is fast enough and much easier
to read. A real app would use `JOIN`s or load all owners at once.
A related detail: a mapper runs more queries *while* the outer result is being read. That's safe with
MySQL Connector/J, because it reads the whole result into memory first (the default).

### New objects on every read
In memory, `findAll()` always returned **the same** objects. In JDBC every read creates **new** objects.
Two objects for the same pet are `equals()` (same class and id: that's why `BaseEntity.equals` uses the id),
but they are not `==`.

This caused a **real bug we found in testing** (fixed in `ServiceFormController` and `PetFormController`).
`ComboBox.setValue(company)` looks for an item that `equals(company)` and then **replaces the value with
that list item**: a different copy of the company. The service form then linked the new service to the copy,
so the company window's own object never got it, and its service list stayed empty in DB mode.
Fix: when the parent is fixed, the form remembers the exact object it was given and uses it on save.
Lesson: with a database, `equals` (same id) and `==` (same object) are not the same thing any more.

### Readable errors
`DataAccessException.of(SQLException)` uses the MySQL error code:
1062 duplicate value → "Toks įrašas jau yra…"; 1451 delete blocked by a foreign key → "Įrašo pašalinti negalima…";
SQLState `08…` → "Ryšys su duomenų baze nutrūko". The services check most rules **before** the database does
(e.g. unique username), so these are a second line of defence.

### How to check it yourself
- `temp_tests/M12Check` (80 checks) runs on a **separate** database `pet_registry_test`
  (a copy of `schema.sql`), so your real `pet_registry` is never touched. It creates, reads, updates,
  searches and deletes one object of each class.
- In phpMyAdmin, look at `service_species` after saving a service with 2 species: 2 rows.

### Questions a lecturer may ask
1. *You load a caretaker. How many SQL queries run, and why doesn't it loop forever?*
   (2: the user row, then the company plain. A plain company doesn't load its caretakers.)
2. *A user types `%` into the pet name filter. What SQL runs, and what is found?*

---

## M13 · Switch, transactions, LAB2 finish

### Transactions
A **transaction** = several SQL changes that are saved **all together or not at all**.
Like a bank transfer: "take 10 € from A" and "add 10 € to B" must not happen half.

```java
public static void inTransaction(Runnable work) {
    Connection c = getConnection();
    if (!c.getAutoCommit()) { work.run(); return; }  // already inside one: join it
    c.setAutoCommit(false);                           // start: don't save each statement
    try {
        work.run();
        c.commit();                                   // save everything
    } catch (RuntimeException e) {
        c.rollback();                                 // undo everything
        throw e;
    } finally {
        c.setAutoCommit(true);                        // back to normal, always
    }
}
```
(`SQLException` handling is left out here; see `DatabaseManager`.)

**autoCommit** = JDBC's default: every statement is saved at once. We turn it off for the transaction.
`finally` runs whether the work worked or failed, so the connection always goes back to normal.

Where transactions are used:
| Multi-step change | Code |
| --- | --- |
| save a service + its species/sizes rows | `JdbcServiceRepository.create / update` |
| delete an owner with their pets | `UserService.deleteOwner` |
| delete a company: services + release caretakers + company | `CompanyService.delete` |
| delete a reservation with its reviews | `ReservationService.delete` |

**Services must not use JDBC** (that's the architecture rule). So they call
`Repositories.inTransaction(() -> { ... })`: in DB mode that is `DatabaseManager.inTransaction`,
in memory mode it just runs the work (a map has nothing to roll back).

**Nesting:** `CompanyService.delete` (a transaction) could call a repository method that also asks for one.
If autoCommit is already off, the inner call simply **joins** the outer transaction, so only the outer one
commits. That's why the first `if` is there.

`M12Check` proves the rollback twice: (1) deleting a pet then throwing an error → the pet is still there;
(2) deleting a review + a reservation + an owner who still has pets → the third step fails on a foreign key,
and the first two deletes are undone.

**Limit (honest):** a rollback undoes the **database**, not the Java objects in memory
(`owner.removePet(pet)` already ran). That's fine because every tab calls `refresh()` after a
save or delete and reloads from the database.

### refresh() after every change
In DB mode the table holds copies. After a save, the table must reload to show what the DB really has.
All tabs already do `refresh()` after save / delete / status change, and `select(saved)` works because
`TableView` finds the row with `equals` (same id).

### How to check it yourself
- `temp_tests/M13Check first` drives the real GUI against `pet_registry_test`: connect (wrong password, then right),
  create / edit / filter / delete in every tab, a reservation through all statuses with a review, delete rules,
  then Atsijungti.
- `temp_tests/M13Check second` is a **new JVM** (= the app restarted): it connects and finds all the data,
  then deletes a reservation with its review, an owner with her pet, and a company with its service.
- By hand: create a company while connected, close the app, start it, connect: it's still there.

### Questions a lecturer may ask
1. *An owner has 2 pets. During `deleteOwner`, the second `DELETE FROM pets` fails. What is in the database afterwards, and what does the user see?*
   (Both pets and the owner are still there, the rollback undid the first delete; the user sees an alert.)
2. *Why does `inTransaction` check `getAutoCommit()` first?*

---

## Test results (all run on 2026-11-11 against your XAMPP MariaDB 10.11)

| Check | Result |
| --- | --- |
| M3/M4/M5/M6/M7 + search checks (older ones, regression) | all pass |
| `M9Check` | 83 / 83 |
| `M10Check` (memory) / `M10Check db` | 49 / 49, 50 / 50, no uncaught errors |
| `M12Check` (JDBC console) | 80 / 80 |
| `M13Check first` / `second` (GUI on DB, then after a restart) | 57 / 57, 14 / 14 |
| module-path start (as IntelliJ / `javafx:run` runs it): MySQL driver found | OK |

How to run a check (from the project folder; `cp.txt` = the Maven dependency classpath):
```
mvnw -o compile
mvnw -o dependency:build-classpath -Dmdep.outputFile=cp.txt
javac -d target/temp_tests -cp "target/classes;<contents of cp.txt>" temp_tests/M12Check.java
java  -cp "target/classes;<contents of cp.txt>;target/temp_tests" M12Check
```
Before the DB checks, recreate the test database (it is separate from `pet_registry`). The checks expect it
empty, so the demo block of `schema.sql` is cut out (Git Bash):
`sed -e '/DEMO DATA START/,/DEMO DATA END/d' -e 's/pet_registry/pet_registry_test/g' docs/schema.sql | C:/xampp/mysql/bin/mysql.exe -u root`

`temp_tests/DemoDataCheck` checks the opposite: that the demo block shows exactly the same data as the
in-memory seed (load the test database WITH the demo block for it, or pass `pet_registry` as the argument).
