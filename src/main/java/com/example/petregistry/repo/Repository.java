package com.example.petregistry.repo;

import com.example.petregistry.model.BaseEntity;

import java.util.List;
import java.util.Optional;

/**
 * The storage contract. Services depend on this interface, not on a concrete class.
 * LAB1: implemented in memory. LAB2: implemented with JDBC.
 */
public interface Repository<T extends BaseEntity> {

    List<T> findAll();

    Optional<T> findById(int id);

    /** Gives the entity a new id, stores it, and returns it. The entity must be new (id = 0). */
    T create(T entity);

    /** Replaces the stored entity that has the same id. The entity must already be saved. */
    void update(T entity);

    void delete(int id);
}