package community.zomi;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class GuestbookController extends Api {
  static final int PAGE_SIZE = 10;

  GuestbookController(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    super(d, j, p, m, e);
  }

  @GetMapping("/guestbook")
  Map<String, Object> entries(@RequestParam(defaultValue = "0") int page, HttpSession s) {
    return list(page, s, false);
  }

  @PostMapping("/guestbook")
  @Transactional
  Map<String, Object> sign(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    String name = text(body, "name", 60), message = text(body, "message", 1000);
    UUID userId = participant(request.getSession());
    if (Boolean.TRUE.equals(user(userId).get("restricted")))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Posting is unavailable for this browser or account.");
    limit("guestbook:" + userId, 3);
    limit("guestbook-ip:" + request.getRemoteAddr(), 12);
    UUID id = UUID.randomUUID();
    db.update(
        "insert into guestbook_entry(id,user_id,name,message) values(?,?,?,?)",
        id,
        userId,
        name,
        message);
    return publicEntry(one("select * from guestbook_entry where id=?", id), userId, false);
  }

  @DeleteMapping("/guestbook/{id}")
  @Transactional
  void remove(@PathVariable UUID id, HttpSession s) {
    var entry = one("select user_id from guestbook_entry where id=? for update", id);
    if (!entry.get("user_id").equals(actor(s))) throw denied();
    db.update("delete from guestbook_entry where id=?", id);
  }

  @GetMapping("/owner/guestbook")
  Map<String, Object> moderation(@RequestParam(defaultValue = "0") int page, HttpSession s) {
    owner(s);
    return list(page, s, true);
  }

  @PutMapping("/owner/guestbook/{id}")
  @Transactional
  void visibility(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpSession s) {
    owner(s);
    if (!(body.get("hidden") instanceof Boolean hidden))
      throw new IllegalArgumentException("Choose whether to hide this message.");
    if (db.update("update guestbook_entry set hidden=? where id=?", hidden, id) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found.");
  }

  private Map<String, Object> list(int page, HttpSession s, boolean ownerView) {
    if (page < 0 || page > 100000)
      throw new IllegalArgumentException("Choose a valid guestbook page.");
    String filter = ownerView ? "" : " where not g.hidden and not u.restricted";
    String from = " from guestbook_entry g join users u on u.id=g.user_id" + filter;
    UUID viewer = actor(s);
    var entries =
        db
            .queryForList(
                "select g.*" + from + " order by g.created_at desc,g.id desc limit ? offset ?",
                PAGE_SIZE,
                page * PAGE_SIZE)
            .stream()
            .map(row -> publicEntry(row, viewer, ownerView))
            .toList();
    return Map.of(
        "entries", entries,
        "total", db.queryForObject("select count(*)" + from, Integer.class),
        "page", page);
  }

  private Map<String, Object> publicEntry(Map<String, Object> row, UUID viewer, boolean ownerView) {
    var entry = new LinkedHashMap<String, Object>();
    entry.put("id", row.get("id"));
    entry.put("name", row.get("name"));
    entry.put("message", row.get("message"));
    entry.put("createdAt", ((java.sql.Timestamp) row.get("created_at")).toInstant().toString());
    entry.put("mine", row.get("user_id").equals(viewer));
    if (ownerView) entry.put("hidden", row.get("hidden"));
    return entry;
  }
}
