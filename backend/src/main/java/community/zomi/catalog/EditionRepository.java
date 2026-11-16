package community.zomi.catalog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Published editions are immutable. Publication writes use a locked SQL transaction. */
public interface EditionRepository extends JpaRepository<Edition, UUID> {}
