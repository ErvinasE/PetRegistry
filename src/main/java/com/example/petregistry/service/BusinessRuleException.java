package com.example.petregistry.service;

/** Thrown when an action is refused by a rule, e.g. "this pet has reservations, it can't be deleted". */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
