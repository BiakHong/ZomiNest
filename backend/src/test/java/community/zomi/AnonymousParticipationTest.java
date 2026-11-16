package community.zomi;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5433/zomi_test}",
      "app.demo=true"
    })
@AutoConfigureMockMvc
class AnonymousParticipationTest {
  @Autowired WebApplicationContext context;
  @Autowired Api api;
  @Autowired ContributionsController contributions;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  UUID identity(MockHttpSession s) throws Exception {
    mvc.perform(get("/api/session").session(s)).andExpect(status().isOk());
    assertNull(s.getAttribute("uid"));
    return (UUID) s.getAttribute("visitorId");
  }

  UUID start(MockHttpSession s, boolean ranked) throws Exception {
    var r =
        mvc.perform(
                post("/api/quizzes/proverbs-demo/start")
                    .session(s)
                    .with(csrf())
                    .contentType("application/json")
                    .content(api.encode(Map.of("ranked", ranked, "displayName", "Open player"))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString((String) api.decode(r.getResponse().getContentAsString()).get("id"));
  }

  Content.Quiz content() {
    return api.quiz(
        (String)
            api.one(
                    "select e.content from edition e join quiz q on q.current_edition=e.id where"
                        + " q.slug='proverbs-demo'")
                .get("content"));
  }

  @Test
  void nicknameBoardPrivateResultsWithdrawalAndRestrictionWithoutRegistration() throws Exception {
    var s = new MockHttpSession();
    UUID id = identity(s);
    var row = api.one("select email,password,visitor from users where id=?", id);
    assertNull(row.get("email"));
    assertNull(row.get("password"));
    assertEquals(true, row.get("visitor"));
    assertEquals(id, identity(s));
    mvc.perform(
            put("/api/account")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Open player\",\"publicScore\":true}"))
        .andExpect(status().isOk());
    UUID aid = start(s, true);
    var answers = new HashMap<String, String>();
    AttemptQuestions.restore(content(),(String)api.one("select question_ids from attempt where id=?",aid).get("question_ids"),api.json())
        .questions()
        .forEach(
            q ->
                answers.put(
                    q.id(),
                    q.options().stream()
                        .filter(o -> Integer.valueOf(1).equals(o.correct()))
                        .findFirst()
                        .orElseThrow()
                        .id()));
    mvc.perform(
            post("/api/attempts/" + aid + "/complete")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(Map.of("answers", answers))))
        .andExpect(status().isOk());
    mvc.perform(put("/api/attempts/"+aid+"/sharing").session(s).with(csrf()).contentType("application/json").content("{\"shared\":true}")).andExpect(status().isOk());
    assertEquals(
        1,
        api.db()
            .queryForObject("select count(*) from leaderboard where user_id=?", Integer.class, id));
    String history =
        mvc.perform(get("/api/results").session(s))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(history.contains(aid.toString()));
    var stranger = new MockHttpSession();
    mvc.perform(get("/api/attempts/" + aid).session(stranger)).andExpect(status().isForbidden());
    assertEquals(
        "[]",
        mvc.perform(get("/api/results").session(stranger))
            .andReturn()
            .getResponse()
            .getContentAsString());
    mvc.perform(
            put("/api/account")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Open player\",\"publicScore\":false}"))
        .andExpect(status().isOk());
    UUID eid = (UUID) api.one("select edition_id from attempt where id=?", aid).get("edition_id");
    assertFalse(
        mvc.perform(get("/api/boards/" + eid).session(s))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .contains("Open player"));
    api.db().update("update users set public_score=true,restricted=true where id=?", id);
    mvc.perform(
            post("/api/quizzes/proverbs-demo/start")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content("{\"ranked\":true}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/owner").session(s)).andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousDraftRevisionApprovalAndIsolation() throws Exception {
    var s = new MockHttpSession();
    identity(s);
    UUID sid =
        UUID.fromString(
            contributions.draft(new Api.Draft("QUIZ", null, content()), s).get("id").toString());
    mvc.perform(post("/api/submissions/" + sid + "/submit").session(s).with(csrf()))
        .andExpect(status().isOk());
    var other = new MockHttpSession();
    assertEquals(
        "[]",
        mvc.perform(get("/api/submissions").session(other))
            .andReturn()
            .getResponse()
            .getContentAsString());
    mvc.perform(post("/api/submissions/" + sid + "/withdraw").session(other).with(csrf()))
        .andExpect(status().isForbidden());
    var fresh = new MockHttpSession();
    mvc.perform(post("/api/submissions/" + sid + "/withdraw").session(fresh).with(csrf()))
        .andExpect(status().isForbidden());
    identity(fresh);
    UUID owner = UUID.randomUUID();
    api.db()
        .update(
            "insert into users(id,email,password,name,role) values(?,?,?,'Owner','OWNER')",
            owner,
            owner + "@example.test",
            api.passwords().encode("a-long-test-password"));
    var os = new MockHttpSession();
    os.setAttribute("uid", owner);
    mvc.perform(
            post("/api/owner/submissions/" + sid + "/review")
                .session(os)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.Decision(1, "CHANGES_REQUESTED", "Add context"))))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/submissions/" + sid)
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.Draft("QUIZ", null, content()))))
        .andExpect(status().isOk());
    mvc.perform(post("/api/submissions/" + sid + "/submit").session(s).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/owner/submissions/" + sid + "/review")
                .session(os)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.Decision(2, "APPROVE", "Reviewed"))))
        .andExpect(status().isOk());
    assertEquals(
        "PUBLISHED", api.one("select status from submission where id=?", sid).get("status"));
  }

  @Test
  void signingIntoDifferentIdentityDoesNotExposeVisitorDraftsOrAttempts() throws Exception {
    var s = new MockHttpSession();
    identity(s);
    UUID attempt = start(s, false);
    contributions.draft(new Api.Draft("QUIZ", null, content()), s);
    UUID account = UUID.randomUUID();
    String email = account + "@example.test";
    api.db()
        .update(
            "insert into users(id,email,password,name) values(?,?,?,'Separate account')",
            account,
            email,
            api.passwords().encode("a-long-test-password"));
    mvc.perform(
            post("/api/login")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(Map.of("email", email, "password", "a-long-test-password"))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/attempts/" + attempt).session(s)).andExpect(status().isForbidden());
    assertEquals(
        "[]",
        mvc.perform(get("/api/submissions").session(s))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
