package com.example.petregistry.repo;

import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.repo.jdbc.JdbcCompanyRepository;
import com.example.petregistry.repo.jdbc.JdbcPetRepository;
import com.example.petregistry.repo.jdbc.JdbcReservationRepository;
import com.example.petregistry.repo.jdbc.JdbcReviewRepository;
import com.example.petregistry.repo.jdbc.JdbcServiceRepository;
import com.example.petregistry.repo.jdbc.JdbcUserRepository;
import com.example.petregistry.repo.memory.InMemoryCompanyRepository;
import com.example.petregistry.repo.memory.InMemoryPetRepository;
import com.example.petregistry.repo.memory.InMemoryReservationRepository;
import com.example.petregistry.repo.memory.InMemoryReviewRepository;
import com.example.petregistry.repo.memory.InMemoryServiceRepository;
import com.example.petregistry.repo.memory.InMemoryUserRepository;
import com.example.petregistry.repo.memory.SeedData;

/**
 * The one place that decides which storage is in use.
 * LAB1: useMemory(). LAB2: useDatabase() after connecting.
 * Services call Repositories.pets() etc. every time, so they follow a switch.
 */
public final class Repositories {

    private static UserRepository users;
    private static CompanyRepository companies;
    private static ServiceRepository services;
    private static PetRepository pets;
    private static ReservationRepository reservations;
    private static ReviewRepository reviews;
    private static boolean database;

    private Repositories() {
        // only static methods, no objects
    }

    /** Fresh in-memory storage filled with seed data. Old in-memory data is thrown away. */
    public static void useMemory() {
        users = new InMemoryUserRepository();
        companies = new InMemoryCompanyRepository();
        services = new InMemoryServiceRepository();
        pets = new InMemoryPetRepository();
        reservations = new InMemoryReservationRepository();
        reviews = new InMemoryReviewRepository();
        SeedData.fill(users, companies, services, pets, reservations, reviews);
        database = false;
    }

    /**
     * LAB2: the same interfaces, now implemented with JDBC. Call after DatabaseManager.connect().
     * Nothing else changes: services keep calling Repositories.pets() etc. and get the JDBC version.
     */
    public static void useDatabase() {
        if (!DatabaseManager.isConnected()) {
            throw new IllegalStateException("Connect first: DatabaseManager.connect(...)");
        }
        users = new JdbcUserRepository();
        companies = new JdbcCompanyRepository();
        services = new JdbcServiceRepository();
        pets = new JdbcPetRepository();
        reservations = new JdbcReservationRepository();
        reviews = new JdbcReviewRepository();
        database = true;
    }

    public static boolean isDatabase() {
        return database;
    }

    /**
     * Runs several repository calls as one unit: all are saved, or (on any error) none.
     * Database: a real SQL transaction. Memory: just runs the work (a map has nothing to roll back).
     * Services use this, so they never need java.sql.
     */
    public static void inTransaction(Runnable work) {
        if (database) {
            DatabaseManager.inTransaction(work);
        } else {
            work.run();
        }
    }

    public static UserRepository users() {
        return ready(users);
    }

    public static CompanyRepository companies() {
        return ready(companies);
    }

    public static ServiceRepository services() {
        return ready(services);
    }

    public static PetRepository pets() {
        return ready(pets);
    }

    public static ReservationRepository reservations() {
        return ready(reservations);
    }

    public static ReviewRepository reviews() {
        return ready(reviews);
    }

    /** A clear error instead of a NullPointerException somewhere later. */
    private static <R> R ready(R repository) {
        if (repository == null) {
            throw new IllegalStateException("Storage is not chosen yet: call Repositories.useMemory() first");
        }
        return repository;
    }
}
