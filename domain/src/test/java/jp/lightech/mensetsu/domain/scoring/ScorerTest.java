package jp.lightech.mensetsu.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.lightech.mensetsu.domain.interview.InterviewState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 測る側。
 *
 * <p>【重要】ここでは点数の値そのものを固定しない。素点は台本を1文字直すだけで動く。
 * 固定するのは「測れたか」「順序が保たれているか」といった、どの基準でも崩れてはいけない性質。
 */
class ScorerTest {

  private final AxisParams params = ScoringPolicy.proposalB().params();

  private ScoreBreakdown scoreOf(Candidates.Profile p) {
    InterviewState state = p.run();
    return new Scorer(params).score(state);
  }

  @Test
  @DisplayName("技術の話が出てこなければ、深さは「測れなかった」になること")
  void depthIsNotMeasuredWithoutTerms() {
    // 0点ではなく「測れなかった」。
    // 「深く聞かれて答えられなかった人」と「そもそも聞かれなかった人」を分ける。
    AxisScore depth = scoreOf(Candidates.noTechTalk()).get(Axis.DEPTH);
    assertFalse(depth.measured(), "掘る対象が無いのに深さが測れたことになっている");
    assertTrue(depth.why().contains("技術"), "測れなかった理由が説明されていない: " + depth.why());
  }

  @Test
  @DisplayName("技術の話が出れば、深さが測れること")
  void depthIsMeasuredWithTerms() {
    assertTrue(scoreOf(Candidates.strong()).get(Axis.DEPTH).measured());
  }

  @Test
  @DisplayName("締めの挨拶を具体性の分母に入れないこと")
  void closingIsExcludedFromSpecificity() {
    // 挨拶に数字が入るはずがない。入れられない場面を分母に入れると、
    // 誰でも同じだけ点が下がる。
    InterviewState state = Candidates.strong().run();
    long closingTurns =
        state.exchangesIn(jp.lightech.mensetsu.domain.interview.Phase.CLOSING).size();
    assertTrue(closingTurns > 0, "この台本には CLOSING が含まれているはず");

    AxisScore spec = new Scorer(params).score(state).get(Axis.SPECIFICITY);
    // 説明文に出る回数が、全往復数より少ないこと。
    int totalTurns = state.history().size();
    assertFalse(spec.why().contains(totalTurns + "回の回答"),
        "全往復を分母にしている: " + spec.why());
  }

  @Test
  @DisplayName("よく答えた人の素点が、答えなかった人を上回ること")
  void betterAnswersScoreHigher() {
    // 値そのものは固定しない。順序だけを固定する。
    // これが崩れたら、測り方のどこかが逆向きになっている。
    ScoreBreakdown strong = scoreOf(Candidates.strong());
    ScoreBreakdown weak = scoreOf(Candidates.weak());

    for (Axis a : Axis.values()) {
      if (!strong.get(a).measured() || !weak.get(a).measured()) {
        continue;
      }
      assertTrue(strong.get(a).value() >= weak.get(a).value(),
          "%s で、よく答えた人(%d)が答えなかった人(%d)を下回った"
              .formatted(a.label(), strong.get(a).value(), weak.get(a).value()));
    }
  }

  @Test
  @DisplayName("どの軸にも、なぜその数字かの説明が付くこと")
  void everyAxisExplainsItself() {
    // 仕様書7章「この内訳表示が、アプリの価値の中心」。
    // 数字だけ出しても、次に何を直せばよいか分からない。
    for (Candidates.Profile p : Candidates.all()) {
      ScoreBreakdown b = scoreOf(p);
      for (AxisScore s : b.inDisplayOrder()) {
        assertFalse(s.why().isBlank(), p.name() + " の " + s.axis().label() + " に説明が無い");
      }
    }
  }

  @Test
  @DisplayName("一貫性の説明に、揺らぐことが書いてあること")
  void consistencyWarnsAboutItsReliability() {
    // 矛盾の判定は LLM がしており、他の軸より当てにならない。
    // 画面を見た人が、それを知らずに数字を信じないようにする。
    AxisScore c = scoreOf(Candidates.strong()).get(Axis.CONSISTENCY);
    assertTrue(c.why().contains("揺らぎ"), "揺らぐことが書かれていない: " + c.why());
  }

  @Test
  @DisplayName("素点が0〜100に収まること")
  void scoresStayInRange() {
    for (Candidates.Profile p : Candidates.all()) {
      for (AxisScore s : scoreOf(p).inDisplayOrder()) {
        assertTrue(s.value() >= 0 && s.value() <= 100,
            p.name() + " の " + s.axis().label() + " が範囲外: " + s.value());
      }
    }
  }

  @Test
  @DisplayName("判定に版が入ること")
  void scoreCarriesTheVersion() {
    // 版が無いと、基準を変えたときに過去のスコアの意味が変わってしまう。
    ScoringPolicy p = ScoringPolicy.proposalA();
    Score s = p.evaluate(scoreOf(Candidates.strong()));
    assertEquals(p.version(), s.thresholdVersion());
  }
}
