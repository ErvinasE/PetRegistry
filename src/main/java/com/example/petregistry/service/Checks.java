package com.example.petregistry.service;

import java.util.Map;

/** Small helpers shared by the services. Package-private: only the service package uses them. */
final class Checks {

    private Checks() {
    }

    /** "Required" rule: null, empty or only spaces counts as empty. */
    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Ends a validate() method: if any check failed, throw them all together. */
    static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
