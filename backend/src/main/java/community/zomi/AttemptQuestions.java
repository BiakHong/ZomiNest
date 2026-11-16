package community.zomi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** A draw belongs to the attempt, never to a page load or to the submitted answers. */
final class AttemptQuestions {
  static List<String> draw(Content.Quiz quiz) {
    return draw(quiz, ThreadLocalRandom.current());
  }

  static List<String> draw(Content.Quiz quiz, Random random) {
    if (quiz.questionsPerAttempt() == null) return null;
    var ids = new ArrayList<>(quiz.questions().stream().map(Content.Question::id).toList());
    Collections.shuffle(ids, random);
    return List.copyOf(ids.subList(0, quiz.questionsPerAttempt()));
  }

  static Content.Quiz restore(Content.Quiz bank, String storedIds, ObjectMapper json) {
    // Earlier attempts have no draw and retain their complete original edition.
    if (storedIds == null) return bank;
    final List<String> ids;
    try {
      ids = json.readValue(storedIds, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("This attempt's saved questions could not be read.", e);
    }
    if (bank.questionsPerAttempt() == null || ids == null
        || ids.size() != bank.questionsPerAttempt() || new HashSet<>(ids).size() != ids.size())
      throw new IllegalStateException("This attempt's saved question draw is invalid.");
    var byId = new HashMap<String, Content.Question>();
    bank.questions().forEach(question -> byId.put(question.id(), question));
    var selected = new ArrayList<Content.Question>();
    for (String id : ids) {
      var question = byId.get(id);
      if (question == null) throw new IllegalStateException("A saved question is absent from this edition.");
      selected.add(question);
    }
    return bank.withQuestions(List.copyOf(selected));
  }
}
