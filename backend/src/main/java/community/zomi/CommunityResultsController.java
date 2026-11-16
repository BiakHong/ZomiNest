package community.zomi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class CommunityResultsController extends Api {
  CommunityResultsController(JdbcTemplate d,ObjectMapper j,PasswordEncoder p,JavaMailSender m,
      community.zomi.catalog.EditionRepository e) { super(d,j,p,m,e); }

  @GetMapping("/editions/{edition}/community")
  Map<String,Object> community(@PathVariable UUID edition,
      @RequestParam(defaultValue="") String personality,@RequestParam(defaultValue="0") int page) {
    var e=one("select e.content from edition e join quiz q on q.id=e.quiz_id where e.id=? and q.published=true",edition);
    var quiz=quiz((String)e.get("content"));
    if (!personality.isEmpty() && (!Personality.isCurrent(quiz) || !personality.matches("[EI][NS][TF][JP]")))
      throw new IllegalArgumentException("Matching requires four resolved personality letters.");
    int safePage=Math.max(0,Math.min(page,100000));
    String from=" from attempt a join users u on u.id=a.user_id where a.edition_id=? and a.shared=true and a.finished_at is not null and not u.restricted and not exists(select 1 from leaderboard l where l.edition_id=a.edition_id and l.user_id=a.user_id and l.hidden)";
    List<Object> params=new ArrayList<>(List.of(edition));
    if (!personality.isEmpty()) {
      from+=" and a.result::jsonb->>'questionnaireVersion'=? and left(a.result::jsonb->>'code',4)=?";
      params.add(Personality.VERSION); params.add(personality);
    }
    long total=db.queryForObject("select count(*)"+from,Long.class,params.toArray());
    List<Object> paged=new ArrayList<>(params); paged.add(safePage*20);
    // Deliberate public projection: no owner IDs, gender, answers or full result objects.
    var rows=db.queryForList("select a.public_id id,a.display_name name,a.result::jsonb->>'type' type,a.result::jsonb->>'code' code,a.result::jsonb->>'score' score,a.result::jsonb->>'maximum' maximum,a.result::jsonb->'outcome'->>'name' outcome"+from+" order by a.finished_at desc,a.public_id limit 20 offset ?",paged.toArray());
    return Map.of("entries",rows,"total",total,"page",safePage);
  }
}
