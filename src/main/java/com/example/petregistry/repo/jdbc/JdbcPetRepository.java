package com.example.petregistry.repo.jdbc;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Species;
import com.example.petregistry.repo.PetRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pets in MySQL. pets.owner_id is the composition "an owner has pets". */
public class JdbcPetRepository implements PetRepository {

    private static final String SELECT = "SELECT * FROM pets p";

    @Override
    public List<Pet> findAll() {
        return Sql.list(SELECT + " ORDER BY p.id", JdbcPetRepository::mapFull);
    }

    @Override
    public Optional<Pet> findById(int id) {
        return Sql.one(SELECT + " WHERE p.id = ?", JdbcPetRepository::mapFull, id);
    }

    /** PetService links the owner BEFORE calling create, so pet.getOwner() is set here. */
    @Override
    public Pet create(Pet pet) {
        int id = Sql.insert("""
                        INSERT INTO pets (owner_id, name, species, size, birth_date, weight_kg, notes, photo_path)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                pet.getOwner().getId(), pet.getName(), pet.getSpecies(), pet.getSize(), pet.getBirthDate(),
                pet.getWeightKg(), pet.getNotes(), pet.getPhotoPath());
        pet.setId(id);
        return pet;
    }

    @Override
    public void update(Pet pet) {
        Sql.execute("""
                        UPDATE pets SET owner_id = ?, name = ?, species = ?, size = ?, birth_date = ?, weight_kg = ?,
                                        notes = ?, photo_path = ?
                        WHERE id = ?
                        """,
                pet.getOwner().getId(), pet.getName(), pet.getSpecies(), pet.getSize(), pet.getBirthDate(),
                pet.getWeightKg(), pet.getNotes(), pet.getPhotoPath(), pet.getId());
    }

    @Override
    public void delete(int id) {
        Sql.execute("DELETE FROM pets WHERE id = ?", id);
    }

    @Override
    public List<Pet> search(String name, Species species, Integer ownerId) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!Sql.blank(name)) {
            sql.append(" AND p.name LIKE ?");
            params.add(Sql.contains(name));
        }
        if (species != null) {
            sql.append(" AND p.species = ?");
            params.add(species);
        }
        if (ownerId != null) {
            sql.append(" AND p.owner_id = ?");
            params.add(ownerId);
        }
        sql.append(" ORDER BY p.id");
        return Sql.list(sql.toString(), JdbcPetRepository::mapFull, params.toArray());
    }

    // ----------------------------------------------------------------- for the other JDBC repositories

    /** An owner's pets without the owner link (the owner adds them to itself with addPet). */
    static List<Pet> findPlainByOwner(int ownerId) {
        return Sql.list(SELECT + " WHERE p.owner_id = ? ORDER BY p.id", JdbcPetRepository::mapRow, ownerId);
    }

    // ----------------------------------------------------------------- row → object

    private static Pet mapRow(ResultSet rs) throws SQLException {
        Pet pet = new Pet();
        pet.setId(rs.getInt("id"));
        pet.setName(rs.getString("name"));
        pet.setSpecies(Sql.enumOf(Species.class, rs, "species"));
        pet.setSize(Sql.enumOf(PetSize.class, rs, "size"));
        pet.setBirthDate(Sql.date(rs, "birth_date"));
        pet.setWeightKg(rs.getDouble("weight_kg"));
        pet.setNotes(rs.getString("notes"));
        pet.setPhotoPath(rs.getString("photo_path"));
        return pet;
    }

    /** mapRow + the owner (plain). addPet also sets pet.owner. */
    private static Pet mapFull(ResultSet rs) throws SQLException {
        Pet pet = mapRow(rs);
        JdbcUserRepository.findPlain(rs.getInt("owner_id"))
                .ifPresent(owner -> ((PetOwner) owner).addPet(pet));
        return pet;
    }
}
