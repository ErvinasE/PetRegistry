package com.example.petregistry.model;

/**
 * Something that can receive reviews. The average is a plain field, not a database column.
 * Only ReviewService.refreshAverage and the JDBC repositories may call the setter.
 */
public interface Rateable {
    double getAverageRating();

    void setAverageRating(double averageRating);
}
