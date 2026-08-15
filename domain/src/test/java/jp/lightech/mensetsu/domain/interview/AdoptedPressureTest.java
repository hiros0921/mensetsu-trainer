package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import jp.lightech.mensetsu.domain.stub.StubEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 採用された圧の設定（案P2）を固定する。
 *
 * <p>ここは値そのものをテストで固定する。諏訪さんが4案を比べて選んだもので、
 * うっかり変わってはいけない。変えるときはこのテストを直すことになり、
 * そのとき「本当に変えてよいか」を考える機会になる。
 */
class AdoptedPressureTest {

  private final PressureConfig adopted = PressureConfigs.adopted();

  private static final String CONCRETE =
      "私が PostgreSQL を選びました。3人のチームで、2か月の納期に対して"
          + "実装コストが最も低いと判断したためです。";
  private static final String VAGUE = "モダンだからです。";

  @Test
  @DisplayName("採用された値が、諏訪さんの選んだ案P2であること")
  void valuesAreAsChosen() {
    assertEquals(12, adopted.riseVague());
    assertEquals(8, adopted.riseNoFirstPerson());
    assertEquals(22, adopted.riseSilent());
    assertEquals(3, adopted.dropNumber());
    assertEquals(2, adopted.dropProperNoun());
    assertEquals(3, adopted.dropFirstPerson());
    assertEquals(75, adopted.forceAt());
    assertEquals(95, adopted.breakAt());
  }

  @Test
  @DisplayName("上げ幅が下げ幅の2倍以上あること（一度上がった空気は戻りにくい）")
  void risesMuchFasterThanItDrops() {
    // 諏訪さんの判断: 「一度あやしいと思われたら、後の良い回答では完全に戻らない」。
    int up = adopted.riseVague() + adopted.riseNoFirstPerson();
    int down = adopted.dropNumber() + adopted.dropProperNoun() + adopted.dropFirstPerson();
    assertTrue(up >= down * 2, "上げ幅 %d が下げ幅 %d の2倍に届かない".formatted(up, down));
  }

  @Test
  @DisplayName("「具体→曖昧（崩れる）」を見逃さないこと")
  void catchesTheCollapsePattern() {
    // 諏訪さんが案P1を落とした理由。圧迫面接がまさに測りたい失敗。
    // 最初は良いことを言うのに、押されると崩れる人。
    List<String> collapse =
        List.of(CONCRETE, CONCRETE, CONCRETE, VAGUE, VAGUE, VAGUE, VAGUE, VAGUE);
    Outcome o = run(collapse).result().orElseThrow();
    assertTrue(o.brokenByPressure(), "崩れたのに押し切られていない");
  }

  @Test
  @DisplayName("「交互」も押し切られること（ムラがあれば突かれる）")
  void catchesTheInconsistentPattern() {
    List<String> alternating =
        List.of(CONCRETE, VAGUE, CONCRETE, VAGUE, CONCRETE, VAGUE, CONCRETE, VAGUE);
    Outcome o = run(alternating).result().orElseThrow();
    assertTrue(o.brokenByPressure(), "ムラがあるのに押し切られていない");
  }

  @Test
  @DisplayName("ずっと具体的に答えれば耐え切れること（報われる）")
  void consistentlyConcreteSurvives() {
    // 諏訪さんが案P3を落とした理由。「どれだけ良い回答をしても報われないと続かない」。
    Outcome o = run(List.of(CONCRETE)).result().orElseThrow();
    assertTrue(o.survivedPressure(), "具体的に答え続けたのに耐え切れていない");
    assertTrue(o.pressureFinal() < 25,
        "良い回答を続けたのに圧が下がりきらない: " + o.pressureFinal());
  }

