package community.zomi;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.stereotype.Component
@org.springframework.context.annotation.Primary
public class Api {
  final community.zomi.catalog.EditionRepository editions;
  final JdbcTemplate db;
  final ObjectMapper json;
  final PasswordEncoder passwords;
  final JavaMailSender mail;

  @Value("${app.base-url}")
  String base;

  @Value("${app.mail-from}")
  String from;

  Api(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    db = d;
    json = j;
    passwords = p;
    mail = m;
    editions = e;
  }

  public JdbcTemplate db() {
    return db;
  }

  public ObjectMapper json() {
    return json;
  }

  public PasswordEncoder passwords() {
    return passwords;
  }

  public JavaMailSender mail() {
    return mail;
  }

  public String from() {
    return from;
  }

  String encode(Object x) {
    try {
      return json.writeValueAsString(x);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid content.");
    }
  }

  Content.Quiz quiz(String s) {
    try {
      return json.readValue(s, Content.Quiz.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid quiz format.");
    }
  }

  Map<String, Object> decode(String s) {
    try {
      return json.readValue(
          s, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid record.");
    }
  }

  static ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
  }

  UUID uid(HttpSession s) {
    return (UUID) s.getAttribute("uid");
  }

  @org.springframework.beans.factory.annotation.Autowired VisitorIdentities visitors;

  UUID actor(HttpSession s) {
    return uid(s) != null ? uid(s) : (UUID) s.getAttribute("visitorId");
  }

  UUID participant(HttpSession s) {
    UUID current = actor(s);
    if (current != null) return current;
    // One browser session owns one real visitor identity. No fabricated credentials.
    synchronized (s) {
      current = actor(s);
      if (current == null) {
        current = visitors.create();
        s.setAttribute("visitorId", current);
      }
      return current;
    }
  }

  UUID member(HttpSession s) {
    UUID id = uid(s);
    if (id == null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    return id;
  }

  Map<String, Object> user(UUID id) {
    var rows =
        db.queryForList(
            "select id,name,role,public_score,restricted,visitor from users where id=?", id);
    if (rows.isEmpty()) throw denied();
    return rows.getFirst();
  }

  UUID owner(HttpSession s) {
    UUID id = member(s);
    if (!user(id).get("role").equals("OWNER")) throw denied();
    return id;
  }

  Map<String, Object> one(String sql, Object... args) {
    var rows = db.queryForList(sql, args);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found.");
    return rows.getFirst();
  }

  final ConcurrentHashMap<String, ArrayDeque<Long>> limits = new ConcurrentHashMap<>();

  void limit(String key, int max) {
    long now = System.currentTimeMillis();
    var q = limits.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && q.peek() < now - 60000) q.remove();
      if (q.size() >= max)
        throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS, "Please wait a minute and try again.");
      q.add(now);
    }
    if (limits.size() > 10000)
      limits.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peek() < now - 60000);
  }

  void metric(String name) {
    db.update(
        "insert into metric(name,value) values(?,1) on conflict(name) do update set"
            + " value=metric.value+1",
        name);
  }

  String text(Map<String, Object> b, String k, int max) {
    Object x = b.get(k);
    if (!(x instanceof String v) || v.isBlank() || v.length() > max)
      throw new IllegalArgumentException("Invalid " + k + ".");
    return v.trim();
  }

  String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException();
    }
  }

  byte[] serialize(UUID id) {
    try {
      var b = new java.io.ByteArrayOutputStream();
      new java.io.ObjectOutputStream(b).writeObject(id);
      return b.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException();
    }
  }

  Map<String, Object> publicEdition(Map<String, Object> row) {
    var m = new LinkedHashMap<>(Content.publicQuiz(quiz((String) row.get("content"))));
    for (String k : List.of("id", "slug", "featured", "edition", "number", "created_at"))
      if (row.containsKey(k)) m.put(k, row.get(k));
    return m;
  }

  Map<String, Object> owned(UUID id, HttpSession s, boolean lock) {
    var a = one("select * from attempt where id=?" + (lock ? " for update" : ""), id);
    UUID u = actor(s);
    if (a.get("user_id") != null
        ? !a.get("user_id").equals(u)
        : !Objects.equals(a.get("guest_key"), s.getAttribute("guest")) || uid(s) != null)
      throw denied();
    return a;
  }

  public record Answers(Map<String, Object> answers, Integer page) {}

  void validPartial(Content.Quiz q, Map<String, ?> a) {
    if (Personality.isCurrent(q)) {
      Personality.validateAnswers(q, a, false);
      return;
    }
    if (a == null || a.size() > q.questions().size())
      throw new IllegalArgumentException("Invalid answers.");
    for (var e : a.entrySet()) {
      var x =
          q.questions().stream()
              .filter(v -> v.id().equals(e.getKey()))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown question."));
      if (x.options().stream().noneMatch(v -> v.id().equals(e.getValue())))
        throw new IllegalArgumentException("Invalid option.");
    }
  }

  Map<String, Object> submissionJson(Map<String, Object> r) {
    r.put("content", decode((String) r.get("content")));
    return r;
  }

  public record Draft(String kind, UUID targetId, Content.Quiz content) {}

  Map<String, Object> ownSubmission(UUID id, HttpSession s) {
    var r = one("select * from submission where id=? for update", id);
    if (!r.get("user_id").equals(participant(s))) throw denied();
    return r;
  }

  @Transactional
  UUID publish(UUID qid, Content.Quiz q) {
    Content.validate(q);
    if (qid == null) {
      qid = UUID.randomUUID();
      String slug =
          q.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
      if (slug.isEmpty()) slug = "quiz";
      slug += "-" + qid.toString().substring(0, 8);
      db.update("insert into quiz(id,slug) values(?,?)", qid, slug);
    }
    one("select id from quiz where id=? for update", qid);
    int n =
        db.queryForObject(
            "select coalesce(max(number),0)+1 from edition where quiz_id=?", Integer.class, qid);
    UUID eid = UUID.randomUUID();
    db.update(
        "insert into edition(id,quiz_id,number,content) values(?,?,?,?)", eid, qid, n, encode(q));
    db.update("update quiz set current_edition=?,published=true where id=?", eid, qid);
    return eid;
  }

  public record Decision(int revision, String decision, String note) {}

  Content.Quiz bankQuiz(UUID qid, List<UUID> entries) {
    var row =
        one(
            "select e.content from edition e join quiz q on q.current_edition=e.id where q.id=?",
            qid);
    var q = quiz((String) row.get("content"));
    if (!q.type().equals("KNOWLEDGE"))
      throw new IllegalArgumentException("Choose a knowledge collection.");
    var questions = new ArrayList<>(q.questions());
    for (UUID id : new LinkedHashSet<>(entries)) {
      var bank =
          one(
              "select content from question_bank where id=? and target_id=? and published_id is"
                  + " null",
              id,
              qid);
      var contribution = quiz((String) bank.get("content"));
      for (var x : contribution.questions())
        questions.add(
            new Content.Question(
                x.id(),
                x.text(),
                x.options(),
                x.explanation(),
                x.trait(),
                x.reverse(),
                x.source(),
                x.explanationEn(),
                contribution.credit()));
    }
    var next = q.withQuestions(questions);
    Content.validate(next);
    return next;
  }

  public record BankPublish(UUID quizId, List<UUID> entries, UUID expectedEdition) {}

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  ResponseEntity<?> unreadable() {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                "Invalid JSON. Use unique answer keys and the documented request fields."));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> bad(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", Objects.requireNonNullElse(e.getReason(), "Request failed.")));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "message",
                "That email or record already exists, or the referenced collection is invalid."));
  }

  @ExceptionHandler(org.springframework.mail.MailException.class)
  ResponseEntity<?> mailFailure() {
    return ResponseEntity.status(503)
        .body(
            Map.of(
                "message", "Recovery email is temporarily unavailable. Please try again later."));
  }
}
