package jp.lightech.mensetsu.domain.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.lightech.mensetsu.domain.interview.InterviewState;

/**
 * 基準の案を、同じ面接に当てて比べる。
 *
 * <pre>
 *   ./gradlew :domain:policycompare
 * </pre>
 *
 * <h2>何のためのものか</h2>
 *
 * 仕様書7章「第5段階で、基準案を複数出して提案すること。実際の採用は諏訪が判断します」。
 *
 * <p>案を文章で並べても選べない。同じ相手を各案で評価して、判定がどう変わるかを見て初めて
 * 選べる。これはそのための道具。
 *
 * <p>Spring も DB も LLM も使わない。スタブで面接を回して素点を出し、3案に通すだけ。
 */
public final class PolicyCompare {

  public static void main(String[] args) {
    List<Candidates.Profile> people = Candidates.all();
    List<ScoringPolicy> policies = ScoringPolicy.proposals();

    // 面接は1人1回だけ回す。素点は基準に依存しないので、使い回せる。
    // ここが分離の効き目。基準を変えても面接をやり直さない。
    Map<String, InterviewState> runs = new LinkedHashMap<>();
    for (Candidates.Profile p : people) {
      runs.put(p.name(), p.run());
    }

    printProposals(policies);
    printMatrix("同じ面接を各案で評価したとき", List.of("案A", "案B", "案C"), people, policies, runs);

    // 「測れなかった軸の扱い」だけを切り分けて見る。
    // 案Cは重みも境目も違うので、そのままでは扱いの効果が分からない。
    ScoringPolicy b = ScoringPolicy.proposalB();
    printMatrix(
        "案Bのまま、測れなかった軸の扱いだけを変えたとき",
        List.of("配り直す", "0点にする"),
        people,
        List.of(b.with(UnmeasuredHandling.REDISTRIBUTE), b.with(UnmeasuredHandling.ZERO)),
        runs);

    printBreakdowns(people, policies, runs);
  }

  private static void printProposals(List<ScoringPolicy> policies) {
    System.out.println();
    System.out.println("=".repeat(78));
    System.out.println("  基準の案（いずれも未採用。諏訪が選ぶもの）");
    System.out.println("=".repeat(78));
    for (ScoringPolicy p : policies) {
      System.out.println();
      System.out.println("  " + p.describe().replace("\n", "\n  "));
    }
  }

  /**
   * 人 × 基準の表を出す。
   *
   * <p>日本語は等幅で2文字幅を占めるので、String.format の桁揃えが効かない。
   * 幅を自分で数えて詰める。
   */
  private static void printMatrix(
      String title, List<String> headers, List<Candidates.Profile> people,
      List<ScoringPolicy> policies, Map<String, InterviewState> runs) {
    System.out.println();
    System.out.println("=".repeat(78));
    System.out.println("  " + title);
    System.out.println("=".repeat(78));
    System.out.println();

    StringBuilder head = new StringBuilder("  " + pad("", 24));
    for (String h : headers) {
      head.append(pad(h, 14));
    }
    System.out.println(head);
    System.out.println("  " + "-".repeat(24 + headers.size() * 14));

    for (Candidates.Profile person : people) {
      StringBuilder row = new StringBuilder("  " + pad(person.name(), 24));
      for (ScoringPolicy policy : policies) {
        Score s = evaluate(runs.get(person.name()), policy);
        row.append(pad("%s  %d点".formatted(s.grade(), s.total()), 14));
      }
      System.out.println(row);
    }
  }

  /** 表示幅で右を空白詰めする。全角は2、半角は1として数える。 */
  private static String pad(String s, int width) {
    int w = 0;
    for (char c : s.toCharArray()) {
      w += (c < 0x80 || c == '｡') ? 1 : 2;
    }
    return s + " ".repeat(Math.max(1, width - w));
  }

  private static void printBreakdowns(
      List<Candidates.Profile> people, List<ScoringPolicy> policies,
      Map<String, InterviewState> runs) {
    System.out.println();
    System.out.println("=".repeat(78));
    System.out.println("  内訳（案Bで評価した場合。画面に出すのはこの形）");
    System.out.println("=".repeat(78));

    ScoringPolicy b = policies.get(1);
    for (Candidates.Profile person : people) {
      Score s = evaluate(runs.get(person.name()), b);
      System.out.printf("%n  ── %s ──  判定 %s（%s） 合計 %d点%n",
          person.name(), s.grade(), s.grade().meaning(), s.total());
      for (Score.Contribution c : s.contributions()) {
        AxisScore raw = s.breakdown().get(c.axis());
        if (!c.measured()) {
          System.out.printf("     %-6s  測定なし        %s%n", c.axis().label(), raw.why());
          continue;
        }
        System.out.printf("     %-6s  %3d点 × 重み%2d = %4.1f点%n",
            c.axis().label(), c.raw(), c.weight(), c.points());
        System.out.printf("             %s%n", raw.why());
      }
      s.biggestGap().ifPresent(g ->
          System.out.printf("     → 伸ばすと合計がいちばん上がるのは「%s」（あと %.1f点ぶん）%n",
              g.axis().label(), (100 - g.raw()) * g.weight() / 100.0));
    }
    System.out.println();
  }

  private static Score evaluate(InterviewState state, ScoringPolicy policy) {
    return policy.evaluate(new Scorer(policy.params()).score(state));
  }

  private PolicyCompare() {}
}
