package community.zomi;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
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
class GuestbookTest {
  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  @Autowired Api api;
  final List<UUID> users = new ArrayList<>();
  final String address = "guestbook-test-" + UUID.randomUUID();

  @BeforeEach
  void setup() {
    mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  MockHttpSession visitor() throws Exception {
    var session = new MockHttpSession();
    mvc.perform(get("/api/session").session(session)).andExpect(status().isOk());
    assertNull(session.getAttribute("uid"));
    users.add((UUID) session.getAttribute("visitorId"));
    return session;
  }

  MockHttpSession account(String role) {
    UUID id = UUID.randomUUID();
    users.add(id);
    api.db()
        .update(
            "insert into users(id,email,password,name,role)"
                + " values(?,?,'unused-in-test','Moderator',?)",
            id,
            id + "@example.test",
            role);
    var session = new MockHttpSession();
    session.setAttribute("uid", id);
    return session;
  }

  ResultActions sign(MockHttpSession session, String name, String message) throws Exception {
    return mvc.perform(
        post("/api/guestbook")
            .session(session)
            .with(csrf())
            .with(
                request -> {
                  request.setRemoteAddr(address);
                  return request;
                })
            .contentType("application/json")
            .content(api.encode(Map.of("name", name, "message", message))));
  }

  Map<String, Object> json(ResultActions response) throws Exception {
    return api.decode(response.andReturn().getResponse().getContentAsString());
  }

  List<?> entries(MockHttpSession session) throws Exception {
    return (List<?>)
        json(mvc.perform(get("/api/guestbook").session(session)).andExpect(status().isOk()))
            .get("entries");
  }

  @AfterEach
  void cleanup() {
    for (UUID id : users) {
      api.db().update("delete from guestbook_entry where user_id=?", id);
      api.db().update("delete from users where id=?", id);
    }
  }

  @Test
  void visitorPostPersistsAndPublicPayloadExcludesIdentity() throws Exception {
    var author = visitor();
    var entry =
        json(
            sign(author, "  A visitor  ", "Hello, community!\nGlad to be here.")
                .andExpect(status().isOk()));
    assertEquals(Set.of("id", "name", "message", "createdAt", "mine"), entry.keySet());
    assertEquals("A visitor", entry.get("name"));
    assertEquals(true, entry.get("mine"));
    Instant.parse((String) entry.get("createdAt"));
    assertEquals(
        "Hello, community!\nGlad to be here.",
        api.one(
                "select message from guestbook_entry where id=?",
                UUID.fromString((String) entry.get("id")))
            .get("message"));
    Map<?, ?> publicEntry = (Map<?, ?>) entries(new MockHttpSession()).getFirst();
    assertEquals(entry.get("id"), publicEntry.get("id"));
    assertEquals(false, publicEntry.get("mine"));
    assertEquals(entry.keySet(), publicEntry.keySet());
    assertNull(author.getAttribute("uid"));
    assertEquals(true, ((Map<?, ?>) entries(author).getFirst()).get("mine"));
  }

  @Test
  void csrfValidationAndRestrictionAreEnforced() throws Exception {
    var author = visitor();
    mvc.perform(
            post("/api/guestbook")
                .session(author)
                .contentType("application/json")
                .content("{\"name\":\"A visitor\",\"message\":\"Hello\"}"))
        .andExpect(status().isForbidden());
    sign(author, " ", "Hello").andExpect(status().isBadRequest());
    sign(author, "A visitor", "\n ").andExpect(status().isBadRequest());
    sign(author, "a".repeat(61), "Hello").andExpect(status().isBadRequest());
    sign(author, "A visitor", "a".repeat(1001)).andExpect(status().isBadRequest());
    assertEquals(
        0,
        api.db()
            .queryForObject(
                "select count(*) from guestbook_entry where user_id=?",
                Integer.class,
                author.getAttribute("visitorId")));
    api.db()
        .update("update users set restricted=true where id=?", author.getAttribute("visitorId"));
    sign(author, "A visitor", "Hello").andExpect(status().isForbidden());
  }

  @Test
  void visitorPostingIsRateLimited() throws Exception {
    var author = visitor();
    for (int i = 0; i < 3; i++) sign(author, "A visitor", "Hello " + i).andExpect(status().isOk());
    sign(author, "A visitor", "One too many").andExpect(status().isTooManyRequests());
  }

  @Test
  void onlyAuthorCanRemoveAndOnlyOwnerCanHideOrRestore() throws Exception {
    var author = visitor();
    var stranger = visitor();
    var member = account("MEMBER");
    var owner = account("OWNER");
    String id = (String) json(sign(author, "A visitor", "An original greeting")).get("id");
    mvc.perform(delete("/api/guestbook/" + id).session(stranger).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/owner/guestbook").session(stranger)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/owner/guestbook").session(member)).andExpect(status().isForbidden());
    mvc.perform(
            put("/api/owner/guestbook/" + id)
                .session(stranger)
                .with(csrf())
                .contentType("application/json")
                .content("{\"hidden\":true}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/owner/guestbook/" + id)
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content("{\"hidden\":\"true\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/owner/guestbook/" + id)
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content("{\"hidden\":true}"))
        .andExpect(status().isOk());
    assertTrue(entries(stranger).stream().noneMatch(row -> ((Map<?, ?>) row).get("id").equals(id)));
    mvc.perform(get("/api/owner/guestbook").session(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[0].hidden").value(true));
    mvc.perform(
            put("/api/owner/guestbook/" + id)
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content("{\"hidden\":false}"))
        .andExpect(status().isOk());
    assertEquals(id, ((Map<?, ?>) entries(stranger).getFirst()).get("id"));
    api.db()
        .update("update users set restricted=true where id=?", author.getAttribute("visitorId"));
    assertTrue(entries(stranger).stream().noneMatch(row -> ((Map<?, ?>) row).get("id").equals(id)));
    mvc.perform(delete("/api/guestbook/" + id).session(author).with(csrf()))
        .andExpect(status().isOk());
    assertEquals(
        0,
        api.db()
            .queryForObject(
                "select count(*) from guestbook_entry where id=?",
                Integer.class,
                UUID.fromString(id)));
  }

  @Test
  void newestFirstPaginationIsBoundedAndStable() throws Exception {
    var author = visitor();
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 13; i++) {
      UUID id = UUID.randomUUID();
      ids.add(id);
      api.db()
          .update(
              "insert into guestbook_entry(id,user_id,name,message,created_at)"
                  + " values(?,?,'Pagination visitor',?,?)",
              id,
              author.getAttribute("visitorId"),
              "Message " + i,
              Timestamp.from(Instant.now().plusSeconds(60 + i)));
    }
    var first = json(mvc.perform(get("/api/guestbook")).andExpect(status().isOk()));
    var firstEntries = (List<?>) first.get("entries");
    assertEquals(10, firstEntries.size());
    assertEquals(ids.getLast().toString(), ((Map<?, ?>) firstEntries.getFirst()).get("id"));
    var second = json(mvc.perform(get("/api/guestbook?page=1")).andExpect(status().isOk()));
    assertEquals(
        ids.get(2).toString(),
        ((Map<?, ?>) ((List<?>) second.get("entries")).getFirst()).get("id"));
    assertTrue(((Number) first.get("total")).intValue() >= 13);
    mvc.perform(get("/api/guestbook?page=-1")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/guestbook?page=100001")).andExpect(status().isBadRequest());
  }
}
