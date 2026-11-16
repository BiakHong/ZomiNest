package community.zomi;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController extends Api {
  CatalogController(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    super(d, j, p, m, e);
  }

  @GetMapping("/quizzes")
  List<Map<String, Object>> catalog() {
    return db
        .queryForList(
            "select q.id,q.slug,q.featured,e.id edition,e.number,e.created_at,e.content from quiz q"
                + " join edition e on e.id=q.current_edition where q.published=true and q.slug<>'community-rhythm' order by"
                + " q.featured desc,e.created_at desc")
        .stream()
        .map(this::publicEdition)
        .toList();
  }

  @GetMapping("/quizzes/{slug}")
  Map<String, Object> detail(@PathVariable String slug) {
    if (slug.equals("community-rhythm")) slug=Personality.SLUG;
    return publicEdition(
        one(
            "select q.id,q.slug,q.featured,e.id edition,e.number,e.created_at,e.content from quiz q"
                + " join edition e on e.id=q.current_edition where q.slug=? and q.published=true",
            slug));
  }

  @GetMapping("/quizzes/{slug}/editions")
  List<Map<String, Object>> editions(@PathVariable String slug) {
    return db.queryForList(
        "select e.id,e.number,e.created_at from edition e join quiz q on q.id=e.quiz_id where"
            + " q.slug=? and q.published=true order by e.number desc",
        slug);
  }

  @PostMapping("/share")
  void share() {
    metric("shares");
  }

  @PostMapping("/quizzes/{slug}/reports")
  void report(@PathVariable String slug, @RequestBody Map<String, Object> b, HttpSession s) {
    limit("report:" + s.getId(), 5);
    db.update(
        "insert into report(id,quiz_id,reason) values(?,?,?)",
        UUID.randomUUID(),
        one("select id from quiz where slug=? and published=true", slug).get("id"),
        text(b, "reason", 2000));
  }

  @PutMapping("/owner/quizzes/{id}")
  void quizControls(@PathVariable UUID id, @RequestBody Map<String, Object> b, HttpSession s) {
    owner(s);
    db.update(
        "update quiz set published=?,featured=? where id=?",
        Boolean.TRUE.equals(b.get("published")),
        Boolean.TRUE.equals(b.get("featured")),
        id);
  }
}
