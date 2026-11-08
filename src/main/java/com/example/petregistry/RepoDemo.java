package com.example.petregistry;

import com.example.petregistry.model.Species;
import com.example.petregistry.repo.Repositories;

/** Throwaway check for M3 ("Done when" in BUILD_PLAN.md). Delete this file when M4 starts. */
public class RepoDemo {
    public static void main(String[] args) {
        Repositories.useMemory();

        System.out.println("== findAll() sizes ==");
        System.out.println("users:        " + Repositories.users().findAll().size());
        System.out.println("companies:    " + Repositories.companies().findAll().size());
        System.out.println("services:     " + Repositories.services().findAll().size());
        System.out.println("pets:         " + Repositories.pets().findAll().size());
        System.out.println("reservations: " + Repositories.reservations().findAll().size());
        System.out.println("reviews:      " + Repositories.reviews().findAll().size());

        System.out.println("== one search ==");
        System.out.println("dogs: " + Repositories.pets().search(null, Species.DOG, null));

        System.out.println("== one countBy ==");
        int reksasId = Repositories.pets().search("Reksas", null, null).get(0).getId();
        System.out.println("reservations of Reksas: " + Repositories.reservations().countByPet(reksasId));
    }
}
