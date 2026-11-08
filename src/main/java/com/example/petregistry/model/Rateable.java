package com.example.petregistry.model;

/**
 * Something that can receive reviews. The average is a plain field, not a database column.
 * Only ReviewService.refreshAverage (LAB1) and the JDBC repositories (LAB2) may call the setter.
 */
public interface Rateable {
    double getAverageRating();

    void setAverageRating(double averageRating);
}
