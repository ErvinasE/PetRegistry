package com.example.petregistry.model;

public abstract class BaseEntity {
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isNew() {
        return id == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