  @Test
  @DisplayName("案P2で「好意的」に届くこと")
  void favorableIsReachable() {
    // 【重要】諏訪さんからの確認事項。
    // 「案P2は圧が下がりにくいので、好意的（25未満）に届かない可能性がある。
    //   5枚すべてが実際に出るか、通しで確認してください」
    //
    // 圧迫面接の初期値は55。下げ幅は1往復あたり8なので、25を切るには4往復かかる。
    // 実測: 55→47→39→31→23 で、4往復目に「好意的」。連続数も4で条件を満たす。
    ExpressionRules rules = ExpressionRules.proposalE1();
    List<Expression> seq = expressionsDuring(List.of(CONCRETE), rules);
    assertTrue(seq.contains(Expression.FAVORABLE),
        "具体的に答え続けても「好意的」に届かない: " + seq);
  }

  @Test
  @DisplayName("圧迫面接で出るのは4つ。「平常」は出ない")
  void pressureModeShowsFourExpressions() {
    // 【重要】確認した結果、圧迫面接モードでは「平常」が出ない。
    //
    // 「平常」の条件は「中身があり、技術用語が無く、圧が中くらい」。
    // 圧迫面接で中身のある回答をすると、ほぼ必ず技術用語が入るので「関心」になる。
    //
    // これは設計の抜けではなく、モードの性格。エンジニア面接では開始時（圧20）に
    // 「平常」が出る。5枚がアプリ全体で使われることは
    // ExpressionRulesTest で確かめてある。
    ExpressionRules rules = ExpressionRules.proposalE1();
    Set<Expression> seen = EnumSet.noneOf(Expression.class);
    for (List<String> script :
        List.of(
            List.of(CONCRETE),
            List.of(VAGUE),
            List.of(CONCRETE, CONCRETE, CONCRETE, CONCRETE, VAGUE, VAGUE, VAGUE, VAGUE))) {
      seen.addAll(expressionsDuring(script, rules));
    }
    assertEquals(
        EnumSet.of(Expression.DOUBTFUL, Expression.INTERESTED,
            Expression.FAVORABLE, Expression.STERN),
        seen,
        "圧迫面接で出る表情が想定と違う: " + seen);
  }

  @Test
  @DisplayName("「平常」はエンジニア面接の開始時に出ること")
  void calmAppearsInEngineerMode() {
    // 5枚の画像が全部使われることの確認。圧迫面接で出ない1枚がここで出る。
    ExpressionRules rules = ExpressionRules.proposalE1();
    assertEquals(Expression.CALM,
        rules.atStart(InterviewerProfile.engineerStandard().pressureBase()));
  }

  // ── 回すための道具 ──

  private InterviewState run(List<String> answers) {
    InterviewMachine machine =
        new InterviewMachine(new StubEngine(), new PressureModel(adopted));
    Step s = machine.begin(Mode.PRESSURE, InterviewerProfile.pressureHard());
    int i = 0;
    while (!s.state().isFinished() && i < 30) {
      s = machine.submit(s.state(), Answer.of(answers.get(Math.min(i++, answers.size() - 1))));
    }
    return s.state();
  }

  /** 面接を1回通し、各往復で出る表情を集める。 */
  private List<Expression> expressionsDuring(List<String> answers, ExpressionRules rules) {
    InterviewMachine machine =
        new InterviewMachine(new StubEngine(), new PressureModel(adopted));
    Step s = machine.begin(Mode.PRESSURE, InterviewerProfile.pressureHard());

    List<Expression> out = new ArrayList<>();
    out.add(rules.atStart(s.state().pressure()));

    int i = 0;
    int streak = 0;
    while (!s.state().isFinished() && i < 30) {
      s = machine.submit(s.state(), Answer.of(answers.get(Math.min(i++, answers.size() - 1))));
      var last = s.state().history().get(s.state().history().size() - 1).analysis();
      streak = last.substantive() ? streak + 1 : 0;
      out.add(rules.pick(
          s.state().pressure(), last.substantive(), !last.technicalTerms().isEmpty(), streak));
    }
    return out;
  }
}
