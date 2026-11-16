package community.zomi;

import java.util.*;

public class Content {
  public record Option(String id, String text, Integer correct, Map<String, Integer> weights) {}

  public record Question(
      String id,
      String text,
      List<Option> options,
      String explanation,
      Integer trait,
      boolean reverse,
      String source,
      String explanationEn,
      String credit) {}

  public record Outcome(String id, String name, String description) {}

  public record Quiz(
      String title,
      String description,
      String category,
      String type,
      String credit,
      boolean demo,
      List<Question> questions,
      List<Outcome> outcomes,
      List<String> tieOrder,
      Map<String, Map<String, Object>> profiles,
      String titleEn,
      String descriptionEn,
      List<String> tags,
      String questionnaireVersion,
      Integer questionsPerAttempt) {
    public Quiz(String title, String description, String category, String type, String credit,
        boolean demo, List<Question> questions, List<Outcome> outcomes, List<String> tieOrder,
        Map<String, Map<String, Object>> profiles, String titleEn, String descriptionEn,
        List<String> tags) {
      this(title, description, category, type, credit, demo, questions, outcomes, tieOrder,
          profiles, titleEn, descriptionEn, tags, null, null);
    }

    public Quiz(String title, String description, String category, String type, String credit,
        boolean demo, List<Question> questions, List<Outcome> outcomes, List<String> tieOrder,
        Map<String, Map<String, Object>> profiles, String titleEn, String descriptionEn,
        List<String> tags, String questionnaireVersion) {
      this(title, description, category, type, credit, demo, questions, outcomes, tieOrder,
          profiles, titleEn, descriptionEn, tags, questionnaireVersion, null);
    }

    public Quiz withQuestions(List<Question> selected) {
      return new Quiz(title, description, category, type, credit, demo, selected, outcomes,
          tieOrder, profiles, titleEn, descriptionEn, tags, questionnaireVersion, questionsPerAttempt);
    }

    public Quiz withQuestionsPerAttempt(Integer count) {
      return new Quiz(title, description, category, type, credit, demo, questions, outcomes,
          tieOrder, profiles, titleEn, descriptionEn, tags, questionnaireVersion, count);
    }
  }

  public static void validate(Quiz q) {
    if (q == null
        || q.title() == null
        || q.title().isBlank()
        || q.title().length() > 160
        || q.description() == null
        || q.description().length() > 12000
        || q.category() == null
        || q.category().isBlank()
        || q.questions() == null
        || q.questions().isEmpty()
        || q.questions().size() > 100)
      throw new IllegalArgumentException("Add a title, description and 1–100 questions.");
    if (q.type() == null || !List.of("KNOWLEDGE", "OUTCOME", "PERSONALITY").contains(q.type()))
      throw new IllegalArgumentException("Choose a supported activity type.");
    if (q.questionsPerAttempt() != null && (!q.type().equals("KNOWLEDGE")
        || q.questionsPerAttempt() < 1 || q.questionsPerAttempt() > q.questions().size()))
      throw new IllegalArgumentException("Random knowledge rounds need a size between 1 and the number of questions.");
    if (q.questionnaireVersion() != null) {
      Personality.validate(q);
      return;
    }
    Set<String> ids = new HashSet<>();
    int[] traits = new int[4];
    for (var x : q.questions()) {
      if (x == null
          || x.id() == null
          || x.id().length() > 100
          || !ids.add(x.id())
          || x.text() == null
          || x.text().isBlank()
          || x.text().length() > 6000
          || x.options() == null
          || x.options().size() < 2
          || x.options().size() > 8)
        throw new IllegalArgumentException("Questions need unique IDs, wording and 2–8 options.");
      Set<String> opts = new HashSet<>();
      for (var o : x.options())
        if (o == null
            || o.id() == null
            || o.id().length() > 100
            || !opts.add(o.id())
            || o.text() == null
            || o.text().isBlank()
            || o.text().length() > 3000)
          throw new IllegalArgumentException("Options need unique IDs and text.");
      if (q.type().equals("KNOWLEDGE")
          && (x.options().stream().filter(o -> Integer.valueOf(1).equals(o.correct())).count() != 1
              || x.explanation() == null
              || x.explanation().isBlank()))
        throw new IllegalArgumentException(
            "Each knowledge question needs exactly one correct answer and an explanation.");
      if (q.type().equals("PERSONALITY")) {
        if (x.trait() == null || x.trait() < 0 || x.trait() > 3 || x.options().size() != 5)
          throw new IllegalArgumentException(
              "Personality items need a trait 0–3 and five options.");
        traits[x.trait()]++;
      }
    }
    if (q.type().equals("PERSONALITY") && (q.profiles() == null || q.profiles().size() != 16))
      throw new IllegalArgumentException(
          "Personality editions require all 16 profile descriptions.");
    if (q.type().equals("PERSONALITY"))
      for (int i = 0; i < 16; i++) {
        String code = "";
        for (int t = 0; t < 4; t++) code += (i & (1 << t)) != 0 ? "A" : "B";
        var profile = q.profiles().get(code);
        if (profile == null) throw new IllegalArgumentException("Missing profile " + code);
        for (String k :
            List.of(
                "name",
                "overview",
                "strengths",
                "difficulties",
                "communication",
                "relationships",
                "work"))
          if (!(profile.get(k) instanceof String v) || v.isBlank())
            throw new IllegalArgumentException("Profile " + code + " needs " + k);
      }
    if (q.type().equals("PERSONALITY") && Arrays.stream(traits).anyMatch(n -> n != 8))
      throw new IllegalArgumentException("Personality editions require eight items per trait.");
    if (q.type().equals("OUTCOME")) {
      if (q.outcomes() == null || q.outcomes().isEmpty() || q.tieOrder() == null)
        throw new IllegalArgumentException("Configure outcomes and tie order.");
      var keys = q.outcomes().stream().map(Outcome::id).toList();
      if (new HashSet<>(keys).size() != keys.size()
          || new HashSet<>(q.tieOrder()).size() != keys.size()
          || !new HashSet<>(q.tieOrder()).equals(new HashSet<>(keys)))
        throw new IllegalArgumentException("Tie order must contain each outcome once.");
      for (var o : q.outcomes())
        if (o.name() == null
            || o.name().isBlank()
            || o.description() == null
            || o.description().isBlank())
          throw new IllegalArgumentException("Outcomes need names and descriptions.");
      for (var x : q.questions())
        for (var o : x.options())
          if (o.weights() == null
              || !keys.containsAll(o.weights().keySet())
              || o.weights().values().stream().anyMatch(v -> v == null || Math.abs(v) > 100))
            throw new IllegalArgumentException("Invalid outcome weights.");
    }
  }

