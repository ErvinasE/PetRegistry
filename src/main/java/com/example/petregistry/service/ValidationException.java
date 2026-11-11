package com.example.petregistry.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thrown by create/update. Holds ALL errors at once (field name -> message). */
public class ValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(String.join("\n", errors.values()));
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    /** Field name -> message, in the order the checks ran. */
    public Map<String, String> getErrors() {
        return errors;
    }
}