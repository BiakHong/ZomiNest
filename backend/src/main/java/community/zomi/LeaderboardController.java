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
public class LeaderboardController extends Api {
  LeaderboardController(
      JdbcTemplate d,
      ObjectMapper j,
      PasswordEncoder p,
      JavaMailSender m,
      community.zomi.catalog.EditionRepository e) {
    super(d, j, p, m, e);
  }

  @GetMapping("/boards/{edition}")
  Map<String, Object> board(
      @PathVariable UUID edition, @RequestParam(defaultValue = "0") int page, HttpSession s) {
    one(
        "select e.id from edition e join quiz q on q.id=e.quiz_id where e.id=? and"
            + " q.published=true",
        edition);
    String sql =
        "select b.*,rank() over(order by score desc) rank from (select distinct on (a.user_id)"
            + " a.public_id id,a.user_id,a.display_name name,(a.result::jsonb->>'score')::int score,"
            + " (a.result::jsonb->>'maximum')::int maximum,a.finished_at achieved_at from attempt a"
            + " join users u on u.id=a.user_id where a.edition_id=? and a.shared=true"
            + " and a.finished_at is not null and a.result::jsonb->>'type'='KNOWLEDGE' and not u.restricted"
            + " and not exists(select 1 from leaderboard l where l.edition_id=a.edition_id"
            + " and l.user_id=a.user_id and l.hidden) order by a.user_id,score desc,a.finished_at,a.id) b";
    var rows =
        db.queryForList(
            "select name,score,maximum,rank from ("
                + sql
                + ") b order by score desc,achieved_at,id limit 10 offset ?",
            edition,
            Math.max(0, page) * 10);
    var mine =
        actor(s) == null
            ? List.of()
            : db.queryForList(
                "select name,score,maximum,rank from (" + sql + ") b where user_id=?",
                edition,
                actor(s));
    return Map.of(
        "entries",
        rows,
        "mine",
        mine,
        "page",
        page,
        "total",
        db.queryForObject("select count(*) from (" + sql + ") b", Integer.class, edition));
  }

  @PutMapping("/owner/boards/{id}")
  void hide(@PathVariable UUID id, @RequestBody Map<String, Object> b, HttpSession s) {
    owner(s);
    db.update(
        "update leaderboard set hidden=?,reason=? where id=?",
        Boolean.TRUE.equals(b.get("hidden")),
        text(b, "reason", 1000),
        id);
  }
}
