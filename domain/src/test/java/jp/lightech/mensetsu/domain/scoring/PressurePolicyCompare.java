package jp.lightech.mensetsu.domain.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InputMethod;
import jp.lightech.mensetsu.domain.interview.InterviewMachine;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.PressureConfig;
import jp.lightech.mensetsu.domain.interview.PressureConfigProposals;
import jp.lightech.mensetsu.domain.interview.PressureModel;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.stub.StubEngine;

/**
 * 圧迫面接モードの基準案を比べる。
 *
 * <pre>
 *   ./gradlew :domain:pressurepolicy
 * </pre>
 *
 * <p>圧の設定と基準は別の判断だが、圧の設定によって「押し切られたか」が変わり、
 * 沈黙や具体性の出方も変わる。だから圧の案も並べて見られるようにしてある。
 */
public final class PressurePolicyCompare {

  /** 圧迫面接を受ける人物像。 */
  private record Person(String name, List<String> answers, int silenceMs) {}

  private static List<Person> people() {
    return List.of(
        new Person(
            "圧に耐える",
            List.of(
                "私が PostgreSQL を選びました。3人のチームで、2か月の納期に対して"
                    + "実装コストが最も低いと判断したためです。",
                "私が MySQL と比較しました。全文検索の拡張と部分インデックスの2点で決めています。",
                "私が SQLite も2日試しました。同時に3人が承認するので行ロックが要ると判断しました。",
                "私が Docker で環境を固定しました。3人の手元で挙動が違う問題が2回起きたためです。",
                "私が Go を選びました。同時に走る処理が5本あり、標準ライブラリで書ける点を取りました。",
                "私が監視を入れました。月500件の処理で、失敗が2件出たら気づける形にしています。",
                "1点だけ伺えますか。3人のチームで、レビューは何名で回されていますか。",
                "本日はありがとうございました。"),
            1_000),
        new Person(
            "押されると崩れる",
            List.of(
                "私が PostgreSQL を選びました。3人のチームで、2か月の納期に対して最適でした。",
                "私が MySQL と比較しました。全文検索の拡張が決め手で、3年ぶんのノウハウを捨てています。",
                "私が判断しました。月500件の処理で、失敗が2件出たら気づける形にしています。",
                "そのあたりはチームが決めたと聞いています。",
                "モダンだからです。",
                "なんとなくです。",
                "特にありません。",
                "ありがとうございました。"),
            18_000),
        new Person(
            "最初から中身が無い",
            List.of(
                "React を使いました。",
                "モダンだからです。",
                "なんとなくです。",
                "みんな使っていたので。",
                "わかりません。",
                "特にありません。",
                "特にありません。",
                "ありがとうございました。"),
            25_000));
  }

  public static void main(String[] args) {
    List<ScoringPolicy> policies = ScoringPolicy.pressureProposals();
    Map<String, PressureConfig> pressures = new LinkedHashMap<>();
    pressures.put("現行（暫定）", PressureConfig.provisional());
    pressures.put("案P1", PressureConfigProposals.p1());
    pressures.put("案P2", PressureConfigProposals.p2());
    pressures.put("案P3", PressureConfigProposals.p3());

    System.out.println();
    System.out.println("=".repeat(84));
    System.out.println("  圧迫面接モードの基準案（いずれも未採用）");
    System.out.println("=".repeat(84));
    for (ScoringPolicy p : policies) {
      System.out.println();
      System.out.println("  " + p.describe().replace("\n", "\n  "));
    }

    for (var entry : pressures.entrySet()) {
      System.out.println();
      System.out.println("=".repeat(84));
      System.out.printf("  圧の設定 = %s%n", entry.getKey());
      System.out.println("=".repeat(84));
      System.out.println();
      StringBuilder head = new StringBuilder("  " + pad("", 22) + pad("圧迫の結果", 20));
      for (ScoringPolicy p : policies) {
        head.append(pad(p.label().substring(0, Math.min(6, p.label().length())), 14));
      }
      System.out.println(head);
      System.out.println("  " + "-".repeat(22 + 20 + policies.size() * 14));

      for (Person person : people()) {
        InterviewState state = run(person, entry.getValue());
        var outcome = state.result().orElseThrow();
        String verdict =
            outcome.brokenByPressure() ? "押し切られた"
                : outcome.survivedPressure() ? "耐え切った" : "PRESSUREに入らず";
        StringBuilder row = new StringBuilder("  " + pad(person.name(), 22) + pad(verdict, 20));
        for (ScoringPolicy policy : policies) {
          Score s = policy.evaluate(new Scorer(policy.params()).score(state));
          row.append(pad("%s %d点".formatted(s.grade(), s.total()), 14));
        }
        System.out.println(row);
      }
    }

    // 一貫性の重みが、判定にどれだけ効くかを見せる。
    System.out.println();
    System.out.println("=".repeat(84));
    System.out.println("  一貫性が1回ぶれると、合計点が何点動くか");
    System.out.println("=".repeat(84));
    System.out.println();
    System.out.println("  実測（第6段階）: 同じ台本を2回流して、食い違い 2回 と 0回 に分かれた。");
    System.out.println("  一貫性の素点で 71点 と 100点、その差は 29点。");
    System.out.println();
    for (ScoringPolicy p : policies) {
      int w = p.weights().of(Axis.CONSISTENCY);
      System.out.printf("     %-24s 一貫性の重み %2d → 合計点が %.1f 点動く%n",
          p.label(), w, 29 * w / 100.0);
    }
    System.out.printf("     %-24s 一貫性の重み %2d → 合計点が %.1f 点動く（参考）%n",
        "エンジニア面接（採用済み）",
        ScoringPolicy.adoptedEngineer().weights().of(Axis.CONSISTENCY),
        29 * ScoringPolicy.adoptedEngineer().weights().of(Axis.CONSISTENCY) / 100.0);
    System.out.println();
  }

  private static InterviewState run(Person person, PressureConfig config) {
    InterviewMachine machine = new InterviewMachine(new StubEngine(), new PressureModel(config));
    Step s = machine.begin(Mode.PRESSURE, InterviewerProfile.pressureHard());
    int i = 0;
    while (!s.state().isFinished() && i < 30) {
      String t = person.answers().get(Math.min(i, person.answers().size() - 1));
      i++;
      s = machine.submit(
          s.state(), new Answer(t, InputMethod.TEXT, 50_000, person.silenceMs(), false));
    }
    return s.state();
  }

  private static String pad(String s, int width) {
    int w = 0;
    for (char c : s.toCharArray()) {
      w += c < 0x80 ? 1 : 2;
    }
    return s + " ".repeat(Math.max(1, width - w));
  }

  private PressurePolicyCompare() {}
}
