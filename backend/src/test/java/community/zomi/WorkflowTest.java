package community.zomi;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5433/zomi_test}",
      "app.demo=true"
    })
@AutoConfigureMockMvc
class WorkflowTest {
  @Autowired ContributionsController contributions;
  @Autowired LeaderboardController boards;
  @Autowired MockMvc mvc;
  @Autowired Api api;
  @Autowired org.springframework.web.context.WebApplicationContext context;

  @BeforeEach
  void setup() {
    mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  MockHttpSession member(boolean owner) {
    UUID id = UUID.randomUUID();
    api.db()
        .update(
            "insert into users(id,email,password,name,role,public_score) values(?,?,?,?,?,true)",
            id,
            id + "@example.test",
            api.passwords().encode("test-password-long"),
            "Member " + id.toString().substring(0, 6),
            owner ? "OWNER" : "MEMBER");
    var s = new MockHttpSession();
    s.setAttribute("uid", id);
    return s;
  }

  Map<String, String> answers(UUID attempt, boolean correct) {
    var row=api.one("select a.question_ids,e.content from attempt a join edition e on e.id=a.edition_id where a.id=?",attempt);
    var q=AttemptQuestions.restore(api.quiz((String)row.get("content")),(String)row.get("question_ids"),api.json());
    var a = new HashMap<String, String>();
    for (var x : q.questions())
      a.put(
          x.id(),
          x.options().stream()
              .filter(o -> Integer.valueOf(1).equals(o.correct()) == correct)
              .findFirst()
              .orElseThrow()
              .id());
    return a;
  }

  UUID start(MockHttpSession s, boolean ranked) throws Exception {
    var r =
        mvc.perform(
                post("/api/quizzes/proverbs-demo/start")
                    .session(s)
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"ranked\":" + ranked + ",\"displayName\":\"Test player\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString((String) api.decode(r.getResponse().getContentAsString()).get("id"));
  }

  void share(UUID id, MockHttpSession s) throws Exception {
    mvc.perform(put("/api/attempts/"+id+"/sharing").session(s).with(csrf()).contentType("application/json").content("{\"shared\":true}")).andExpect(status().isOk());
  }

  UUID edition(UUID a) {
    return (UUID) api.one("select edition_id from attempt where id=?", a).get("edition_id");
  }

  @Test
  void ownershipScoringRetriesAndCsrf() throws Exception {
    var guest = new MockHttpSession();
    UUID a = start(guest, false), eid = edition(a);
    mvc.perform(get("/api/attempts/" + a).session(new MockHttpSession()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/attempts/" + a).session(member(false))).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/attempts/" + a + "/complete")
                .session(guest)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    String body = api.encode(Map.of("answers", answers(a, true)));
    var invalidAnswers = new HashMap<>(answers(a, true));
    invalidAnswers.put(invalidAnswers.keySet().iterator().next(), "not-an-option");
    mvc.perform(
            post("/api/attempts/" + a + "/complete")
                .session(guest)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(Map.of("answers", invalidAnswers))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/attempts/" + a + "/complete")
                .session(guest)
                .with(csrf())
                .contentType("application/json")
                .content(body.substring(0, body.length() - 1) + ",\"score\":99}"))
        .andExpect(status().isBadRequest());
    String first =
        mvc.perform(
                post("/api/attempts/" + a + "/complete")
                    .session(guest)
                    .with(csrf())
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String retry =
        mvc.perform(
                post("/api/attempts/" + a + "/complete")
                    .session(guest)
                    .with(csrf())
                    .contentType("application/json")
                    .content(body))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(api.decode(first), api.decode(retry));
    mvc.perform(get("/api/owner").session(guest)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/owner").session(member(false))).andExpect(status().isForbidden());
  }

  @Test
  void boardConcurrencyBestScoresVisibilityAndRanks() throws Exception {
    var s = member(false);
    UUID a = start(s, true), eid = edition(a);
    String body = api.encode(Map.of("answers", answers(a, true)));
    final var concurrentSession = s;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<Callable<Integer>>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            () ->
                mvc.perform(
                        post("/api/attempts/" + a + "/complete")
                            .session(concurrentSession)
                            .with(csrf())
                            .contentType("application/json")
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus());
      for (var f : pool.invokeAll(tasks)) assertEquals(200, f.get());
    }
    share(a,s);
    UUID uid = (UUID) s.getAttribute("uid");
    assertEquals(
        1,
        api.db()
            .queryForObject(
                "select count(*) from leaderboard where edition_id=? and user_id=?",
                Integer.class,
                eid,
                uid));
    for (boolean high : List.of(true, false)) {
      UUID b = start(s, true);
      mvc.perform(
              post("/api/attempts/" + b + "/complete")
                  .session(s)
                  .with(csrf())
                  .contentType("application/json")
                  .content(api.encode(Map.of("answers", answers(b, high)))))
          .andExpect(status().isOk());
      share(b,s);
    }
    assertEquals(
        answers(a, true).size(),
        api.db()
            .queryForObject(
                "select score from leaderboard where edition_id=? and user_id=?",
                Integer.class,
                eid,
                uid));
    // Separate edition for deterministic competition ranks and outside-Top-10 query.
    var q =
        api.quiz((String) api.one("select content from edition where id=?", eid).get("content"));
    UUID boardEdition = api.publish(null, q);
    for (int i = 0; i < 12; i++) {
      var u = member(false);
      api.db()
          .update(
              "insert into attempt(id,edition_id,user_id,display_name,ranked,result,finished_at,shared) values(?,?,?,'Ranked fixture',false,? ,now(),true)",
              UUID.randomUUID(),
              boardEdition,
              u.getAttribute("uid"),
              api.encode(Map.of("type","KNOWLEDGE","score",i<2?15:15-i,"maximum",15)));
      if (i == 11) s = u;
    }
    var board = boards.board(boardEdition, 0, s);
    var entries = (List<Map<String, Object>>) board.get("entries");
    assertEquals(1L, entries.get(0).get("rank"));
    assertEquals(1L, entries.get(1).get("rank"));
    assertEquals(3L, entries.get(2).get("rank"));
    assertEquals(12L, ((List<Map<String, Object>>) board.get("mine")).getFirst().get("rank"));
    api.db().update("update users set restricted=true where id=?", s.getAttribute("uid"));
    assertTrue(((List<?>) boards.board(boardEdition, 0, s).get("mine")).isEmpty());
    mvc.perform(
            put("/api/account")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Private now\",\"publicScore\":false}"))
        .andExpect(status().isOk());
    assertTrue(((List<?>) boards.board(boardEdition, 0, s).get("mine")).isEmpty());
  }

  @Test
  void exactReviewWithdrawalNewEditionAndHistory() throws Exception {
    var s = member(false);
    var owner = member(true);
    UUID a = start(s, false), eid = edition(a);
    UUID qid = (UUID) api.one("select quiz_id from edition where id=?", eid).get("quiz_id");
    var q =
        api.quiz((String) api.one("select content from edition where id=?", eid).get("content"));
    var draft = new Api.Draft("QUIZ", qid, q);
    UUID sid = UUID.fromString(contributions.draft(draft, s).get("id").toString());
    mvc.perform(
            put("/api/submissions/" + sid)
                .session(member(false))
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(draft)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/submissions/" + sid + "/submit").session(s).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/submissions/" + sid)
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(draft)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/owner/submissions/" + sid + "/review")
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.Decision(99, "APPROVE", ""))))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/owner/submissions/" + sid + "/review")
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content(
                    api.encode(new Api.Decision(1, "CHANGES_REQUESTED", "Please add context."))))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/submissions/" + sid)
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(draft)))
        .andExpect(status().isOk());
    mvc.perform(post("/api/submissions/" + sid + "/submit").session(s).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(post("/api/submissions/" + sid + "/withdraw").session(s).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/owner/submissions/" + sid + "/review")
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.Decision(2, "APPROVE", ""))))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/submissions/" + sid)
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(draft)))
        .andExpect(status().isOk());
    mvc.perform(post("/api/submissions/" + sid + "/submit").session(s).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/owner/submissions/" + sid + "/review")
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.Decision(3, "APPROVE", ""))))
        .andExpect(status().isOk());
    assertNotEquals(
        eid, api.one("select current_edition from quiz where id=?", qid).get("current_edition"));
    assertEquals(eid, edition(a));
    mvc.perform(
            post("/api/attempts/" + a + "/complete")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(Map.of("answers", answers(a, true)))))
        .andExpect(status().isOk());
    assertEquals(
        answers(a, true).size(),
        api.decode((String) api.one("select result from attempt where id=?", a).get("result"))
            .get("score"));
  }

  @Test
  void passwordRecoverySingleUseAndPublicKeysHidden() throws Exception {
    var s = member(false);
    UUID id = (UUID) s.getAttribute("uid");
    String email = (String) api.one("select email from users where id=?", id).get("email");
    String message =
        mvc.perform(
                post("/api/recovery")
                    .with(csrf())
                    .contentType("application/json")
                    .content(api.encode(Map.of("email", email))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(message.contains("token"));
    String body =
        (String) api.one("select body from mail_outbox where recipient=?", email).get("body");
    String token = body.substring(body.indexOf("token=") + 6);
    String reset = api.encode(Map.of("token", token, "password", "changed-password-123"));
    mvc.perform(post("/api/reset").with(csrf()).contentType("application/json").content(reset))
        .andExpect(status().isOk());
    mvc.perform(post("/api/reset").with(csrf()).contentType("application/json").content(reset))
        .andExpect(status().isNotFound());
    String catalog =
        mvc.perform(get("/api/quizzes")).andReturn().getResponse().getContentAsString();
    assertFalse(catalog.contains("\"correct\":"));
    assertFalse(catalog.contains("\"weights\":"));
    assertFalse(catalog.contains(email));
  }

  @Test
  void questionBankPublicationAndFrozenResultDescriptions() throws Exception {
    var contributor = member(false);
    var ownerSession = member(true);
    UUID old = start(contributor, false), eid = edition(old);
    UUID qid = (UUID) api.one("select quiz_id from edition where id=?", eid).get("quiz_id");
    var collection =
        api.quiz((String) api.one("select content from edition where id=?", eid).get("content"));
    UUID isolatedEdition = api.publish(null, collection);
    qid = (UUID) api.one("select quiz_id from edition where id=?", isolatedEdition).get("quiz_id");
    var original = collection.questions().getFirst();
    var question =
        new Content.Question(
            UUID.randomUUID().toString(),
            original.text(),
            original.options(),
            original.explanation(),
            null,
            false,
            "Original test fixture",
            null,
            null);
    var single =
        new Content.Quiz(
            "A bank contribution",
            "Original English question",
            "Language & culture",
            "KNOWLEDGE",
            null,
            true,
            List.of(question),
            List.of(),
            List.of(),
            Map.of(),
            null,
            null,
            List.of("test"));
    UUID sid =
        UUID.fromString(
            contributions
                .draft(new Api.Draft("QUESTION", qid, single), contributor)
                .get("id")
                .toString());
    mvc.perform(post("/api/submissions/" + sid + "/submit").session(contributor).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/owner/submissions/" + sid + "/review")
                .session(ownerSession)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.Decision(1, "APPROVE", "Reviewed"))))
        .andExpect(status().isOk());
    UUID bank = (UUID) api.one("select id from question_bank where submission_id=?", sid).get("id");
    mvc.perform(get("/api/owner/bank/" + qid + "/preview?entries=" + bank).session(ownerSession))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/owner/bank/publish")
                .session(ownerSession)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(new Api.BankPublish(qid, List.of(bank), UUID.randomUUID()))))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/owner/bank/publish")
                .session(ownerSession)
                .with(csrf())
                .contentType("application/json")
                .content(
                    api.encode(
                        new Api.BankPublish(
                            qid,
                            List.of(bank),
                            (UUID)
                                api.one("select current_edition from quiz where id=?", qid)
                                    .get("current_edition")))))
        .andExpect(status().isOk());
    assertEquals(
        "PUBLISHED_IN_QUIZ",
        api.one("select status from submission where id=?", sid).get("status"));
    assertEquals(eid, edition(old));
    // A personality edition contains its descriptions; global profile edits cannot rewrite them.
    var pq = api.json().readValue(getClass().getResourceAsStream("/demo/community-rhythm.json"),Content.Quiz.class);
    UUID pe = api.publish(null, pq);
    String oldName = (String) pq.profiles().get("BBBB").get("name");
    var updated = new HashMap<>(pq.profiles().get("BBBB"));
    updated.put("name", "Changed after the attempt began");
    mvc.perform(
            put("/api/owner/profiles/BBBB")
                .session(ownerSession)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(updated)))
        .andExpect(status().isOk());
    var a = new HashMap<String, String>();
    pq.questions().forEach(x -> a.put(x.id(), x.options().get(2).id()));
    UUID aid = UUID.randomUUID();
    api.db()
        .update(
            "insert into attempt(id,edition_id,user_id,ranked,display_name) values(?,?,?,false,'Legacy player')",
            aid,
            pe,
            contributor.getAttribute("uid"));
    var r =
        mvc.perform(
                post("/api/attempts/" + aid + "/complete")
                    .session(contributor)
                    .with(csrf())
                    .contentType("application/json")
                    .content(api.encode(Map.of("answers", a))))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        oldName,
        ((Map<?, ?>) api.decode(r.getResponse().getContentAsString()).get("profile")).get("name"));
  }

  @Test
  void improvementModerationAndMalformedAnswerRecords() throws Exception {
    var session = member(false);
    UUID a = start(session, true), eid = edition(a);
    for (boolean high : List.of(false, true)) {
      UUID aid = high ? start(session, true) : a;
      mvc.perform(
              post("/api/attempts/" + aid + "/complete")
                  .session(session)
                  .with(csrf())
                  .contentType("application/json")
                  .content(api.encode(Map.of("answers", answers(aid, high)))))
          .andExpect(status().isOk());
      share(aid,session);
    }
    UUID uid = (UUID) session.getAttribute("uid");
    var entry =
        api.one("select id,score from leaderboard where user_id=? and edition_id=?", uid, eid);
    assertEquals(answers(a, true).size(), entry.get("score"));
    var os = member(true);
    mvc.perform(
            put("/api/owner/boards/" + entry.get("id"))
                .session(os)
                .with(csrf())
                .contentType("application/json")
                .content("{\"hidden\":true,\"reason\":\"Owner reviewed this entry\"}"))
        .andExpect(status().isOk());
    UUID retry = start(session, true);
    mvc.perform(
            post("/api/attempts/" + retry + "/complete")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(api.encode(Map.of("answers", answers(retry, true)))))
        .andExpect(status().isOk());
    share(retry,session);
    assertEquals(
        true, api.one("select hidden from leaderboard where id=?", entry.get("id")).get("hidden"));
    UUID malformed = start(session, false);
    mvc.perform(
            post("/api/attempts/" + malformed + "/complete")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{\"answers\":{\"k0\":\"o0\",\"k0\":\"o1\"}}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/attempts/" + malformed + "/complete")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{\"answers\":{}}"))
        .andExpect(status().isBadRequest());
    api.db().update("update users set restricted=true where id=?", uid);
    mvc.perform(
            post("/api/quizzes/proverbs-demo/start")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{\"ranked\":true}"))
        .andExpect(status().isOk());
    api.db()
        .update(
            "insert into reset_token values(?,?,now()-interval '1 minute') on conflict(token_hash)"
                + " do update set user_id=excluded.user_id,expires_at=excluded.expires_at",
            api.hash("expired-token"),
            uid);
    mvc.perform(
            post("/api/reset")
                .with(csrf())
                .contentType("application/json")
                .content("{\"token\":\"expired-token\",\"password\":\"a-new-password-long\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void smtpOutboxDeliversWithoutTokenApiExposure() throws Exception {
    api.db().update("delete from mail_outbox");
    UUID id = UUID.randomUUID();
    api.db()
        .update(
            "insert into mail_outbox(id,recipient,body) values(?,?,'Local recovery test message')",
            id,
            "test@example.test");
    var sender = (org.springframework.mail.javamail.JavaMailSenderImpl) api.mail();
    String host = sender.getHost();
    int port = sender.getPort();
    try (var server = new java.net.ServerSocket(0);
        var pool = Executors.newSingleThreadExecutor()) {
      server.setSoTimeout(10000);
      var received =
          pool.submit(
              () -> {
                try (var socket = server.accept()) {
                  socket.setSoTimeout(10000);
                  var in =
                      new java.io.BufferedReader(
                          new java.io.InputStreamReader(socket.getInputStream()));
                  var out = new java.io.PrintWriter(socket.getOutputStream(), true);
                  out.print("220 localhost test SMTP\r\n");
                  out.flush();
                  StringBuilder data = new StringBuilder();
                  boolean collecting = false;
                  String line;
                  while ((line = in.readLine()) != null) {
                    if (collecting) {
                      if (line.equals(".")) {
                        collecting = false;
                        out.print("250 queued\r\n");
                        out.flush();
                      } else data.append(line).append('\n');
                    } else if (line.startsWith("DATA")) {
                      collecting = true;
                      out.print("354 end with dot\r\n");
                      out.flush();
                    } else if (line.startsWith("QUIT")) {
                      out.print("221 bye\r\n");
                      out.flush();
                      break;
                    } else {
                      out.print("250 OK\r\n");
                      out.flush();
                    }
                  }
                  return data.toString();
                }
              });
      sender.setHost("127.0.0.1");
      sender.setPort(server.getLocalPort());
      new MailOutbox(api).deliver();
      assertTrue(received.get(15, TimeUnit.SECONDS).contains("Local recovery test message"));
      assertEquals(
          0,
          api.db()
              .queryForObject("select count(*) from mail_outbox where id=?", Integer.class, id));
    } finally {
      sender.setHost(host);
      sender.setPort(port);
    }
  }
}
