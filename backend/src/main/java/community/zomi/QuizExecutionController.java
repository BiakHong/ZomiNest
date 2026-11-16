package community.zomi;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class QuizExecutionController extends Api {
  QuizExecutionController(JdbcTemplate d, ObjectMapper j, PasswordEncoder p, JavaMailSender m,
      community.zomi.catalog.EditionRepository e) { super(d,j,p,m,e); }

  static String displayName(Object input) {
    if (input == null) return "Guest";
    if (!(input instanceof String value)) throw new IllegalArgumentException("Enter a display name or leave it blank.");
    String name=value.strip();
    if (name.isEmpty()) return "Guest";
    if (name.length()>60 || !name.matches("[\\p{L}\\p{M}\\p{N} .,'’‘ʼʻ\\-‐‑–·]+")
        || name.codePoints().noneMatch(Character::isLetterOrDigit))
      throw new IllegalArgumentException("Enter a name of 1–60 characters, using letters, spaces or normal name punctuation.");
    return name;
  }

  @PostMapping("/quizzes/{slug}/start")
  @Transactional
  Map<String,Object> start(@PathVariable String slug, @RequestBody Map<String,Object> b, HttpSession s) {
    if (slug.equals("community-rhythm")) slug=Personality.SLUG;
    limit("start:"+s.getId(),30);
    String name=displayName(b.get("displayName"));
    String gender=Objects.toString(b.get("gender"),"undisclosed");
    if (!Set.of("woman","man","nonbinary","self-described","undisclosed").contains(gender))
      throw new IllegalArgumentException("Choose a gender option or Prefer not to say.");
    String language=Objects.toString(b.get("language"),"en");
    if (!Set.of("en","zom").contains(language)) throw new IllegalArgumentException("Unsupported language.");
    var q=one("select e.* from edition e join quiz q on q.current_edition=e.id where q.slug=? and q.published=true",slug);
    var selected=AttemptQuestions.draw(quiz((String)q.get("content")));
    UUID participant=participant(s), id=UUID.randomUUID();
    db.update("insert into attempt(id,edition_id,user_id,ranked,display_name,gender,language,question_ids) values(?,?,?,false,?,?,?,?)",
        id,q.get("id"),participant,name,gender,language,selected == null ? null : encode(selected));
    metric("starts");
    return attempt(id,s);
  }

  @GetMapping("/attempts/{id}")
  Map<String,Object> attempt(@PathVariable UUID id, HttpSession s) {
    var a=owned(id,s,false);
    var e=one("select content,quiz_id from edition where id=?",a.get("edition_id"));
    var m=new LinkedHashMap<String,Object>();
    m.put("id",id); m.put("edition",a.get("edition_id")); m.put("ranked",false);
    m.put("displayName",a.get("display_name")); m.put("gender",a.get("gender"));
    m.put("language",a.get("language")); m.put("page",a.get("current_page"));
    m.put("shared",a.get("shared")); m.put("avatar",a.get("public_id"));
    var bank=quiz((String)e.get("content"));
    var publicQuiz=Content.publicQuiz(AttemptQuestions.restore(bank,(String)a.get("question_ids"),json));
    publicQuiz.put("questionBankSize",bank.questions().size());
    m.put("quiz",publicQuiz);
    m.put("slug",one("select slug from quiz where id=?",e.get("quiz_id")).get("slug"));
    m.put("answers",a.get("answers")==null?Map.of():decode((String)a.get("answers")));
    m.put("result",a.get("result")==null?null:decode((String)a.get("result")));
    return m;
  }

  @PutMapping("/attempts/{id}")
  @Transactional
  void save(@PathVariable UUID id, @RequestBody Answers b, HttpSession s) {
    var a=owned(id,s,true);
    if (a.get("result")!=null) return;
    var q=attemptQuiz(a);
    validPartial(q,b.answers());
    int page=b.page()==null?(Integer)a.get("current_page"):b.page();
    int pages=Personality.isCurrent(q)?12:q.questions().size();
    if (page<0 || page>=pages) throw new IllegalArgumentException("Invalid page.");
    db.update("update attempt set answers=?,current_page=? where id=?",encode(b.answers()),page,id);
  }

  @PostMapping("/attempts/{id}/complete")
  @Transactional
  Map<String,Object> complete(@PathVariable UUID id, @RequestBody Answers b, HttpSession s) {
    var a=owned(id,s,true);
    if (a.get("result")!=null) return decode((String)a.get("result"));
    var q=attemptQuiz(a);
    var result=Content.score(q,b.answers());
    if (q.type().equals("PERSONALITY") && !Personality.isCurrent(q) && q.profiles()!=null)
      result.put("profile",q.profiles().get((String)result.get("code")));
    String completedAt=Instant.now().toString();
    result.put("displayName",a.get("display_name")); result.put("language",a.get("language"));
    result.put("edition",a.get("edition_id")); result.put("completedAt",completedAt);
    db.update("update attempt set answers=?,result=?,finished_at=?::timestamptz where id=?",
        encode(b.answers()),encode(result),completedAt,id);
    // Completion never grants public consent, including attempts started by older clients.
    metric("completions");
    return result;
  }

  public record Sharing(Boolean shared) {}

  private Content.Quiz attemptQuiz(Map<String,Object> attempt) {
    var bank=quiz(editions.findById((UUID)attempt.get("edition_id")).orElseThrow().content());
    return AttemptQuestions.restore(bank,(String)attempt.get("question_ids"),json);
  }

  @PutMapping("/attempts/{id}/sharing")
  @Transactional
  Map<String,Object> sharing(@PathVariable UUID id,@RequestBody Sharing body,HttpSession s) {
    var a=owned(id,s,true);
    if (body.shared()==null) throw new IllegalArgumentException("Choose whether to share this result.");
    if (a.get("result")==null) throw new IllegalArgumentException("Finish this quiz before sharing a result.");
    UUID ownerId=(UUID)a.get("user_id");
    if (ownerId==null) {
      ownerId=participant(s);
      db.update("update attempt set user_id=? where id=?",ownerId,id);
    }
    if (body.shared() && Boolean.TRUE.equals(user(ownerId).get("restricted")))
      throw denied();
    if (body.shared())
      one("select e.id from edition e join quiz q on q.id=e.quiz_id where e.id=? and q.published=true",a.get("edition_id"));
    db.update("update attempt set shared=? where id=?",body.shared(),id);
    var result=decode((String)a.get("result"));
    if (body.shared() && "KNOWLEDGE".equals(result.get("type"))) {
      // Retain the existing owner's board moderation controls. Public reads use consented attempts.
      db.update("insert into leaderboard(id,edition_id,user_id,score,maximum) values(?,?,?,?,?) on conflict(edition_id,user_id) do update set score=greatest(leaderboard.score,excluded.score)",
          UUID.randomUUID(),a.get("edition_id"),ownerId,result.get("score"),result.get("maximum"));
    }
    return Map.of("shared",body.shared());
  }

  @GetMapping("/results")
  List<Map<String,Object>> results(HttpSession s) {
    return db.queryForList("select a.id,a.edition_id,a.display_name,a.shared,a.public_id avatar,a.result,a.finished_at,a.started_at,q.slug,e.content::jsonb->>'title' title from attempt a join edition e on a.edition_id=e.id join quiz q on q.id=e.quiz_id where a.user_id=? order by a.started_at desc",participant(s))
        .stream().map(r->{if(r.get("result")!=null) r.put("result",decode((String)r.get("result")));return r;}).toList();
  }
}
