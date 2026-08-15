package jp.lightech.mensetsu.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 採用された基準を固定する。
 *
 * <h2>ここだけは値そのものをテストで固定する</h2>
 *
 * 他のテストでは値を固定していない。値は変わりうるものだから。
 * だがこの基準は、諏訪さんが3案を比べて選んだもの。うっかり変わってはいけない。
 *
 * <p>変えるときは、このテストを直すことになる。そのとき「本当に変えてよいか」を
 * 考える機会になる。それがこのテストの役目。
 */
class AdoptedPolicyTest {

  private final ScoringPolicy adopted = ScoringPolicy.adoptedEngineer();

  @Test
  @DisplayName("採用された重みが、諏訪さんの選んだとおりであること")
  void weightsAreAsChosen() {
    // 案Aの重み。深さを主軸に、揺らぐ一貫性は抑える。
    assertEquals(25, adopted.weights().of(Axis.SPECIFICITY));
    assertEquals(15, adopted.weights().of(Axis.CONCISENESS));
    assertEquals(15, adopted.weights().of(Axis.CONSISTENCY));
    assertEquals(35, adopted.weights().of(Axis.DEPTH));
    assertEquals(10, adopted.weights().of(Axis.SILENCE));
  }

  @Test
  @DisplayName("深さが、いちばん重い軸であること")
  void depthIsTheHeaviest() {
    // このモードの中心は「技術選定を掘られて答え切れるか」。
    int depth = adopted.weights().of(Axis.DEPTH);
    for (Axis a : Axis.values()) {
      if (a == Axis.DEPTH) {
        continue;
      }
      assertTrue(depth > adopted.weights().of(a),
          "深さ(%d)が %s(%d) 以下になっている".formatted(depth, a.label(), adopted.weights().of(a)));
    }
  }

  @Test
  @DisplayName("一貫性の重みが、深さと具体性より小さいこと")
  void consistencyIsDeliberatelyLight() {
    // 一貫性は LLM の判定で揺らぐ。揺らぐ判定の影響を、重み配分で抑えている。
    // ここを上げるときは、判定の揺らぎが減ったことを確かめてから。
    int consistency = adopted.weights().of(Axis.CONSISTENCY);
    assertTrue(consistency < adopted.weights().of(Axis.DEPTH));
    assertTrue(consistency < adopted.weights().of(Axis.SPECIFICITY));
  }

  @Test
  @DisplayName("採用された境目が、諏訪さんの選んだとおりであること")
  void thresholdsAreAsChosen() {
    assertEquals(90, adopted.thresholds().s());
    assertEquals(78, adopted.thresholds().a());
    assertEquals(62, adopted.thresholds().b());
    assertEquals(45, adopted.thresholds().c());
  }

  @Test
  @DisplayName("測れなかった軸を0点として数えること")
  void unmeasuredCountsAsZero() {
    // 技術用語を出さない限り掘られない設計なので、出さなかったのは本人の結果。
    // 配り直すと、話さないほうが有利になる。
    assertEquals(UnmeasuredHandling.ZERO, adopted.unmeasured());
  }

  @Test
  @DisplayName("技術の話が出てこない人が、配り直しより低く出ること")
  void notTalkingTechIsPenalised() {
    InterviewState state = Candidates.noTechTalk().run();
    ScoreBreakdown b = new Scorer(adopted.params()).score(state);

    int withZero = adopted.evaluate(b).total();
    int withRedistribute = adopted.with(UnmeasuredHandling.REDISTRIBUTE).evaluate(b).total();

    assertTrue(withZero < withRedistribute,
        "0点にしたのに、配り直しより高い: %d / %d".formatted(withZero, withRedistribute));
  }

  @Test
  @DisplayName("逆質問が、具体性の分母に入っていること")
  void reverseQuestionCountsTowardScore() {
    // 「特にありません」を選ぶことにコストがあることを見せる、という判断。
    // 逆質問で答えた人と答えなかった人で、素点が変わることを確かめる。
    InterviewState answered = Candidates.strong().run(); // 逆質問に中身のある返しをする
    InterviewState declined = Candidates.middling().run(); // 「特にありません」

    assertFalse(answered.exchangesIn(Phase.REVERSE).isEmpty(), "この台本には逆質問が含まれるはず");

    Scorer scorer = new Scorer(adopted.params());
    assertTrue(
        scorer.score(answered).get(Axis.SPECIFICITY).value()
            > scorer.score(declined).get(Axis.SPECIFICITY).value());
  }

  @Test
  @DisplayName("締めの挨拶は、分母に入っていないこと")
  void closingIsStillExcluded() {
    InterviewState state = Candidates.strong().run();
    int turns = state.history().size();
    String why = new Scorer(adopted.params()).score(state).get(Axis.SPECIFICITY).why();
    assertFalse(why.contains(turns + "回の回答"), "全往復を分母にしている: " + why);
  }

  @Test
  @DisplayName("エンジニア面接以外では、基準を引こうとすると止まること")
  void otherModesRefuseToScore() {
    // 【重要】黙って流用してはいけない。点は出るしエラーも出ないが、
    // その数字が別のモードの基準で出たことは、誰にも分からなくなる。
    assertEquals(ScoringPolicy.adoptedEngineer(), ScoringPolicies.forMode(Mode.ENGINEER));

    IllegalStateException pressure =
        assertThrows(IllegalStateException.class, () -> ScoringPolicies.forMode(Mode.PRESSURE));
    assertTrue(pressure.getMessage().contains("第7段階"), pressure.getMessage());

    IllegalStateException english =
        assertThrows(IllegalStateException.class, () -> ScoringPolicies.forMode(Mode.ENGLISH));
    assertTrue(english.getMessage().contains("第8段階"), english.getMessage());

    assertTrue(ScoringPolicies.isDecided(Mode.ENGINEER));
    assertFalse(ScoringPolicies.isDecided(Mode.PRESSURE));
    assertFalse(ScoringPolicies.isDecided(Mode.ENGLISH));
  }
}
