package community.zomi;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class PersonalityScoringTest {
  Content.Quiz quiz() throws Exception {
    return new ObjectMapper().readValue(getClass().getResourceAsStream("/demo/discover-personality.json"),Content.Quiz.class);
  }
  Map<String,Object> neutral() {
    var a=new LinkedHashMap<String,Object>();
    for(int n=1;n<=60;n++) a.put(Personality.id(n),0);
    return a;
  }
  Map<String,Object> pattern(int sign) {
    var a=neutral();
    for(var d:Personality.DIMENSIONS) {
      d.plus().forEach(n->a.put(Personality.id(n),3*sign));
      d.minus().forEach(n->a.put(Personality.id(n),-3*sign));
    }
    return a;
  }
  @SuppressWarnings("unchecked")
  List<Map<String,Object>> dimensions(Map<String,Object> result) { return (List<Map<String,Object>>)result.get("dimensions"); }

  @Test void all60StableIdsAndExactKeyCoverage() throws Exception {
    var q=quiz(); Content.validate(q);
    assertEquals(60,q.questions().size());
    var assigned=new HashSet<Integer>();
    for(var d:Personality.DIMENSIONS) {
      assertEquals(12,d.plus().size()+d.minus().size());
      for(int n:d.plus()) assertTrue(assigned.add(n));
      for(int n:d.minus()) assertTrue(assigned.add(n));
    }
    assertEquals(60,assigned.size());
    for(int n=1;n<=60;n++) {
      assertTrue(assigned.contains(n)); assertEquals(Personality.id(n),q.questions().get(n-1).id());
      assertEquals(7,q.questions().get(n-1).options().size());
    }
    assertEquals("You regularly make new friends.",q.questions().getFirst().text());
    assertEquals("Even a small mistake can cause you to doubt your overall abilities and knowledge.",q.questions().getLast().text());
  }
  @Test void allNeutralMeansBalancedWithoutFallbackLetters() throws Exception {
    var r=Content.score(quiz(),neutral()); assertEquals("XXXX-X",r.get("code"));
    assertEquals(true,r.get("balanced")); assertEquals(false,r.get("mainResolved"));
    for(var d:dimensions(r)) { assertEquals(0,d.get("raw")); assertEquals(50d,d.get("positivePercentage")); assertEquals(50d,d.get("negativePercentage")); assertEquals("Balanced",d.get("strength")); }
    assertFalse(r.get("description").toString().contains("Your answers suggest you "));
  }
  @Test void exactExtremesOnEveryPole() throws Exception {
    for(int sign:List.of(1,-1)) {
      var r=Content.score(quiz(),pattern(sign));
      assertEquals(sign==1?"ENTJ-A":"ISFP-T",r.get("code"));
      for(var d:dimensions(r)) {
        assertEquals(36*sign,d.get("raw")); assertEquals(sign==1?100d:0d,d.get("positivePercentage"));
        assertEquals(sign==1?0d:100d,d.get("negativePercentage"));
      }
    }
  }
  @Test void raw18Gives75AndReverseItemSixContributesMinusThree() throws Exception {
    var a=neutral(); for(int n:Personality.DIMENSIONS.getFirst().plus().subList(0,6)) a.put(Personality.id(n),3);
    var d=dimensions(Content.score(quiz(),a)).getFirst();
    assertEquals(18,d.get("raw")); assertEquals(75d,d.get("positivePercentage")); assertEquals(25d,d.get("negativePercentage"));
    a=neutral(); a.put("q06",3);
    assertEquals(-3,dimensions(Content.score(quiz(),a)).getFirst().get("raw"));
  }
  @Test void independentDimensionsAndOrderDoesNotAffectScoring() throws Exception {
    var q=quiz(); var a=neutral(); a.put("q06",3);
    var result=Content.score(q,a);
    for(var d:dimensions(result).subList(1,5)) assertEquals(0,d.get("raw"));
    var reversed=new Content.Quiz(q.title(),q.description(),q.category(),q.type(),q.credit(),q.demo(),q.questions().reversed(),q.outcomes(),q.tieOrder(),q.profiles(),q.titleEn(),q.descriptionEn(),q.tags(),q.questionnaireVersion());
    Content.validate(reversed); assertEquals(result,Content.score(reversed,a));
    var reversedAnswers=new LinkedHashMap<String,Object>();
    new ArrayList<>(a.keySet()).reversed().forEach(key->reversedAnswers.put(key,a.get(key))); assertEquals(result,Content.score(q,reversedAnswers));
  }
  @Test void exactTiesAndCloseMidpointUseUnroundedRawValues() throws Exception {
    var a=pattern(1);
    for(int n:Personality.DIMENSIONS.getFirst().plus()) a.put(Personality.id(n),0);
    for(int n:Personality.DIMENSIONS.getFirst().minus()) a.put(Personality.id(n),0);
    var r=Content.score(quiz(),a); assertEquals("XNTJ-A",r.get("code")); assertEquals(false,r.get("mainResolved"));
    a.put("q01",1); var d=dimensions(Content.score(quiz(),a)).getFirst();
    assertEquals("E",d.get("letter")); assertEquals("Slight preference",d.get("strength")); assertEquals(true,d.get("closeToMidpoint"));
    assertEquals(100L,(Long)d.get("displayPositive")+(Long)d.get("displayNegative"));
  }
  @Test void invalidMissingUnknownFractionalAndStringAnswersAreRejected() throws Exception {
    var q=quiz(); var missing=neutral(); missing.remove("q01");
    assertThrows(IllegalArgumentException.class,()->Content.score(q,missing));
    for(Object bad:Arrays.asList(null,4,-4,1.5,"0",true,0.0)) {
      var a=neutral(); a.put("q01",bad); assertThrows(IllegalArgumentException.class,()->Content.score(q,a));
    }
    var a=neutral(); a.remove("q01"); a.put("old-sample-id",0);
    assertThrows(IllegalArgumentException.class,()->Content.score(q,a));
    assertThrows(IllegalArgumentException.class,()->Personality.validateAnswers(q,Map.of("q99",0),false));
    assertDoesNotThrow(()->Personality.validateAnswers(q,Map.of("q01",0),false));
  }
  @Test void oldSampleAnswersCannotBeUsedForNewQuestionnaire() throws Exception {
    var old=new ObjectMapper().readValue(getClass().getResourceAsStream("/demo/community-rhythm.json"),Content.Quiz.class);
    assertNotEquals(old.questionnaireVersion(),quiz().questionnaireVersion());
    var a=new HashMap<String,Object>(); old.questions().forEach(q->a.put(q.id(),q.options().get(2).id()));
    assertThrows(IllegalArgumentException.class,()->Content.score(quiz(),a));
    assertFalse(Personality.isCurrent(old)); Content.validate(old);
  }
}
