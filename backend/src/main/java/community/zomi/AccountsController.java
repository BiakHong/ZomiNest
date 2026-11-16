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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AccountsController extends Api {
  AccountsController(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    super(d, j, p, m, e);
  }

  @GetMapping("/session")
  Map<String, Object> session(HttpServletRequest r) {
    CsrfToken t = (CsrfToken) r.getAttribute(CsrfToken.class.getName());
    var m = new HashMap<String, Object>();
    m.put("csrf", t.getToken());
    m.put("user", user(participant(r.getSession())));
    return m;
  }

  @PostMapping("/register")
  @Transactional
  Map<String, Object> register(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    limit("auth:" + r.getRemoteAddr(), 10);
    String email = text(b, "email", 254).toLowerCase(Locale.ROOT),
        pass = text(b, "password", 128),
        name = text(b, "name", 60);
    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || pass.length() < 12)
      throw new IllegalArgumentException(
          "Use a valid email and a password of at least 12 characters.");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into users(id,email,password,name) values(?,?,?,?)",
        id,
        email,
        passwords.encode(pass),
        name);
    r.changeSessionId();
    r.getSession().setAttribute("uid", id);
    return user(id);
  }

  @PostMapping("/login")
  Map<String, Object> login(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    limit("auth:" + r.getRemoteAddr(), 10);
    var rows =
        db.queryForList(
            "select * from users where email=?", text(b, "email", 254).toLowerCase(Locale.ROOT));
    String pass = text(b, "password", 128);
    String hash =
        rows.isEmpty()
            ? "$2a$10$7EqJtq98hPqEX7fNZaFWoO5M9PB.RwGqbL8O.3QJbRCJge0VHHKc2"
            : (String) rows.getFirst().get("password");
    if (!passwords.matches(pass, hash) || rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
    r.changeSessionId();
    UUID id = (UUID) rows.getFirst().get("id");
    r.getSession().setAttribute("uid", id);
    return user(id);
  }

  @PostMapping("/logout")
  void logout(HttpSession s) {
    s.invalidate();
  }

  @PutMapping("/account")
  @Transactional
  Map<String, Object> account(@RequestBody Map<String, Object> b, HttpSession s) {
    UUID id = participant(s);
    if (Boolean.FALSE.equals(b.get("publicScore")))
      db.update("update attempt set shared=false where user_id=?", id);
    db.update(
        "update users set name=?,public_score=? where id=?",
        text(b, "name", 60),
        Boolean.TRUE.equals(b.get("publicScore")),
        id);
    return user(id);
  }

  @PostMapping("/recovery")
  @Transactional
  Map<String, String> recovery(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    limit("reset:" + r.getRemoteAddr(), 5);
    var rows =
        db.queryForList(
            "select id from users where email=?", text(b, "email", 254).toLowerCase(Locale.ROOT));
    if (!rows.isEmpty()) {
      byte[] bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      db.update(
          "insert into reset_token values(?,?,?)",
          hash(token),
          rows.getFirst().get("id"),
          java.sql.Timestamp.from(Instant.now().plusSeconds(1800)));
      db.update(
          "insert into mail_outbox(id,recipient,body) values(?,?,?)",
          UUID.randomUUID(),
          (String) b.get("email"),
          "Open this link within 30 minutes: " + base + "/reset?token=" + token);
    }
    return Map.of("message", "If that account exists, a recovery email has been sent.");
  }

  @PostMapping("/reset")
  @Transactional
  void reset(@RequestBody Map<String, Object> b) {
    String pass = text(b, "password", 128);
    if (pass.length() < 12) throw new IllegalArgumentException("Use at least 12 characters.");
    var row =
        one(
            "delete from reset_token where token_hash=? and expires_at>now() returning user_id",
            hash(text(b, "token", 200)));
    UUID id = (UUID) row.get("user_id");
    db.update("update users set password=? where id=?", passwords.encode(pass), id);
    db.update("delete from reset_token where user_id=?", id);
    db.update(
        "delete from spring_session where primary_id in (select session_primary_id from"
            + " spring_session_attributes where attribute_name='uid' and attribute_bytes=?)",
        serialize(id));
  }
}
