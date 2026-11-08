package com.example.petregistry;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.Species;

/** Throwaway check for M2. Delete this file when M3 starts. */
public class ModelDemo {
    public static void main(String[] args) {
        PetOwner owner = new PetOwner();
        owner.setFirstName("Jonas");
        owner.setLastName("Jonaitis");

        Pet rex = new Pet();
        rex.setName("Reksas");
        rex.setSpecies(Species.DOG);
        rex.setSize(PetSize.LARGE);

        Pet tom = new Pet();
        tom.setName("Tomas");
        tom.setSpecies(Species.CAT);
        tom.setSize(PetSize.SMALL);

        owner.addPet(rex);
        owner.addPet(tom);

        System.out.println("Owner: " + owner + " has " + owner.getPets().size() + " pets: " + owner.getPets());
        System.out.println("Reksas's owner: " + rex.getOwner());

        try {
            owner.getPets().add(new Pet());
        } catch (UnsupportedOperationException e) {
            System.out.println("getPets() is read-only: OK");
        }

        owner.removePet(rex);
        System.out.println("After removePet: " + owner.getPets() + ", Reksas's owner: " + rex.getOwner());

        try {
            new Reservation().complete();
        } catch (IllegalStateException e) {
            System.out.println("Bad transition refused: " + e.getMessage());
        }
    }
}
