package community.zomi.catalog;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "edition")
@Immutable
public class Edition {
  @Id private UUID id;

  @Column(name = "quiz_id", nullable = false)
  private UUID quizId;

  @Column(nullable = false)
  private int number;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  protected Edition() {}

  public String content() {
    return content;
  }

  public UUID id() {
    return id;
  }
}
