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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ContributionsController extends Api {
  ContributionsController(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    super(d, j, p, m, e);
  }

  @GetMapping("/submissions")
  List<Map<String, Object>> submissions(HttpSession s) {
    return db
        .queryForList(
            "select * from submission where user_id=? order by submitted_at desc nulls first",
            participant(s))
        .stream()
        .map(this::submissionJson)
        .toList();
  }

  @PostMapping("/submissions")
  Map<String, Object> draft(@RequestBody Draft b, HttpSession s) {
    UUID id = UUID.randomUUID();
    if (b.content() == null
        || b.content().questions() == null
        || b.kind() == null
        || !List.of("QUIZ", "QUESTION").contains(b.kind()))
      throw new IllegalArgumentException("Invalid contribution kind.");
    db.update(
        "insert into submission(id,user_id,status,kind,target_id,content)"
            + " values(?,?,'DRAFT',?,?,?)",
        id,
        participant(s),
        b.kind(),
        b.targetId(),
        encode(b.content()));
    return Map.of("id", id);
  }

  @PutMapping("/submissions/{id}")
  @Transactional
  void edit(@PathVariable UUID id, @RequestBody Draft b, HttpSession s) {
    if (b.content() == null
        || b.content().questions() == null
        || b.kind() == null
        || !List.of("QUIZ", "QUESTION").contains(b.kind()))
      throw new IllegalArgumentException("Invalid contribution kind.");
    var r = ownSubmission(id, s);
    if (!List.of("DRAFT", "CHANGES_REQUESTED", "WITHDRAWN", "REJECTED").contains(r.get("status")))
      throw new IllegalArgumentException("This revision is frozen.");
    db.update(
        "update submission set content=?,kind=?,target_id=?,status='DRAFT',revision=revision+1"
            + " where id=?",
        encode(b.content()),
        b.kind(),
        b.targetId(),
        id);
  }

  @PostMapping("/submissions/{id}/submit")
  @Transactional
  void submit(@PathVariable UUID id, HttpSession s) {
    limit("submit:" + participant(s), 10);
    var r = ownSubmission(id, s);
    if (!r.get("status").equals("DRAFT"))
      throw new IllegalArgumentException("Save a draft before submitting.");
    var q = quiz((String) r.get("content"));
    Content.validate(q);
    if (r.get("kind").equals("QUESTION")
        && (!q.type().equals("KNOWLEDGE")
            || q.questions().size() != 1
            || r.get("target_id") == null))
      throw new IllegalArgumentException("Select a collection and provide one knowledge question.");
    db.update(
        "insert into submission_revision values(?,?,?,?)",
        UUID.randomUUID(),
        id,
        r.get("revision"),
        r.get("content"));
    db.update(
        "update submission set status='SUBMITTED',submitted_at=now(),note=null where id=?", id);
  }

  @PostMapping("/submissions/{id}/withdraw")
  @Transactional
  void withdraw(@PathVariable UUID id, HttpSession s) {
    var r = ownSubmission(id, s);
    if (!r.get("status").equals("SUBMITTED"))
      throw new IllegalArgumentException("Only pending submissions can be withdrawn.");
    db.update("update submission set status='WITHDRAWN' where id=?", id);
  }
}
