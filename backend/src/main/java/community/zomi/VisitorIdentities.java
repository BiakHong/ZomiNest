package community.zomi;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persist the browser identity even when the enclosing request is rejected or rolled back. */
@Service
public class VisitorIdentities {
  private final JdbcTemplate db;

  VisitorIdentities(JdbcTemplate db) {
    this.db = db;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID create() {
    UUID id = UUID.randomUUID();
    db.update("insert into users(id,name,visitor) values(?,'Community guest',true)", id);
    return id;
  }
}
