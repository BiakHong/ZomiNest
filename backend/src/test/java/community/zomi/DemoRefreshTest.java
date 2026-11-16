package community.zomi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5433/zomi_test}",
      "app.demo=true"
    })
class DemoRefreshTest {
  @Autowired Api api;
  @Autowired Bootstrap bootstrap;
  final List<UUID> quizzes = new ArrayList<>();
  final List<UUID> users = new ArrayList<>();

  Content.Quiz source(String wording) {
    return api.quiz(
        api.encode(
            Map.of(
                "title", "Refresh fixture",
                "description", "An isolated development import test.",
                "category", "Tests",
                "type", "KNOWLEDGE",
                "demo", true,
                "questions",
                    List.of(
                        Map.of(
                            "id",
                            "question",
                            "text",
                            wording,
                            "explanation",
                            "An original test explanation.",
                            "options",
                            List.of(
                                Map.of("id", "a", "text", "The correct choice", "correct", 1),
                                Map.of("id", "b", "text", "Another choice", "correct", 0)))))));
  }

  Map<String, Object> fixture() {
    UUID edition = api.publish(null, source("Original wording"));
    var quiz =
        api.one("select q.* from quiz q join edition e on e.quiz_id=q.id where e.id=?", edition);
    quizzes.add((UUID) quiz.get("id"));
    return quiz;
  }

  @AfterEach
  void cleanup() {
    for (UUID id : quizzes) {
      api.db()
          .update(
              "delete from leaderboard where edition_id in (select id from edition where"
                  + " quiz_id=?)",
              id);
      api.db()
          .update(
              "delete from attempt where edition_id in (select id from edition where quiz_id=?)",
              id);
      api.db().update("update quiz set current_edition=null where id=?", id);
      api.db().update("delete from edition where quiz_id=?", id);
      api.db().update("delete from quiz where id=?", id);
    }
    for (UUID id : users) api.db().update("delete from users where id=?", id);
  }

  @Test
  void newEditionPreservesEarlierContentResultsAndScores() {
    var quiz = fixture();
    UUID previous = (UUID) quiz.get("current_edition");
    String original =
        (String) api.one("select content from edition where id=?", previous).get("content");
    UUID user = UUID.randomUUID(), attempt = UUID.randomUUID();
    users.add(user);
    api.db()
        .update(
            "insert into users(id,name,visitor,public_score) values(?,'Refresh visitor',true,true)",
            user);
    String result = api.encode(Content.score(source("Original wording"), Map.of("question", "a")));
    api.db()
        .update(
            "insert into attempt(id,edition_id,user_id,ranked,display_name,answers,result,finished_at)"
                + " values(?,?,?,true,'Refresh visitor',?,?,now())",
            attempt,
            previous,
            user,
            "{\"question\":\"a\"}",
            result);
    api.db()
        .update(
            "insert into leaderboard(id,edition_id,user_id,score,maximum) values(?,?,?,1,1)",
            UUID.randomUUID(),
            previous,
            user);
    assertTrue(bootstrap.refreshSource((String) quiz.get("slug"), source("Updated wording")));
    UUID current =
        (UUID)
            api.one("select current_edition from quiz where id=?", quiz.get("id"))
                .get("current_edition");
    assertNotEquals(previous, current);
    var updated = api.one("select number,content from edition where id=?", current);
    assertEquals(2, updated.get("number"));
    assertEquals(
        "Updated wording", api.quiz((String) updated.get("content")).questions().getFirst().text());
    assertEquals(
        original, api.one("select content from edition where id=?", previous).get("content"));
    var stored = api.one("select edition_id,result from attempt where id=?", attempt);
    assertEquals(previous, stored.get("edition_id"));
    assertEquals(result, stored.get("result"));
    assertEquals(
        1,
        api.one("select score from leaderboard where edition_id=? and user_id=?", previous, user)
            .get("score"));
  }

  @Test
  void unchangedSourceDoesNotCreateDuplicateEditions() {
    var quiz = fixture();
    assertFalse(bootstrap.refreshSource((String) quiz.get("slug"), source("Original wording")));
    assertTrue(bootstrap.refreshSource((String) quiz.get("slug"), source("Updated wording")));
    assertFalse(bootstrap.refreshSource((String) quiz.get("slug"), source("Updated wording")));
    assertEquals(
        2,
        api.db()
            .queryForObject(
                "select count(*) from edition where quiz_id=?", Integer.class, quiz.get("id")));
  }

  @Test
  void invalidSourceCannotChangeTheCurrentEdition() {
    var quiz = fixture();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            bootstrap.refreshSource(
                (String) quiz.get("slug"), api.quiz("{\"title\":\"Incomplete\"}")));
    assertEquals(
        quiz.get("current_edition"),
        api.one("select current_edition from quiz where id=?", quiz.get("id"))
            .get("current_edition"));
    assertEquals(
        1,
        api.db()
            .queryForObject(
                "select count(*) from edition where quiz_id=?", Integer.class, quiz.get("id")));
  }

  @Test
  void refreshPreservesPublicationControlsAndOtherCollections() {
    var target = fixture();
    var other = fixture();
    api.db().update("update quiz set published=false,featured=true where id=?", target.get("id"));
    assertTrue(bootstrap.refreshSource((String) target.get("slug"), source("Updated wording")));
    var flags = api.one("select published,featured from quiz where id=?", target.get("id"));
    assertEquals(false, flags.get("published"));
    assertEquals(true, flags.get("featured"));
    assertEquals(
        other.get("current_edition"),
        api.one("select current_edition from quiz where id=?", other.get("id"))
            .get("current_edition"));
  }

  @Test
  void refreshNeedsDemoModeAndAnExplicitSupportedSource() {
    var runner = new Bootstrap(api);
    runner.refresh = "proverbs-demo";
    runner.demo = false;
    assertThrows(IllegalArgumentException.class, runner::run);
    runner.demo = true;
    runner.refresh = "unrelated-collection";
    assertThrows(IllegalArgumentException.class, runner::run);
  }
}
