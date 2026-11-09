import com.example.petregistry.model.Pet;
import com.example.petregistry.repo.Repository;
import com.example.petregistry.repo.memory.InMemoryRepository;

public class RepoCheck {
    public static void main(String[] args) {
        Repository<Pet> repo = new InMemoryRepository<>();
        Pet p = new Pet();
        p.setName("Rex");
        repo.create(p);
        System.out.println("id after create (expect 1): " + p.getId());
        System.out.println("findAll size (expect 1): " + repo.findAll().size());
        System.out.println("findById(1) present (expect true): " + repo.findById(1).isPresent());
        System.out.println("findById(99) present (expect false): " + repo.findById(99).isPresent());

        Pet q = new Pet();
        q.setName("Mia");
        repo.create(q);
        System.out.println("second id (expect 2): " + q.getId());

        q.setName("Mia2");
        repo.update(q);
        System.out.println("after update name (expect Mia2): " + repo.findById(2).get().getName());

        try {
            repo.create(p);
            System.out.println("create twice: NO EXCEPTION (wrong)");
        } catch (IllegalArgumentException e) {
            System.out.println("create twice threw (expected): " + e.getMessage());
        }

        Pet ghost = new Pet();
        ghost.setId(99);
        try {
            repo.update(ghost);
            System.out.println("update unknown: NO EXCEPTION (wrong)");
        } catch (IllegalArgumentException e) {
            System.out.println("update unknown threw (expected): " + e.getMessage());
        }

        repo.findAll().clear();
        System.out.println("findAll copy cleared, real size still (expect 2): " + repo.findAll().size());

        repo.delete(1);
        repo.delete(99);
        System.out.println("after delete(1), size (expect 1): " + repo.findAll().size());
    }
}
