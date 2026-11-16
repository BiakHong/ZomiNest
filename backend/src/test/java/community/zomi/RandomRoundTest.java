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

@SpringBootTest(properties = {"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5433/zomi_test}", "app.demo=true"})
@AutoConfigureMockMvc
class RandomRoundTest {
  @Autowired Api api;
  @Autowired WebApplicationContext context;
  MockMvc mvc;
  @BeforeEach void setup() {
    mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
  }
  Map<String,Object> start(MockHttpSession session, String slug) throws Exception {
    return api.decode(mvc.perform(post("/api/quizzes/"+slug+"/start").session(session).with(csrf())
        .contentType("application/json").content("{}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  Map<String,Object> attempt(MockHttpSession session,Object id) throws Exception {
    return api.decode(mvc.perform(get("/api/attempts/"+id).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  Content.Quiz selected(Object id) {
    var row=api.one("select a.question_ids,e.content from attempt a join edition e on e.id=a.edition_id where a.id=?",UUID.fromString(id.toString()));
    return AttemptQuestions.restore(api.quiz((String)row.get("content")),(String)row.get("question_ids"),api.json());
  }
  Map<String,String> correct(Content.Quiz quiz) {
    var answers=new HashMap<String,String>();
    quiz.questions().forEach(q->answers.put(q.id(),q.options().stream().filter(o->Integer.valueOf(1).equals(o.correct())).findFirst().orElseThrow().id()));
    return answers;
  }
  Map<String,Object> complete(MockHttpSession session,Object id,Map<String,String> answers) throws Exception {
    return api.decode(mvc.perform(post("/api/attempts/"+id+"/complete").session(session).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("answers",answers)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  @Test void savesTheDrawAndResumesItsOrderAndProgress() throws Exception {
    var session=new MockHttpSession(); var first=start(session,"proverbs-demo"); var id=first.get("id");
    var quiz=selected(id); assertEquals(10,quiz.questions().size());
    assertEquals(first.get("quiz"),attempt(session,id).get("quiz"));
    assertNotNull(api.one("select question_ids from attempt where id=?",UUID.fromString(id.toString())).get("question_ids"));
    mvc.perform(put("/api/attempts/"+id).session(session).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("answers",Map.of(quiz.questions().getFirst().id(),quiz.questions().getFirst().options().getFirst().id()),"page",4))))
        .andExpect(status().isOk());
    var resumed=attempt(session,id); assertEquals(4,resumed.get("page"));
    assertEquals(first.get("quiz"),resumed.get("quiz")); assertEquals(1,((Map<?,?>)resumed.get("answers")).size());
    mvc.perform(put("/api/attempts/"+id).session(session).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("answers",Map.of(),"page",10)))).andExpect(status().isBadRequest());
  }

  @Test void scoresOnlyTenAndRejectsUnselectedOrIncompleteAnswers() throws Exception {
    var session=new MockHttpSession(); var a=start(session,"proverbs-demo"); var id=a.get("id"); var quiz=selected(id);
    var bank=api.quiz((String)api.one("select content from edition where id=?",UUID.fromString(a.get("edition").toString())).get("content"));
    var answers=correct(quiz); var incomplete=new HashMap<>(answers); incomplete.remove(quiz.questions().getFirst().id());
    mvc.perform(post("/api/attempts/"+id+"/complete").session(session).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("answers",incomplete)))).andExpect(status().isBadRequest());
    var outside=bank.questions().stream().filter(q->!answers.containsKey(q.id())).findFirst().orElseThrow();
    var forged=new HashMap<>(incomplete); forged.put(outside.id(),outside.options().getFirst().id());
    mvc.perform(put("/api/attempts/"+id).session(session).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("answers",forged)))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/attempts/"+id+"/complete").session(session).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("answers",forged)))).andExpect(status().isBadRequest());
    var result=complete(session,id,answers); assertEquals(10,result.get("score")); assertEquals(10,result.get("maximum"));
    assertEquals(10,((List<?>)result.get("explanations")).size());
    assertEquals(result,complete(session,id,answers));
    assertEquals(false,attempt(session,id).get("shared"));
  }

  @Test void oldFullAttemptsKeepTheirEditionAndCannotMixWithTenQuestionBoards() throws Exception {
    var session=new MockHttpSession(); UUID user=api.participant(session);
    var source=api.json().readValue(getClass().getResourceAsStream("/demo/proverbs-demo.json"),Content.Quiz.class);
    var full=source.withQuestionsPerAttempt(null); UUID oldEdition=api.publish(null,full);
    var collection=api.one("select q.id,q.slug from quiz q join edition e on e.quiz_id=q.id where e.id=?",oldEdition);
    UUID legacy=UUID.randomUUID();
    api.db().update("insert into attempt(id,edition_id,user_id,ranked,display_name) values(?,?,?,false,'Earlier full quiz')",legacy,oldEdition,user);
    UUID newEdition=api.publish((UUID)collection.get("id"),source);
    assertEquals(full.questions().size(),selected(legacy).questions().size());
    var previous=complete(session,legacy,correct(full)); assertEquals(full.questions().size(),previous.get("maximum"));
    var round=start(session,(String)collection.get("slug")); assertEquals(newEdition.toString(),round.get("edition"));
    var result=complete(session,round.get("id"),correct(selected(round.get("id")))); assertEquals(10,result.get("maximum"));
    for(Object id:List.of(legacy,round.get("id")))
      mvc.perform(put("/api/attempts/"+id+"/sharing").session(session).with(csrf()).contentType("application/json").content("{\"shared\":true}"))
          .andExpect(status().isOk());
    var board=api.decode(mvc.perform(get("/api/boards/"+newEdition)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals(1,((List<?>)board.get("entries")).size());
    assertEquals(10,((Map<?,?>)((List<?>)board.get("entries")).getFirst()).get("maximum"));
    var community=api.decode(mvc.perform(get("/api/editions/"+newEdition+"/community")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertFalse(api.encode(community).contains("Earlier full quiz"));
    assertEquals(oldEdition,api.one("select edition_id from attempt where id=?",legacy).get("edition_id"));
  }
}
