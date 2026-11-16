package community.zomi;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScoringTest {
  final ObjectMapper json = new ObjectMapper();

  Content.Quiz load(String slug) throws Exception {
    return json.readValue(
        getClass().getResourceAsStream("/demo/" + slug + ".json"), Content.Quiz.class);
  }

  @Test
  void knowledgeAndInvalidAnswers() throws Exception {
    var q = load("proverbs-demo");
    Content.validate(q);
    Map<String, String> a = new HashMap<>();
    q.questions()
        .forEach(
            x ->
                a.put(
                    x.id(),
                    x.options().stream()
                        .filter(o -> Integer.valueOf(1).equals(o.correct()))
                        .findFirst()
                        .orElseThrow()
                        .id()));
    assertEquals(q.questions().size(), Content.score(q, a).get("score"));
    a.put(q.questions().getFirst().id(), "forged");
    assertThrows(IllegalArgumentException.class, () -> Content.score(q, a));
    a.clear();
    assertThrows(IllegalArgumentException.class, () -> Content.score(q, a));
  }

  @Test
  void outcomeTieUsesStoredOrder() throws Exception {
    var q = load("community-character");
    var x = q.questions().getFirst();
    var zero =
        x.options().stream()
            .map(o -> new Content.Option(o.id(), o.text(), null, Map.<String, Integer>of()))
            .toList();
    var tie =
        new Content.Quiz(
            q.title(),
            q.description(),
            q.category(),
            q.type(),
            null,
            true,
            List.of(new Content.Question("tie", "Tie", zero, null, null, false, null, null, null)),
            q.outcomes(),
            q.tieOrder().reversed(),
            q.profiles(),
            q.titleEn(),
            q.descriptionEn(),
            q.tags());
    Content.validate(tie);
    assertEquals(
        q.tieOrder().getLast(),
        ((Content.Outcome) Content.score(tie, Map.of("tie", zero.getFirst().id())).get("outcome"))
            .id());
  }

  @Test
  void all16ProfilesReverseScoringAndMidpoint() throws Exception {
    var q = load("community-rhythm");
    Content.validate(q);
    for (int mask = 0; mask < 16; mask++) {
      Map<String, String> a = new HashMap<>();
      String code = "";
      for (int t = 0; t < 4; t++) code += (mask & (1 << t)) != 0 ? "A" : "B";
      for (var x : q.questions()) {
        boolean high = (mask & (1 << x.trait())) != 0;
        int value = high ? 5 : 1;
        if (x.reverse()) value = 6 - value;
        a.put(x.id(), x.options().get(value - 1).id());
      }
      var r = Content.score(q, a);
      assertEquals(code, r.get("code"));
    }
    Map<String, String> mid = new HashMap<>();
    q.questions().forEach(x -> mid.put(x.id(), x.options().get(2).id()));
    var r = Content.score(q, mid);
    assertEquals("BBBB", r.get("code"));
    for (Object t : (List<?>) r.get("traits")) {
      assertEquals(50d, ((Map<?, ?>) t).get("value"));
      assertEquals(true, ((Map<?, ?>) t).get("balanced"));
    }
  }
}
