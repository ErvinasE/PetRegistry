import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Species;
import com.example.petregistry.repo.PetRepository;
import com.example.petregistry.repo.memory.InMemoryPetRepository;

public class PetSearchCheck {
    static Pet pet(String name, Species s) {
        Pet p = new Pet();
        p.setName(name);
        p.setSpecies(s);
        return p;
    }

    public static void main(String[] args) {
        PetRepository repo = new InMemoryPetRepository();

        PetOwner anna = new PetOwner();
        anna.setId(10);
        PetOwner jonas = new PetOwner();
        jonas.setId(20);

        Pet rex = repo.create(pet("Rex", Species.DOG));
        Pet rexa = repo.create(pet("Maxi", Species.DOG));
        Pet mia = repo.create(pet("Mia", Species.CAT));
        Pet stray = repo.create(pet("Stray", Species.CAT)); // no owner

        anna.addPet(rex);
        anna.addPet(mia);
        jonas.addPet(rexa);

        System.out.println("no filters (expect 4): " + repo.search(null, null, null, null, null, null, null, null).size());
        System.out.println("blank name (expect 4): " + repo.search("  ", null, null, null, null, null, null, null).size());
        System.out.println("name 'REX' case-insens (expect 1 Rex): " + repo.search("REX", null, null, null, null, null, null, null));
        System.out.println("name 'x' (expect Rex, Maxi): " + repo.search("x", null, null, null, null, null, null, null));
        System.out.println("species CAT (expect Mia, Stray): " + repo.search(null, Species.CAT, null, null, null, null, null, null));
        System.out.println("owner 10 (expect Rex, Mia): " + repo.search(null, null, 10, null, null, null, null, null));
        System.out.println("owner 20 (expect Maxi): " + repo.search(null, null, 20, null, null, null, null, null));
        System.out.println("owner 10 + CAT (expect Mia): " + repo.search(null, Species.CAT, 10, null, null, null, null, null));
        System.out.println("owner 10 + DOG + name 'm' (expect none): " + repo.search("m", Species.DOG, 10, null, null, null, null, null));
        System.out.println("owner 99 (expect none): " + repo.search(null, null, 99, null, null, null, null, null));
    }
}
