-- ============================================================
--  Pet Registry – LAB2 database schema (matches docs/lab1-classes.puml)
--  MySQL 8+ or MariaDB 10.4+ (XAMPP)
--  Run: mysql -u root < schema.sql   (or phpMyAdmin → Import)
--  It DELETES an existing pet_registry database first, then creates the tables and demo data.
--  Column names = Java field names in snake_case.
-- ============================================================
DROP DATABASE IF EXISTS pet_registry;
CREATE DATABASE pet_registry CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pet_registry;

CREATE TABLE companies (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    company_code  CHAR(9)      NOT NULL UNIQUE,
    city          VARCHAR(80)  NOT NULL,
    address       VARCHAR(200),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- User + 4 subclasses in one table; `role` says which Java class to create.
CREATE TABLE users (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    role             ENUM('ADMIN','COMPANY_MANAGER','CARETAKER','PET_OWNER') NOT NULL,
    username         VARCHAR(30)  NOT NULL UNIQUE,
    password_hash    CHAR(60)     NOT NULL,
    email            VARCHAR(120) NOT NULL UNIQUE,
    first_name       VARCHAR(60)  NOT NULL,
    last_name        VARCHAR(60)  NOT NULL,
    phone            VARCHAR(20),
    blocked          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    address          VARCHAR(200),          -- PetOwner
    bio              TEXT,                  -- Caretaker
    experience_years INT,                   -- Caretaker
    company_id       INT NULL,              -- Caretaker (optional), CompanyManager (required)
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;

CREATE TABLE services (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    company_id       INT NOT NULL,
    title            VARCHAR(120) NOT NULL,
    description      TEXT,
    type             ENUM('VETERINARY','GROOMING','PET_SITTING','DOG_WALKING','BOARDING') NOT NULL,
    price            DECIMAL(10,2) NOT NULL,
    duration_minutes INT NOT NULL,
    location         ENUM('PROVIDER_PREMISES','CLIENT_HOME','OUTDOORS') NOT NULL,
    status           ENUM('ACTIVE','TEMPORARILY_UNAVAILABLE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_services_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;

-- Service.allowedSpecies and Service.allowedSizes (one row per value)
CREATE TABLE service_species (
    service_id INT NOT NULL,
    species    ENUM('DOG','CAT','BIRD','RODENT','OTHER') NOT NULL,
    PRIMARY KEY (service_id, species),
    CONSTRAINT fk_ss_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE service_sizes (
    service_id INT NOT NULL,
    size       ENUM('SMALL','MEDIUM','LARGE') NOT NULL,
    PRIMARY KEY (service_id, size),
    CONSTRAINT fk_sz_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE pets (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    owner_id    INT NOT NULL,
    name        VARCHAR(60) NOT NULL,
    species     ENUM('DOG','CAT','BIRD','RODENT','OTHER') NOT NULL,
    size        ENUM('SMALL','MEDIUM','LARGE') NOT NULL,
    birth_date  DATE,
    weight_kg   DECIMAL(5,2) NOT NULL,
    notes       TEXT,
    photo_path  VARCHAR(255),
    CONSTRAINT fk_pets_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- `start`/`end` are SQL keywords, so the columns are start_at / end_at.
CREATE TABLE reservations (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    owner_id     INT NOT NULL,
    pet_id       INT NOT NULL,
    service_id   INT NOT NULL,
    caretaker_id INT NULL,
    start_at     DATETIME NOT NULL,
    end_at       DATETIME NOT NULL,
    status       ENUM('PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','REJECTED') NOT NULL DEFAULT 'PENDING',
    price        DECIMAL(10,2) NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_res_owner     FOREIGN KEY (owner_id)     REFERENCES users(id),
    CONSTRAINT fk_res_pet       FOREIGN KEY (pet_id)       REFERENCES pets(id),
    CONSTRAINT fk_res_service   FOREIGN KEY (service_id)   REFERENCES services(id),
    CONSTRAINT fk_res_caretaker FOREIGN KEY (caretaker_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- Review.target (Rateable) is stored in two columns: exactly one is filled.
CREATE TABLE reviews (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id      INT NOT NULL,
    author_id           INT NOT NULL,
    target_user_id      INT NULL,           -- target is a PetOwner or Caretaker
    target_company_id   INT NULL,           -- target is a Company
    rating              TINYINT NOT NULL,
    pet_behavior_rating TINYINT NULL,
    comment             TEXT,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rev_res     FOREIGN KEY (reservation_id)    REFERENCES reservations(id),
    CONSTRAINT fk_rev_author  FOREIGN KEY (author_id)         REFERENCES users(id),
    CONSTRAINT fk_rev_tuser   FOREIGN KEY (target_user_id)    REFERENCES users(id),
    CONSTRAINT fk_rev_tcomp   FOREIGN KEY (target_company_id) REFERENCES companies(id),
    CONSTRAINT uq_one_review_per_author UNIQUE (reservation_id, author_id),
    CONSTRAINT chk_rating   CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_behavior CHECK (pet_behavior_rating IS NULL OR pet_behavior_rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

-- Test login: admin / admin123 (BCrypt hash)
INSERT INTO users (role, username, password_hash, email, first_name, last_name)
VALUES ('ADMIN', 'admin', '$2a$12$ahmucL96a9hkPm/YVpoGzu5CWhYRoNpSE.WplMYbmFRSHfGIejqWK',
        'admin@petregistry.local', 'System', 'Admin');

-- ============================================================
--  DEMO DATA START
--  The same data the app shows in memory mode (SeedData.java), so the tabs are not empty
--  after connecting. Every demo user's password is "Slaptazodis1".
--  Reservation dates are relative to the import day (CURDATE()), so "future" bookings stay in the future.
--  The test scripts cut this block out to get an empty database (see README.md).
-- ============================================================
INSERT INTO companies (id, name, company_code, city, address) VALUES
    (1, 'Letenėlė', '123456789', 'Vilnius', 'Gedimino pr. 1'),
    (2, 'Uodegėlė', '987654321', 'Kaunas',  'Laisvės al. 10');

-- ids 2–9 (admin is 1). company_id: managers = their company, caretakers = employer or NULL (independent).
INSERT INTO users (id, role, username, password_hash, email, first_name, last_name, phone,
                   address, bio, experience_years, company_id) VALUES
    (2, 'COMPANY_MANAGER', 'rasa',   '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'rasa@letenele.lt',   'Rasa',   'Jonaitė',      '+37061111111', NULL, NULL, NULL, 1),
    (3, 'COMPANY_MANAGER', 'mantas', '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'mantas@uodegele.lt', 'Mantas', 'Kazlauskas',   '+37062222222', NULL, NULL, NULL, 2),
    (4, 'CARETAKER',       'tomas',  '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'tomas@letenele.lt',  'Tomas',  'Petraitis',    '+37063333333', NULL, 'Myliu šunis, vedžioju kasdien.', 4, 1),
    (5, 'CARETAKER',       'egle',   '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'egle@uodegele.lt',   'Eglė',   'Vaitkutė',     '+37064444444', NULL, 'Veterinarijos studentė.', 2, 2),
    (6, 'CARETAKER',       'lukas',  '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'lukas@gmail.com',    'Lukas',  'Stankevičius', NULL,           NULL, 'Dirbu savarankiškai.', 7, NULL),
    (7, 'PET_OWNER',       'anna',   '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'anna@gmail.com',     'Anna',   'Kazlauskienė', '+37065555555', 'Vilnius, Žirmūnų g. 5', NULL, NULL, NULL),
    (8, 'PET_OWNER',       'jonas',  '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'jonas@gmail.com',    'Jonas',  'Jonaitis',     '+37066666666', 'Kaunas, Savanorių pr. 20', NULL, NULL, NULL),
    (9, 'PET_OWNER',       'greta',  '$2a$10$rAUQ4cJek5cKykmZYPPi5OpDQR8vSIk.W1DxFU8kBGrnFUu0z2.my', 'greta@gmail.com',    'Greta',  'Paulauskaitė', NULL,           'Vilnius, Antakalnio g. 12', NULL, NULL, NULL);

INSERT INTO pets (id, owner_id, name, species, size, birth_date, weight_kg) VALUES
    (1, 7, 'Reksas',   'DOG',    'LARGE',  '2019-04-12', 32.50),
    (2, 7, 'Murka',    'CAT',    'SMALL',  '2021-08-03',  4.20),
    (3, 8, 'Bimas',    'DOG',    'MEDIUM', '2022-01-20', 14.00),
    (4, 8, 'Žydrukas', 'BIRD',   'SMALL',  '2023-06-01',  0.10),
    (5, 9, 'Pūkas',    'RODENT', 'SMALL',  '2024-02-14',  0.30);

INSERT INTO services (id, company_id, title, description, type, price, duration_minutes, location) VALUES
    (1, 1, 'Šunų vedžiojimas',        'Šunų vedžiojimas',        'DOG_WALKING', 15.00,   60, 'OUTDOORS'),
    (2, 1, 'Kailio kirpimas',         'Kailio kirpimas',         'GROOMING',    35.00,   90, 'PROVIDER_PREMISES'),
    (3, 1, 'Priežiūra namuose',       'Priežiūra namuose',       'PET_SITTING', 25.00,  120, 'CLIENT_HOME'),
    (4, 2, 'Veterinaro konsultacija', 'Veterinaro konsultacija', 'VETERINARY',  40.00,   30, 'PROVIDER_PREMISES'),
    (5, 2, 'Gyvūnų viešbutis',        'Gyvūnų viešbutis',        'BOARDING',    30.00, 1440, 'PROVIDER_PREMISES');

INSERT INTO service_species (service_id, species) VALUES
    (1, 'DOG'),
    (2, 'DOG'), (2, 'CAT'),
    (3, 'DOG'), (3, 'CAT'), (3, 'BIRD'), (3, 'RODENT'), (3, 'OTHER'),
    (4, 'DOG'), (4, 'CAT'), (4, 'BIRD'), (4, 'RODENT'), (4, 'OTHER'),
    (5, 'DOG'), (5, 'CAT');

INSERT INTO service_sizes (service_id, size) VALUES
    (1, 'SMALL'), (1, 'MEDIUM'), (1, 'LARGE'),
    (2, 'SMALL'), (2, 'MEDIUM'), (2, 'LARGE'),
    (3, 'SMALL'), (3, 'MEDIUM'), (3, 'LARGE'),
    (4, 'SMALL'), (4, 'MEDIUM'), (4, 'LARGE'),
    (5, 'SMALL'), (5, 'MEDIUM');

-- One reservation per status. end_at = start_at + the service's duration; price copied from the service.
-- TIMESTAMP(date, '10:00:00') = that day at 10:00.
INSERT INTO reservations (id, owner_id, pet_id, service_id, caretaker_id, start_at, end_at, status, price) VALUES
    (1, 7, 1, 1, 4, TIMESTAMP(CURDATE() + INTERVAL 3 DAY, '10:00:00'),
                    TIMESTAMP(CURDATE() + INTERVAL 3 DAY, '10:00:00') + INTERVAL 60 MINUTE,    'PENDING',     15.00),
    (2, 8, 3, 2, 4, TIMESTAMP(CURDATE() + INTERVAL 5 DAY, '10:00:00'),
                    TIMESTAMP(CURDATE() + INTERVAL 5 DAY, '10:00:00') + INTERVAL 90 MINUTE,    'CONFIRMED',   35.00),
    (3, 9, 5, 4, 5, TIMESTAMP(CURDATE(), '10:00:00'),
                    TIMESTAMP(CURDATE(), '10:00:00') + INTERVAL 30 MINUTE,                     'IN_PROGRESS', 40.00),
    (4, 7, 2, 5, 5, TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '10:00:00'),
                    TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '10:00:00') + INTERVAL 1440 MINUTE, 'COMPLETED',   30.00),
    (5, 8, 4, 3, 4, TIMESTAMP(CURDATE() + INTERVAL 7 DAY, '10:00:00'),
                    TIMESTAMP(CURDATE() + INTERVAL 7 DAY, '10:00:00') + INTERVAL 120 MINUTE,   'CANCELLED',   25.00);

-- Both reviews are on the COMPLETED reservation, one by each participant.
INSERT INTO reviews (id, reservation_id, author_id, target_user_id, target_company_id, rating, pet_behavior_rating, comment) VALUES
    (1, 4, 7, 5, NULL, 5, NULL, 'Murka grįžo laiminga, ačiū!'),
    (2, 4, 5, 7, NULL, 4, 5,    'Maloni šeimininkė, aiškios instrukcijos.');
-- ============================================================
--  DEMO DATA END
-- ============================================================
