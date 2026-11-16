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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ReviewController extends Api {
  ReviewController(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    super(d, j, p, m, e);
  }

  @GetMapping("/owner")
  Map<String, Object> dashboard(HttpSession s) {
    owner(s);
    var m = new LinkedHashMap<String, Object>();
    m.put(
        "submissions",
        db
            .queryForList(
                "select s.*,u.name contributor from submission s join users u on u.id=s.user_id"
                    + " order by submitted_at")
            .stream()
            .map(this::submissionJson)
            .toList());
    m.put(
        "quizzes",
        db.queryForList(
            "select id,slug,published,featured,current_edition from quiz order by slug"));
    m.put(
        "bank",
        db.queryForList("select * from question_bank").stream().map(this::submissionJson).toList());
    m.put(
        "reports",
        db.queryForList(
            "select r.*,q.slug from report r join quiz q on q.id=r.quiz_id order by created_at"
                + " desc"));
    m.put("users", db.queryForList("select id,name,restricted,public_score,visitor from users"));
    m.put(
        "boards",
        db.queryForList(
            "select l.id,u.name,l.score,l.hidden,l.reason from leaderboard l join users u on"
                + " u.id=l.user_id"));
    m.put("metrics", db.queryForList("select * from metric"));
    m.put(
        "pendingHours",
        db.queryForObject(
            "select coalesce(avg(extract(epoch from now()-submitted_at)/3600),0) from submission"
                + " where status='SUBMITTED'",
            Double.class));
    return m;
  }

  @PostMapping("/owner/submissions/{id}/review")
  @Transactional
  Map<String, Object> review(@PathVariable UUID id, @RequestBody Decision b, HttpSession s) {
    UUID oid = owner(s);
    var r = one("select * from submission where id=? for update", id);
    if (!r.get("status").equals("SUBMITTED")
        || ((Number) r.get("revision")).intValue() != b.revision())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The submitted revision changed or was withdrawn.");
    if (!List.of("APPROVE", "CHANGES_REQUESTED", "REJECTED").contains(b.decision()))
      throw new IllegalArgumentException("Invalid decision.");
    if (!b.decision().equals("APPROVE") && (b.note() == null || b.note().isBlank()))
      throw new IllegalArgumentException("Provide a review note.");
    var snap =
        one(
            "select content from submission_revision where submission_id=? and revision=?",
            id,
            b.revision());
    UUID eid = null;
    String state = b.decision();
    if (state.equals("APPROVE")) {
      var q = quiz((String) snap.get("content"));
      Content.validate(q);
      if (r.get("kind").equals("QUESTION")) {
        db.update(
            "insert into question_bank(id,submission_id,target_id,content) values(?,?,?,?)",
            UUID.randomUUID(),
            id,
            r.get("target_id"),
            snap.get("content"));
        state = "APPROVED_IN_BANK";
      } else {
        eid = publish((UUID) r.get("target_id"), q);
        state = "PUBLISHED";
      }
    }
    db.update(
        "update submission set status=?,note=?,published_id=? where id=?",
        state,
        b.note(),
        eid,
        id);
    db.update(
        "insert into review(id,submission_id,revision,reviewer,decision,note,edition_id)"
            + " values(?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        id,
        b.revision(),
        oid,
        b.decision(),
        b.note(),
        eid);
    return Map.of("status", state);
  }

  @GetMapping("/owner/bank/{qid}/preview")
  @Transactional
  Map<String, Object> bankPreview(
      @PathVariable UUID qid, @RequestParam List<UUID> entries, HttpSession s) {
    owner(s);
    one("select id from quiz where id=? for update", qid);
    var preview = decode(encode(bankQuiz(qid, entries)));
    preview.put(
        "baseEdition",
        one("select current_edition from quiz where id=?", qid).get("current_edition"));
    return preview;
  }

  @PostMapping("/owner/bank/publish")
  @Transactional
  Map<String, Object> bankPublish(@RequestBody BankPublish b, HttpSession s) {
    owner(s);
    var current = one("select current_edition from quiz where id=? for update", b.quizId());
    if (!Objects.equals(current.get("current_edition"), b.expectedEdition()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The destination edition changed. Preview again before publishing.");
    var next = bankQuiz(b.quizId(), b.entries());
    UUID eid = publish(b.quizId(), next);
    for (UUID id : b.entries()) {
      db.update("update question_bank set published_id=? where id=?", eid, id);
      db.update(
          "update submission set status='PUBLISHED_IN_QUIZ',published_id=? where id=(select"
              + " submission_id from question_bank where id=?)",
          eid,
          id);
    }
    return Map.of("edition", eid);
  }

  @PutMapping("/owner/users/{id}")
  @Transactional
  void moderate(@PathVariable UUID id, @RequestBody Map<String, Object> b, HttpSession s) {
    UUID oid = owner(s);
    String reason = text(b, "reason", 1000);
    db.update(
        "update users set name=?,restricted=? where id=?",
        text(b, "name", 60),
        Boolean.TRUE.equals(b.get("restricted")),
        id);
    db.update(
        "insert into moderation(id,owner_id,user_id,reason) values(?,?,?,?)",
        UUID.randomUUID(),
        oid,
        id,
        reason);
  }

  @PutMapping("/owner/reports/{id}")
  void resolve(@PathVariable UUID id, HttpSession s) {
    owner(s);
    db.update("update report set resolved=true where id=?", id);
  }
}
