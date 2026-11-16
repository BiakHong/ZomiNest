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
public class ProfilesController extends Api {
  ProfilesController(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    super(d, j, p, m, e);
  }

  @GetMapping("/profiles")
  List<Map<String, Object>> profiles() {
    return db.queryForList("select code,content from profile order by code").stream()
        .map(
            r -> {
              var m = decode((String) r.get("content"));
              m.put("code", r.get("code"));
              return m;
            })
        .toList();
  }

  @GetMapping("/profiles/{code}")
  Map<String, Object> profile(@PathVariable String code) {
    return decode((String) one("select content from profile where code=?", code).get("content"));
  }

  @PutMapping("/owner/profiles/{code}")
  void profileEdit(@PathVariable String code, @RequestBody Map<String, Object> b, HttpSession s) {
    owner(s);
    for (String k :
        List.of(
            "name",
            "overview",
            "strengths",
            "difficulties",
            "communication",
            "relationships",
            "work")) text(b, k, 6000);
    if (!code.matches("[AB]{4}"))
      throw new IllegalArgumentException("Use a four-letter A/B profile code.");
    db.update(
        "insert into profile(code,content) values(?,?) on conflict(code) do update set"
            + " content=excluded.content",
        code,
        encode(b));
  }
}
