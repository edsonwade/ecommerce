package code.with.vanilson.productservice.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    /**
     * Fase 4: case-insensitive name-exists check for the create path — the DB
     * {@code unique_name} constraint is case-sensitive, so this guards against
     * "Keyboards" vs "keyboards" duplicates before the INSERT (409 vs raw 500).
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Fase 4: same check for the update path, excluding the row being edited so
     * renaming a category to its own current name is not flagged as a duplicate.
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);
}