  public static Map<String, Object> publicQuiz(Quiz q) {
    var m = new LinkedHashMap<String, Object>();
    m.put("title", q.title());
    m.put("titleEn", q.titleEn());
    m.put("descriptionEn", q.descriptionEn());
    m.put("tags", q.tags() == null ? List.of() : q.tags());
    m.put("description", q.description());
    m.put("category", q.category());
    m.put("type", q.type());
    m.put("credit", q.credit());
    m.put("demo", q.demo());
    m.put("questionnaireVersion", q.questionnaireVersion());
    m.put("questionsPerAttempt", q.questionsPerAttempt() == null ? q.questions().size() : q.questionsPerAttempt());
    m.put("randomQuestions", q.questionsPerAttempt() != null);
    m.put("questionBankSize", q.questions().size());
    m.put(
        "questions",
        q.questions().stream()
            .map(
                x ->
                    Map.of(
                        "id",
                        x.id(),
                        "text",
                        x.text(),
                        "credit",
                        Objects.requireNonNullElse(x.credit(), ""),
                        "options",
                        x.options().stream()
                            .map(o -> Map.of("id", o.id(), "text", o.text()))
                            .toList()))
            .toList());
    return m;
  }

  public static Map<String, Object> score(Quiz q, Map<String, ?> answers) {
    if (Personality.isCurrent(q)) return Personality.score(q, answers);
    if (answers == null || answers.size() != q.questions().size())
      throw new IllegalArgumentException("Answer every question exactly once.");
    int score = 0;
    double[] sums = new double[4];
    int[] counts = new int[4];
    Map<String, Integer> totals = new HashMap<>();
    List<Object> explanations = new ArrayList<>();
    for (var x : q.questions()) {
      Object a = answers.get(x.id());
      var o =
          x.options().stream()
              .filter(v -> v.id().equals(a))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Invalid answer."));
      if (q.type().equals("KNOWLEDGE")) {
        boolean ok = Integer.valueOf(1).equals(o.correct());
        if (ok) score++;
        explanations.add(
            Map.of(
                "question",
                x.text(),
                "answer",
                o.text(),
                "correct",
                ok,
                "explanation",
                x.explanation(),
                "correctAnswer",
                x.options().stream()
                    .filter(v -> Integer.valueOf(1).equals(v.correct()))
                    .findFirst()
                    .orElseThrow()
                    .text()));
      }
      if (q.type().equals("OUTCOME"))
        o.weights().forEach((k, v) -> totals.merge(k, v, Integer::sum));
      if (q.type().equals("PERSONALITY")) {
        int r = x.options().indexOf(o) + 1;
        sums[x.trait()] += x.reverse() ? 6 - r : r;
        counts[x.trait()]++;
      }
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("type", q.type());
    result.put("title", q.title());
    result.put("demo", q.demo());
    if (q.type().equals("KNOWLEDGE")) {
      result.put("score", score);
      result.put("maximum", q.questions().size());
      result.put("explanations", explanations);
    }
    if (q.type().equals("OUTCOME")) {
      String best = q.tieOrder().getFirst();
      for (String k : q.tieOrder())
        if (totals.getOrDefault(k, 0) > totals.getOrDefault(best, 0)) best = k;
      final String id = best;
      result.put(
          "outcome",
          q.outcomes().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow());
    }
    if (q.type().equals("PERSONALITY")) {
      String code = "";
      List<Object> traits = new ArrayList<>();
      String[] names = {
        "Shared energy / Quiet energy",
        "Open paths / Clear plans",
        "Care first / Logic first",
        "Explore / Deepen"
      };
      for (int i = 0; i < 4; i++) {
        double value = 25 * sums[i] / counts[i] - 25;
        code += value > 50 ? "A" : "B";
        traits.add(
            Map.of("name", names[i], "value", value, "balanced", value >= 45 && value <= 55));
      }
      result.put("code", code);
      result.put("traits", traits);
    }
    return result;
  }
}
