# Pet Registry

A desktop app for a pet-care service registry: users (4 roles), companies, services, pets, reservations and reviews.
Course project for *Programavimo technologijos* (VILNIUS TECH).

- **Java 21**, **JavaFX 21** (FXML, made with Scene Builder), **Maven** (wrapper included)
- **LAB1:** class diagram + JavaFX GUI with CRUD for every class (data in memory)
- **LAB2:** the same GUI on a **MySQL / MariaDB** database (XAMPP), plain JDBC with `PreparedStatement`

## Run it

Requirements: **JDK 21 or newer**. Maven does not need to be installed; the project has its own wrapper (`mvnw`).

```
mvnw javafx:run            (Windows: mvnw.cmd javafx:run)
```

Or in IntelliJ IDEA: open the folder as a Maven project and run `com.example.petregistry.Launcher`.

The app starts with **demo data in memory**, so every tab works right away, without a database.

## Connect to the database (LAB2)

1. Start **XAMPP** → MySQL **Start**.
2. Import [`schema.sql`](schema.sql): phpMyAdmin (`http://localhost/phpmyadmin`) → **Import** → choose the file → **Go**.
   Or from the command line: `C:\xampp\mysql\bin\mysql.exe -u root < docs\schema.sql`.
   It creates the database `pet_registry` with all tables **and the same demo data** as the memory mode.
   (Careful: it deletes an existing `pet_registry` database first.)
3. In the app: **Duomenų bazė → Prisijungti…**. The defaults fit XAMPP:
   URL `jdbc:mysql://localhost:3306/pet_registry`, user `root`, empty password.
   The status bar shows "Prisijungta prie DB". **Atsijungti** goes back to memory.

Everything you change while connected is saved in MySQL and is still there after restarting the app.

**Demo logins:** `admin` / `admin123`; all other demo users (`rasa`, `tomas`, `anna`, …) / `Slaptazodis1`.

## Project structure

```
controller (JavaFX)  →  service (rules, validation)  →  repo interface  ←  repo.memory (LAB1)
                                                                       ←  repo.jdbc   (LAB2)  →  db.DatabaseManager
```

| Package | What is in it |
| --- | --- |
| `model` | The classes from the diagram (`lab1-classes.puml`) |
| `repo`, `repo.memory`, `repo.jdbc` | Storage interfaces and their two implementations. `Repositories` decides which one is in use |
| `service` | All rules: validation (all errors at once), delete rules, status transitions, transactions |
| `controller` + `resources/.../view/*.fxml` | Tabs, forms and dialogs |
| `db` | The shared JDBC connection and readable database errors |

Controllers never contain SQL, and the JDBC code never imports JavaFX.

## Documents

| File | Contents |
| --- | --- |
| [`BUILD_PLAN.md`](BUILD_PLAN.md) | Requirements, milestones M1–M13, all rules |
| [`lab1-classes.puml`](lab1-classes.puml) / `.png` | Class diagram |
| [`schema.sql`](schema.sql) | Database schema + demo data |
| [`explanation_plan.md`](explanation_plan.md) | How milestones M9–M13 work, step by step |

## Checks (`../temp_tests`)

Small programs that drive the real app or services from code and print `PASS` / `FAIL`
(e.g. `M9Check` for reservations, `M12Check` for the JDBC repositories, `M13Check` for the GUI on the database).
The database checks use a **separate** database `pet_registry_test`, created from `schema.sql` without the demo block:

```
sed -e '/DEMO DATA START/,/DEMO DATA END/d' -e 's/pet_registry/pet_registry_test/g' docs/schema.sql | C:/xampp/mysql/bin/mysql.exe -u root
```

(Git Bash.) Your `pet_registry` database is never touched by the checks.
