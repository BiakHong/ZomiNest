package community.zomi;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class Bootstrap implements CommandLineRunner {
  static final List<String> DEMO_SLUGS =
      List.of("proverbs-demo", "community-character", Personality.SLUG);
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Bootstrap.class);
  final Api api;

  @Value("${app.demo}")
  boolean demo;

  @Value("${app.demo-refresh:}")
  String refresh;

  @Value("${app.owner-email}")
  String email;

  @Value("${app.owner-password}")
  String password;

  Bootstrap(Api api) {
    this.api = api;
  }

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    if (!refresh.isBlank() && (!demo || !DEMO_SLUGS.contains(refresh)))
      throw new IllegalArgumentException(
          "Demo refresh requires DEMO_CONTENT=true and a source name: "
              + String.join(", ", DEMO_SLUGS));
    if (!email.isBlank() && !password.isBlank()) {
      if (password.length() < 12)
        throw new IllegalArgumentException(
            "Bootstrap owner password must contain at least 12 characters.");
      if (api.db()
              .queryForObject(
                  "select count(*) from users where email=?",
                  Integer.class,
                  email.toLowerCase(Locale.ROOT))
          == 0)
        api.db()
            .update(
                "insert into users(id,email,password,name,role) values(?,?,?,'Community"
                    + " owner','OWNER')",
                UUID.randomUUID(),
                email.toLowerCase(Locale.ROOT),
                api.passwords().encode(password));
    }
    // Upgrade installed sample quizzes even when demo seeding has since been disabled.
    if (demo || api.db().queryForObject("select count(*) from quiz where slug='community-rhythm'", Integer.class)>0)
      installPersonality();
    if (!demo) {
      installProverbsRounds();
      return;
    }
    for (String slug : DEMO_SLUGS) {
      String source =
          new ClassPathResource("demo/" + slug + ".json")
              .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
      if (api.db().queryForObject("select count(*) from quiz where slug=?", Integer.class, slug)
          == 0) {
        UUID qid = UUID.randomUUID();
        api.db().update("insert into quiz(id,slug,featured) values(?,?,true)", qid, slug);
        api.publish(qid, api.quiz(source));
      } else if (slug.equals(refresh)) {
        refreshSource(slug, api.quiz(source));
      }
    }
    installProverbsRounds();
    var profiles =
        api.json().readTree(new ClassPathResource("demo/profiles.json").getInputStream());
    for (var p : profiles)
      api.db()
          .update(
              "insert into profile(code,content) values(?,?) on conflict(code) do nothing",
              p.get("code").asText(),
              p.toString());
  }

  private void installPersonality() throws Exception {
    if (api.db().queryForObject("select count(*) from quiz where slug=?",Integer.class,Personality.SLUG)>0) return;
    var source=new ClassPathResource("demo/"+Personality.SLUG+".json")
        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    UUID id=UUID.randomUUID();
    api.db().update("insert into quiz(id,slug,featured) values(?,?,true)",id,Personality.SLUG);
    api.publish(id,api.quiz(source));
    // Keep all earlier editions and attempts intact, but replace the sample in discovery.
    api.db().update("update quiz set featured=false where slug='community-rhythm'");
  }

  /** Publish only the round setting, preserving the installed question bank and old attempts. */
  private void installProverbsRounds() {
    var rows=api.db().queryForList("select q.id,q.published,e.content from quiz q join edition e on e.id=q.current_edition where q.slug='proverbs-demo' for update of q");
    if (rows.isEmpty()) return;
    var row=rows.getFirst();
    var content=api.quiz((String)row.get("content"));
    if (!content.type().equals("KNOWLEDGE") || content.questionsPerAttempt()!=null) return;
    api.publish((UUID)row.get("id"),content.withQuestionsPerAttempt(Math.min(10,content.questions().size())));
    if (!Boolean.TRUE.equals(row.get("published")))
      api.db().update("update quiz set published=false where id=?",row.get("id"));
  }

  /** Explicit development import; published editions and their results remain immutable. */
  @Transactional
  public boolean refreshSource(String slug, Content.Quiz source) {
    Content.validate(source);
    var quiz =
        api.one("select id,current_edition,published from quiz where slug=? for update", slug);
    var previous = api.one("select content from edition where id=?", quiz.get("current_edition"));
    if (source.equals(api.quiz((String) previous.get("content")))) {
      log.info(
          "Development source {} already matches the current edition; no changes needed.", slug);
      return false;
    }
    api.publish((UUID) quiz.get("id"), source);
    // Refresh content without undoing an owner's decision to unpublish the collection.
    if (!Boolean.TRUE.equals(quiz.get("published")))
      api.db().update("update quiz set published=false where id=?", quiz.get("id"));
    log.info(
        "Imported updated development source {} as a new edition. Earlier results are preserved.",
        slug);
    return true;
  }
}
