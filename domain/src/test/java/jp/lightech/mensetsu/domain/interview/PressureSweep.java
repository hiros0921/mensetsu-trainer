package jp.lightech.mensetsu.domain.interview;

import java.util.ArrayList;
import java.util.List;
import jp.lightech.mensetsu.domain.stub.StubEngine;

/**
 * 圧の設定を、同じ回答パターンに当てて比べる。
 *
 * <pre>
 *   ./gradlew :domain:pressuresweep
 * </pre>
 *
 * <h2>案を出す前に測る</h2>
 *
 * 第3段階で置いた暫定値は、動かすために必要だから置いただけで、根拠が無い。
 * 第6段階の実測では、具体的に答えると圧が 20 → 0 → 2 で底に張り付いた。
 *
 * <p>幅をいくつにすべきかは、文章で考えても決まらない。回答のパターンごとに
 * 圧がどう動くかを並べて初めて選べる。これはそのための道具。
 *
 * <p>Spring も DB も LLM も使わない。スタブで面接を回し、圧の推移を記録するだけ。
 */
public final class PressureSweep {

  /** 回答のパターン。1回の面接ぶん。 */
  private record Pattern(String name, List<String> answers) {}

  private static final String CONCRETE =
      "私が PostgreSQL を選びました。3人のチームで、2か月の納期に対して"
          + "実装コストが最も低いと判断したためです。";
  private static final String VAGUE = "モダンだからです。";
  private static final String HEARSAY = "そのあたりはチームが決めたと聞いています。";
  private static final String SILENT = "";

  private static List<Pattern> patterns() {
    return List.of(
        new Pattern("ずっと具体的", List.of(CONCRETE)),
        new Pattern("ずっと曖昧", List.of(VAGUE)),
        new Pattern("ずっと伝聞", List.of(HEARSAY)),
        new Pattern("ずっと無言", List.of(SILENT)),
        // 実際の面接に近い形。詰まったあと持ち直す。
        new Pattern("曖昧→具体（持ち直す）",
            List.of(VAGUE, VAGUE, CONCRETE, CONCRETE, CONCRETE, CONCRETE, CONCRETE, CONCRETE)),
        // 良く始まって崩れる。
        new Pattern("具体→曖昧（崩れる）",
            List.of(CONCRETE, CONCRETE, CONCRETE, VAGUE, VAGUE, VAGUE, VAGUE, VAGUE)),
        new Pattern("交互", List.of(CONCRETE, VAGUE, CONCRETE, VAGUE, CONCRETE, VAGUE, CONCRETE, VAGUE)));
  }

  /** 比べる設定。いずれも案。 */
  private record Named(String label, PressureConfig config) {}

  private static List<Named> configs() {
    return List.of(
        new Named("現行（第3段階の暫定）", PressureConfig.provisional()),
        new Named("案P1・幅を半分に", PressureConfigProposals.p1()),
        new Named("案P2・下げにくくする", PressureConfigProposals.p2()),
        new Named("案P3・上がりやすく戻りにくく", PressureConfigProposals.p3()));
  }

  public static void main(String[] args) {
    System.out.println();
    System.out.println("=".repeat(88));
    System.out.println("  圧の推移（圧迫面接モード・面接官は圧迫面接官／初期値55）");
    System.out.println("=".repeat(88));

    for (Named n : configs()) {
      PressureConfig c = n.config();
      System.out.printf("%n  ── %s ──%n", n.label());
      System.out.printf("     上げ: 曖昧+%d 主語なし+%d 無言+%d ／ 下げ: 数字-%d 固有名詞-%d 自分-%d%n",
          c.riseVague(), c.riseNoFirstPerson(), c.riseSilent(),
          c.dropNumber(), c.dropProperNoun(), c.dropFirstPerson());
      System.out.printf("     強制遷移 %d ／ 押し切られ %d%n%n", c.forceAt(), c.breakAt());
      System.out.printf("     %-22s %-34s %s%n", "回答パターン", "圧の推移", "結果");
      System.out.println("     " + "-".repeat(78));

      for (Pattern p : patterns()) {
        Run r = run(p, c);
        System.out.printf("     %-22s %-34s %s%n", pad(p.name(), 22), r.trace, r.verdict);
      }
    }
    System.out.println();
    System.out.println("  ※ いずれも案です。採用されていません。");
    System.out.println();
  }

  private record Run(String trace, String verdict) {}

  private static Run run(Pattern p, PressureConfig config) {
    InterviewMachine machine =
        new InterviewMachine(new StubEngine(), new PressureModel(config));
    Step s = machine.begin(Mode.PRESSURE, InterviewerProfile.pressureHard());

    List<Integer> trace = new ArrayList<>();
    trace.add(s.state().pressure());
    boolean enteredPressure = false;
    int enteredAt = -1;

    int i = 0;
    while (!s.state().isFinished() && i < 30) {
      String text = p.answers().get(Math.min(i, p.answers().size() - 1));
      i++;
      s = machine.submit(s.state(), Answer.of(text));
      trace.add(s.state().pressure());
      if (!enteredPressure && s.state().phase() == Phase.PRESSURE) {
        enteredPressure = true;
        enteredAt = i;
      }
    }

    Outcome o = s.state().result().orElseThrow();
    String verdict =
        o.brokenByPressure() ? "押し切られた"
            : o.survivedPressure() ? "耐え切った（%d往復目に PRESSURE 入り）".formatted(enteredAt)
                : "PRESSURE に入らなかった";
    return new Run(compress(trace), verdict);
  }

  /** 推移を短く。「55→39→23→7→0→0→0」だと長いので、同じ値の連続をまとめる。 */
  private static String compress(List<Integer> trace) {
    StringBuilder b = new StringBuilder();
    int i = 0;
    while (i < trace.size()) {
      int v = trace.get(i);
      int j = i;
      while (j + 1 < trace.size() && trace.get(j + 1) == v) {
        j++;
      }
      if (b.length() > 0) {
        b.append('→');
      }
      b.append(v);
      if (j > i) {
        b.append("×").append(j - i + 1);
      }
      i = j + 1;
    }
    return b.toString();
  }

  private static String pad(String s, int width) {
    int w = 0;
    for (char c : s.toCharArray()) {
      w += c < 0x80 ? 1 : 2;
    }
    return s + " ".repeat(Math.max(0, width - w));
  }

  private PressureSweep() {}
}
