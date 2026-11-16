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

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5433/zomi_test}","app.demo=true"})
@AutoConfigureMockMvc
class SharedResultsTest {
  @Autowired Api api;
  @Autowired WebApplicationContext context;
  MockMvc mvc;
  @BeforeEach void setup() {
    mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
  }
  Map<String,Object> start(MockHttpSession s,String slug,String name) throws Exception {
    var response=mvc.perform(post("/api/quizzes/"+slug+"/start").session(s).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("displayName",name,"gender","nonbinary")))).andExpect(status().isOk()).andReturn();
    return api.decode(response.getResponse().getContentAsString());
  }
  void share(MockHttpSession s,Object id,boolean value) throws Exception {
    mvc.perform(put("/api/attempts/"+id+"/sharing").session(s).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("shared",value)))).andExpect(status().isOk());
  }
  Map<String,Object> community(Object edition,String filter) throws Exception {
    return api.decode(mvc.perform(get("/api/editions/"+edition+"/community"+filter)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  Map<String,Object> complete(MockHttpSession s,Map<String,Object> a,Map<String,?> answers) throws Exception {
    return api.decode(mvc.perform(post("/api/attempts/"+a.get("id")+"/complete").session(s).with(csrf()).contentType("application/json")
        .content(api.encode(Map.of("answers",answers)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  String fixture(String source) throws Exception {
    Content.Quiz q=api.json().readValue(getClass().getResourceAsStream("/demo/"+source+".json"),Content.Quiz.class);
    UUID edition=api.publish(null,q);
    return (String)api.one("select q.slug from quiz q join edition e on e.quiz_id=q.id where e.id=?",edition).get("slug");
  }
  Map<String,Object> answers(Map<String,Object> a) {
    var row=api.one("select a.question_ids,e.content from attempt a join edition e on e.id=a.edition_id where a.id=?",UUID.fromString(a.get("id").toString()));
    var content=AttemptQuestions.restore(api.quiz((String)row.get("content")),(String)row.get("question_ids"),api.json());
    var answers=new HashMap<String,Object>();
    for(var q:content.questions()) answers.put(q.id(),q.options().getFirst().id());
    return answers;
  }
  List<?> entries(Object edition) throws Exception { return (List<?>)community(edition,"").get("entries"); }

  @Test void everyTypeAllowsGuestPlayAndRejectsInvalidSuppliedNames() throws Exception {
    for(String slug:List.of("proverbs-demo","community-character",Personality.SLUG)) {
      var s=new MockHttpSession();
      for(String name:List.of("a".repeat(61),"<script>alert(1)</script>","Name\nInjection"))
        mvc.perform(post("/api/quizzes/"+slug+"/start").session(s).with(csrf()).contentType("application/json")
            .content(api.encode(Map.of("displayName",name)))).andExpect(status().isBadRequest());
      for(String body:List.of("{}", "{\"displayName\":\"   \"}")) {
        var response=mvc.perform(post("/api/quizzes/"+slug+"/start").session(s).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn();
        var guest=api.decode(response.getResponse().getContentAsString());
        assertEquals("Guest",guest.get("displayName")); assertEquals("undisclosed",guest.get("gender")); assertEquals(false,guest.get("shared"));
      }
      var a=start(s,slug,"မင်း Zoë O’Neil-Suan");
      assertEquals("မင်း Zoë O’Neil-Suan",a.get("displayName")); assertEquals(false,a.get("shared"));
    }
  }

  @Test void completionIsPrivateEvenWithOldRankedAndAccountOptIns() throws Exception {
    var s=new MockHttpSession(); var a=start(s,fixture("proverbs-demo"),"Private named visitor");
    UUID id=UUID.fromString(a.get("id").toString());
    api.db().update("update attempt set ranked=true where id=?",id);
    api.db().update("update users set public_score=true where id=?",s.getAttribute("visitorId"));
    var r=complete(s,a,answers(a)); assertEquals("Private named visitor",r.get("displayName"));
    api.db().update("insert into leaderboard(id,edition_id,user_id,score,maximum) values(?,?,?,?,?)",UUID.randomUUID(),UUID.fromString(a.get("edition").toString()),s.getAttribute("visitorId"),r.get("score"),r.get("maximum"));
    assertTrue(entries(a.get("edition")).isEmpty());
    assertEquals(false,api.one("select shared from attempt where id=?",id).get("shared"));
    assertTrue(((List<?>)api.decode(mvc.perform(get("/api/boards/"+a.get("edition"))).andReturn().getResponse().getContentAsString()).get("entries")).isEmpty());
  }

  @Test void explicitSharingCrossVisitorVisibilityMinimalPublicPayloadAndWithdrawal() throws Exception {
    var s=new MockHttpSession(); var a=start(s,fixture("proverbs-demo"),"Amina O’Neil");
    complete(s,a,answers(a)); share(s,a.get("id"),true);
    var publicResult=entries(a.get("edition")); assertEquals(1,publicResult.size());
    var entry=(Map<?,?>)publicResult.getFirst(); assertEquals("Amina O’Neil",entry.get("name"));
    assertEquals(Set.of("id","name","type","code","score","maximum","outcome"),entry.keySet());
    String publicJson=api.encode(publicResult);
    for(String forbidden:List.of("answers","gender","user_id","guest_key","explanations","edition_id","email","password")) assertFalse(publicJson.contains(forbidden));
    assertFalse(publicJson.contains(a.get("id").toString()));
    mvc.perform(get("/api/attempts/"+a.get("id")).session(new MockHttpSession())).andExpect(status().isForbidden());
    mvc.perform(put("/api/attempts/"+a.get("id")+"/sharing").session(new MockHttpSession()).with(csrf()).contentType("application/json").content("{\"shared\":false}"))
        .andExpect(status().isForbidden());
    share(s,a.get("id"),false); assertTrue(entries(a.get("edition")).isEmpty());
    assertTrue(((List<?>)api.decode(mvc.perform(get("/api/boards/"+a.get("edition"))).andReturn().getResponse().getContentAsString()).get("entries")).isEmpty());
  }

  @Test void duplicateDisplayNamesHaveSeparateOwnersAndEntries() throws Exception {
    String slug=fixture("community-character");
    var first=new MockHttpSession(); var second=new MockHttpSession();
    var a=start(first,slug,"Same Name"); var b=start(second,slug,"Same Name");
    complete(first,a,answers(a)); complete(second,b,answers(b)); share(first,a.get("id"),true); share(second,b.get("id"),true);
    var entries=entries(a.get("edition")); assertEquals(2,entries.size());
    assertNotEquals(((Map<?,?>)entries.get(0)).get("id"),((Map<?,?>)entries.get(1)).get("id"));
    mvc.perform(put("/api/attempts/"+a.get("id")+"/sharing").session(second).with(csrf()).contentType("application/json").content("{\"shared\":false}"))
        .andExpect(status().isForbidden());
    share(first,a.get("id"),false); assertEquals(1,entries(a.get("edition")).size());
  }

  @Test void nameNeutralAnswersAndPageSurviveSaveResumeAndAccountRename() throws Exception {
    var s=new MockHttpSession(); var a=start(s,Personality.SLUG,"Biak’s Name");
    mvc.perform(put("/api/attempts/"+a.get("id")).session(s).with(csrf()).contentType("application/json")
        .content("{\"answers\":{\"q01\":0,\"q06\":3},\"page\":1}")).andExpect(status().isOk());
    mvc.perform(put("/api/account").session(s).with(csrf()).contentType("application/json").content("{\"name\":\"Different later name\"}"))
        .andExpect(status().isOk());
    var resumed=api.decode(mvc.perform(get("/api/attempts/"+a.get("id")).session(s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals("Biak’s Name",resumed.get("displayName")); assertEquals("nonbinary",resumed.get("gender"));
    assertEquals(1,resumed.get("page")); assertEquals(0,((Map<?,?>)resumed.get("answers")).get("q01"));
    var r=complete(s,a,new PersonalityScoringTest().neutral());
    assertEquals("XXXX-X",r.get("code")); assertEquals("Biak’s Name",r.get("displayName"));
    assertEquals("en",r.get("language")); assertNotNull(r.get("completedAt")); assertEquals(Personality.VERSION,r.get("questionnaireVersion"));
  }

  @Test void matchingIgnoresSuffixExcludesMainTiesAndSeparatesEditions() throws Exception {
    String slug=fixture(Personality.SLUG);
    var scorer=new PersonalityScoringTest(); List<Map<String,Object>> attempts=new ArrayList<>();
    for(int n=0;n<4;n++) {
      var s=new MockHttpSession(); var a=start(s,slug,"Person "+n); attempts.add(a);
      var answers=scorer.pattern(n==2?-1:1);
      if(n==1) { var confidence=Personality.DIMENSIONS.get(4); for(int i:confidence.plus()) answers.put(Personality.id(i),-3); for(int i:confidence.minus()) answers.put(Personality.id(i),3); }
      if(n==3) answers=scorer.neutral();
      complete(s,a,answers); share(s,a.get("id"),true);
    }
    var edition=attempts.getFirst().get("edition");
    assertEquals(4,entries(edition).size());
    var matches=(List<?>)community(edition,"?personality=ENTJ").get("entries"); assertEquals(2,matches.size());
    var codes=matches.stream().map(e->((Map<?,?>)e).get("code")).toList(); assertTrue(codes.containsAll(List.of("ENTJ-A","ENTJ-T")));
    mvc.perform(get("/api/editions/"+edition+"/community?personality=XXXX")).andExpect(status().isBadRequest());
    var other=start(new MockHttpSession(),fixture(Personality.SLUG),"Other edition"); assertTrue(entries(other.get("edition")).isEmpty());
    assertEquals(4,entries(edition).size());
  }

  @Test void incompleteInvalidCsrfAndRestrictedPublishingAreRejected() throws Exception {
    var s=new MockHttpSession(); var a=start(s,Personality.SLUG,"Tester");
    mvc.perform(put("/api/attempts/"+a.get("id")+"/sharing").session(s).with(csrf()).contentType("application/json").content("{\"shared\":true}"))
        .andExpect(status().isBadRequest());
    for(String answers:List.of("{}","{\"q01\":\"0\"}","{\"q01\":0.5}","{\"q01\":4}","{\"q99\":0}"))
      mvc.perform(post("/api/attempts/"+a.get("id")+"/complete").session(s).with(csrf()).contentType("application/json").content("{\"answers\":"+answers+"}"))
          .andExpect(status().isBadRequest());
    complete(s,a,new PersonalityScoringTest().neutral());
    mvc.perform(put("/api/attempts/"+a.get("id")+"/sharing").session(s).contentType("application/json").content("{\"shared\":true}"))
        .andExpect(status().isForbidden());
    api.db().update("update users set restricted=true where id=?",s.getAttribute("visitorId"));
    mvc.perform(put("/api/attempts/"+a.get("id")+"/sharing").session(s).with(csrf()).contentType("application/json").content("{\"shared\":true}"))
        .andExpect(status().isForbidden());
    share(s,a.get("id"),false);
  }
}
