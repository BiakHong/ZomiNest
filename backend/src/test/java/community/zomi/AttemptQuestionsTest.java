package community.zomi;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class AttemptQuestionsTest {
  final ObjectMapper json = new ObjectMapper();
  Content.Quiz bank() throws Exception {
    return json.readValue(getClass().getResourceAsStream("/demo/proverbs-demo.json"), Content.Quiz.class);
  }

  @Test void drawsTenUniqueQuestionsAndUsesRandomness() throws Exception {
    var bank = bank(); Content.validate(bank);
    var first = AttemptQuestions.draw(bank, new Random(21));
    var second = AttemptQuestions.draw(bank, new Random(22));
    assertEquals(10, first.size()); assertEquals(10, new HashSet<>(first).size());
    assertTrue(bank.questions().stream().map(Content.Question::id).toList().containsAll(first));
    assertNotEquals(first, second);
    assertEquals(first, AttemptQuestions.draw(bank, new Random(21)));
  }

  @Test void restoresExactOrderWithoutChangingTheBankOrOtherQuizTypes() throws Exception {
    var bank = bank(); var draw = AttemptQuestions.draw(bank, new Random(7));
    String stored = json.writeValueAsString(draw);
    var restored = AttemptQuestions.restore(bank, stored, json);
    assertEquals(draw, restored.questions().stream().map(Content.Question::id).toList());
    assertEquals(restored, AttemptQuestions.restore(bank, stored, json));
    assertTrue(bank.questions().size() > restored.questions().size());
    assertEquals(bank.credit(), restored.credit());
    assertSame(bank, AttemptQuestions.restore(bank, null, json));
    assertNull(AttemptQuestions.draw(bank.withQuestionsPerAttempt(null)));
    var personality = json.readValue(getClass().getResourceAsStream("/demo/discover-personality.json"), Content.Quiz.class);
    assertNull(AttemptQuestions.draw(personality));
    assertEquals(60, AttemptQuestions.restore(personality, null, json).questions().size());
  }

  @Test void rejectsBrokenDrawsAndInvalidRoundSizes() throws Exception {
    var bank = bank(); var draw = new ArrayList<>(AttemptQuestions.draw(bank));
    draw.set(0, draw.get(1));
    assertThrows(IllegalStateException.class, () -> AttemptQuestions.restore(bank, json.writeValueAsString(draw), json));
    draw.set(0, "not-a-question");
    assertThrows(IllegalStateException.class, () -> AttemptQuestions.restore(bank, json.writeValueAsString(draw), json));
    assertThrows(IllegalStateException.class, () -> AttemptQuestions.restore(bank, "[]", json));
    assertThrows(IllegalStateException.class, () -> AttemptQuestions.restore(bank, "not json", json));
    assertThrows(IllegalArgumentException.class, () -> Content.validate(bank.withQuestionsPerAttempt(0)));
    assertThrows(IllegalArgumentException.class, () -> Content.validate(bank.withQuestionsPerAttempt(bank.questions().size() + 1)));
  }
}
