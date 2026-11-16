package community.zomi;

import java.util.*;

/** Versioned, ID-based scoring. Historical 32-item editions retain their original scorer. */
final class Personality {
  static final String SLUG = "discover-personality";
  static final String VERSION = "zomi-personality-60-v1";
  record Dimension(String id, String name, String positive, String negative,
      String positiveLetter, String negativeLetter, String explanation,
      List<Integer> plus, List<Integer> minus) {}

  static final List<Dimension> DIMENSIONS = List.of(
      new Dimension("socialEnergy", "Social energy", "Extraverted", "Introverted", "E", "I",
          "This reflects whether connection with others or time to yourself tends to replenish your energy.",
          List.of(1,11,16,31,36,43,53), List.of(6,21,26,41,51)),
      new Dimension("information", "Information preference", "Intuitive", "Observant", "N", "S",
          "This explores your pull toward possibilities and patterns or toward concrete, familiar information.",
          List.of(2,7,12,19,37,42,56), List.of(17,22,32,46,52)),
      new Dimension("decisions", "Decision preference", "Thinking", "Feeling", "T", "F",
          "This describes the relative emphasis you give to consistent reasoning and to people's feelings when choosing a direction.",
          List.of(3,13,23,25,38), List.of(8,18,28,33,48,54,57)),
      new Dimension("structure", "Approach to structure", "Judging", "Prospecting", "J", "P",
          "This explores whether plans and closure or flexibility and open possibilities tend to suit you.",
          List.of(9,24,30,39,44,55,58), List.of(4,14,29,34,49)),
      new Dimension("confidence", "Confidence and stress", "Assertive", "Turbulent", "A", "T",
          "This reflects how readily you feel settled in your choices or notice doubts and stress. Neither response defines your ability or worth.",
          List.of(5,15,35,40,59), List.of(10,20,27,45,47,50,60)));

  static String id(int number) { return "q%02d".formatted(number); }
  static boolean isCurrent(Content.Quiz q) { return VERSION.equals(q.questionnaireVersion()); }

  static void validate(Content.Quiz q) {
    if (!isCurrent(q) || !"PERSONALITY".equals(q.type()) || q.questions().size()!=60)
      throw new IllegalArgumentException("Unsupported personality questionnaire version.");
    Set<String> expected = new HashSet<>();
    for (int i=1;i<=60;i++) expected.add(id(i));
    for (var question:q.questions()) {
      if (question==null || !expected.remove(question.id()) || question.text()==null
          || question.text().isBlank() || question.text().length()>6000
          || question.options()==null || question.options().size()!=7)
        throw new IllegalArgumentException("This questionnaire requires statements q01–q60 and seven choices per statement.");
      Set<String> options = new HashSet<>();
      for (var o:question.options()) {
        if (o==null || o.text()==null || o.text().isBlank() || !options.add(o.id()))
          throw new IllegalArgumentException("Invalid personality choices.");
      }
      if (!options.equals(Set.of("-3","-2","-1","0","1","2","3")))
        throw new IllegalArgumentException("Personality choices must cover −3 through +3.");
    }
  }

  static void validateAnswers(Content.Quiz q, Map<String, ?> answers, boolean complete) {
    if (answers==null || answers.size()>60 || (complete && answers.size()!=60))
      throw new IllegalArgumentException("Answer all 60 statements before viewing your results.");
    Set<String> ids = new HashSet<>(q.questions().stream().map(Content.Question::id).toList());
    for (var entry:answers.entrySet()) {
      if (!ids.contains(entry.getKey()) || !(entry.getValue() instanceof Integer value)
          || value < -3 || value > 3)
        throw new IllegalArgumentException("Answers must use statement IDs q01–q60 and whole numbers from −3 to +3.");
    }
  }

  static Map<String,Object> score(Content.Quiz q, Map<String,?> answers) {
    validateAnswers(q, answers, true);
    List<Map<String,Object>> dimensions = new ArrayList<>();
    StringBuilder letters = new StringBuilder();
    for (var d:DIMENSIONS) {
      int raw=0;
      for (int n:d.plus()) raw+=(Integer)answers.get(id(n));
      for (int n:d.minus()) raw-=(Integer)answers.get(id(n));
      double positive=50+50d*raw/36;
      double distance=Math.abs(positive-50);
      String letter=raw>0?d.positiveLetter():raw<0?d.negativeLetter():"X";
      letters.append(letter);
      var trait=new LinkedHashMap<String,Object>();
      trait.put("id",d.id()); trait.put("name",d.name());
      trait.put("positivePole",d.positive()); trait.put("negativePole",d.negative());
      trait.put("raw",raw); trait.put("positivePercentage",positive);
      trait.put("negativePercentage",100-positive);
      trait.put("displayPositive",Math.round(positive));
      trait.put("displayNegative",100-Math.round(positive));
      trait.put("letter",letter); trait.put("balanced",raw==0);
      trait.put("strength",distance==0?"Balanced":distance<10?"Slight preference":distance<25?"Moderate preference":"Strong preference");
      trait.put("closeToMidpoint",distance>0 && distance<=5);
      trait.put("explanation",d.explanation()); dimensions.add(trait);
    }
    String main=letters.substring(0,4), suffix=letters.substring(4);
    boolean resolved=!main.contains("X");
    String description;
    if (resolved) {
      description="Your answers suggest you "
          +(main.charAt(0)=='E'?"draw energy from connecting with others":"value space to reflect and recharge")
          +", "+(main.charAt(1)=='N'?"look for patterns and possibilities":"notice practical details and concrete information")
          +", "+(main.charAt(2)=='T'?"weigh decisions through consistent reasoning":"consider how decisions will feel for the people involved")
          +", and "+(main.charAt(3)=='J'?"appreciate a clear plan":"like room to adapt as you go")+".";
    } else {
      var balanced=dimensions.subList(0,4).stream().filter(d->Boolean.TRUE.equals(d.get("balanced")))
          .map(d->(String)d.get("name")).toList();
      description="Your answers suggest a balanced preference in: "+String.join(", ",balanced)
          +". Both sides may feel natural in different situations, so this quiz does not assign a complete four-letter type.";
    }
    var result=new LinkedHashMap<String,Object>();
    result.put("type","PERSONALITY"); result.put("title",q.title()); result.put("demo",false);
    result.put("quizId",SLUG); result.put("questionnaireVersion",VERSION);
    result.put("code",main+"-"+suffix); result.put("mainCode",main); result.put("suffix",suffix);
    result.put("mainResolved",resolved); result.put("balanced",letters.toString().equals("XXXXX"));
    result.put("description",description); result.put("dimensions",dimensions);
    return result;
  }
}
