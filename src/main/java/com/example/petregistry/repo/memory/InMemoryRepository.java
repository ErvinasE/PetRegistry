package com.example.petregistry.repo.memory;

import com.example.petregistry.model.BaseEntity;
import com.example.petregistry.repo.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** One generic class that stores any entity type in a map. No database needed. */
public class InMemoryRepository<T extends BaseEntity> implements Repository<T> {

    // LinkedHashMap keeps insertion order, so tables show rows in the order they were created.
    private final Map<Integer, T> items = new LinkedHashMap<>();
    private int nextId = 1;

    @Override
    public List<T> findAll() {
        // A copy, so a caller cannot change our map by accident.
        return new ArrayList<>(items.values());
    }

    @Override
    public Optional<T> findById(int id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public T create(T entity) {
        if (!entity.isNew()) {
            throw new IllegalArgumentException("Entity is already saved (id = " + entity.getId() + ")");
        }
        entity.setId(nextId++);
        items.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public void update(T entity) {
        if (!items.containsKey(entity.getId())) {
            throw new IllegalArgumentException("Nothing to update, no entity with id " + entity.getId());
        }
        items.put(entity.getId(), entity);
    }

    @Override
    public void delete(int id) {
        items.remove(id);
    }

    /** For subclasses: a stream over all stored items, used to write search methods. */
    protected Stream<T> stream() {
        return items.values().stream();
    }

    /** Search helper. True when the filter is empty, or when the value contains it (ignoring case). */
    protected static boolean matchesText(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase().contains(filter.trim().toLowerCase());
    }

    /** Search helper. True when no id is given, or when the entity exists and has that id. */
    protected static boolean matchesId(BaseEntity entity, Integer id) {
        return id == null || (entity != null && entity.getId() == id);
    }
}
