package jp.lightech.mensetsu.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 基準の側。数値そのものではなく、基準が満たすべき関係を固定する。 */
class ScoringPolicyTest {

  static List<ScoringPolicy> proposals() {
    return ScoringPolicy.proposals();
  }

  @Nested
  @DisplayName("壊れた基準を作れないこと")
  class Guards {

    @Test
    @DisplayName("重みの合計が100でないと作れない")
    void weightsMustSumTo100() {
      // 合計95で運用していた、のような事故はあとから気づけない。
      assertThrows(IllegalArgumentException.class, () -> Weights.of(25, 20, 20, 25, 5));
      assertThrows(IllegalArgumentException.class, () -> Weights.of(30, 20, 20, 25, 10));
      Weights.of(25, 20, 20, 25, 10); // これは通る
    }

    @Test
    @DisplayName("境目の順序が壊れていると作れない")
    void thresholdsMustDescend() {
      // A の下限が S の下限より高いと、A が一度も出ない。
      assertThrows(IllegalArgumentException.class, () -> new GradeThresholds(70, 85, 55, 40));
      assertThrows(IllegalArgumentException.class, () -> new GradeThresholds(85, 70, 70, 40));
      new GradeThresholds(85, 70, 55, 40); // これは通る
    }

    @Test
    @DisplayName("簡潔さの帯が逆だと作れない")
    void bandMustBeOrdered() {
      assertThrows(IllegalArgumentException.class, () -> new AxisParams(250, 40, 5000));
    }
  }

  @ParameterizedTest
  @MethodSource("proposals")
  @DisplayName("どの案でも5段階すべてに到達できること")
  void everyGradeIsReachable(ScoringPolicy policy) {
    // 仕様書7章「中間のB（保留）を必ず残すこと」。
    // 到達できない段階があると、5段階と言いながら実質4段階になる。
    Set<Grade> seen = EnumSet.noneOf(Grade.class);
    for (int total = 0; total <= 100; total++) {
      seen.add(policy.thresholds().gradeOf(total));
    }
    assertEquals(EnumSet.allOf(Grade.class), seen, policy.label() + " で到達できない判定がある");
  }

  @ParameterizedTest
  @MethodSource("proposals")
  @DisplayName("全軸が満点なら100点になること")
  void perfectScoresTo100(ScoringPolicy policy) {
    ScoreBreakdown all100 =
        ScoreBreakdown.of(
            java.util.Arrays.stream(Axis.values())
                .map(a -> AxisScore.of(a, 100, "試験"))
                .toList());
    assertEquals(100, policy.evaluate(all100).total(), policy.label());
    assertEquals(Grade.S, policy.evaluate(all100).grade(), policy.label());
  }

  @ParameterizedTest
  @MethodSource("proposals")
  @DisplayName("全軸が0点なら0点でDになること")
  void zeroScoresToD(ScoringPolicy policy) {
    ScoreBreakdown all0 =
        ScoreBreakdown.of(
            java.util.Arrays.stream(Axis.values()).map(a -> AxisScore.of(a, 0, "試験")).toList());
    assertEquals(0, policy.evaluate(all0).total(), policy.label());
    assertEquals(Grade.D, policy.evaluate(all0).grade(), policy.label());
  }

  @Nested
  @DisplayName("測れなかった軸の扱い")
  class Unmeasured {

    /** 深さだけ測れず、他はすべて満点。 */
    private ScoreBreakdown depthMissing() {
      return ScoreBreakdown.of(
          java.util.Arrays.stream(Axis.values())
              .map(a -> a == Axis.DEPTH
                  ? AxisScore.notMeasured(a, "掘る対象が無かった")
                  : AxisScore.of(a, 100, "試験"))
              .toList());
    }

    @Test
    @DisplayName("配り直すと、測れた軸だけで100点満点になること")
    void redistributeReachesFullMarks() {
      ScoringPolicy p = ScoringPolicy.proposalB().with(UnmeasuredHandling.REDISTRIBUTE);
      assertEquals(100, p.evaluate(depthMissing()).total(),
          "測れた軸が全部満点なのに100点にならない");
    }

    @Test
    @DisplayName("0点として数えると、その軸の重みぶんだけ引かれること")
    void zeroLosesTheWeight() {
      ScoringPolicy p = ScoringPolicy.proposalB().with(UnmeasuredHandling.ZERO);
      int depthWeight = p.weights().of(Axis.DEPTH);
      assertEquals(100 - depthWeight, p.evaluate(depthMissing()).total());
    }

    @Test
    @DisplayName("扱いを変えると判定が変わりうること")
    void handlingActuallyMatters() {
      // ここが同じ結果になるなら、選択肢として出す意味が無い。
      ScoringPolicy redistribute = ScoringPolicy.proposalB().with(UnmeasuredHandling.REDISTRIBUTE);
      ScoringPolicy zero = ScoringPolicy.proposalB().with(UnmeasuredHandling.ZERO);
      assertNotEquals(
          redistribute.evaluate(depthMissing()).total(), zero.evaluate(depthMissing()).total());
    }

    @Test
    @DisplayName("1つも測れなければDになること")
    void nothingMeasuredIsD() {
      ScoreBreakdown none =
          ScoreBreakdown.of(
              java.util.Arrays.stream(Axis.values())
                  .map(a -> AxisScore.notMeasured(a, "記録なし"))
                  .toList());
      Score s = ScoringPolicy.proposalB().evaluate(none);
      assertEquals(Grade.D, s.grade());
      assertEquals(0, s.total());
    }
  }

  @Test
  @DisplayName("案ごとに版が違うこと")
  void versionsAreDistinct() {
    // 版が同じだと、どの基準で出したスコアか区別できなくなる。
    List<String> versions = ScoringPolicy.proposals().stream().map(ScoringPolicy::version).toList();
    assertEquals(versions.size(), Set.copyOf(versions).size(), "版が重複している: " + versions);
  }

  @Test
  @DisplayName("寄与の合計が合計点と一致すること")
  void contributionsSumToTotal() {
    // 内訳が合計と合わないと、画面に出したときに読み手が混乱する。
    ScoreBreakdown mixed =
        ScoreBreakdown.of(
            List.of(
                AxisScore.of(Axis.SPECIFICITY, 80, ""),
                AxisScore.of(Axis.CONCISENESS, 60, ""),
                AxisScore.of(Axis.CONSISTENCY, 100, ""),
                AxisScore.of(Axis.DEPTH, 40, ""),
                AxisScore.of(Axis.SILENCE, 90, "")));
    Score s = ScoringPolicy.proposalB().evaluate(mixed);
    double sum = s.contributions().stream().mapToDouble(Score.Contribution::points).sum();
    assertTrue(Math.abs(sum - s.total()) <= 1.0,
        "内訳の合計 %.1f と 合計点 %d が合わない".formatted(sum, s.total()));
  }
}
