-- ============================================================
--  Pet Registry – LAB2 database schema (matches docs/lab1-classes.puml)
--  MySQL 8+ or MariaDB 10.4+ (XAMPP)
--  Run: mysql -u root -p < schema.sql   (or phpMyAdmin → Import)
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
