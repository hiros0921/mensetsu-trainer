package jp.lightech.mensetsu.domain.scoring;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 5軸の素点。まだ値踏みしていない状態。
 *
 * <h2>ここに合計点も判定も入っていない</h2>
 *
 * これは「測った結果」であって「評価」ではない。同じ素点から、重みと閾値を変えれば
 * 違う判定が出る。基準を変えたときに過去の面接を再評価できるのは、この分離があるため。
 *
 * <p>合計と判定を作るのは {@link ScoringPolicy}。そちらの数値は諏訪が決める（仕様書7章）。
 */
public record ScoreBreakdown(Map<Axis, AxisScore> scores) {

  public ScoreBreakdown {
    Map<Axis, AxisScore> copy = new EnumMap<>(Axis.class);
    if (scores != null) {
      copy.putAll(scores);
    }
    for (Axis a : Axis.values()) {
      if (!copy.containsKey(a)) {
        throw new IllegalArgumentException("軸が欠けている: " + a);
      }
    }
    scores = Map.copyOf(copy);
  }

  public static ScoreBreakdown of(List<AxisScore> list) {
    Map<Axis, AxisScore> m = new EnumMap<>(Axis.class);
    for (AxisScore s : list) {
      m.put(s.axis(), s);
    }
    return new ScoreBreakdown(m);
  }

  public AxisScore get(Axis axis) {
    return scores.get(axis);
  }

  /** 測れた軸だけ。重みを配り直すときに使う。 */
  public List<Axis> measuredAxes() {
    return java.util.Arrays.stream(Axis.values()).filter(a -> get(a).measured()).toList();
  }

  /** 測れなかった軸。画面に「測定なし」と出すために使う。 */
  public List<Axis> unmeasuredAxes() {
    return java.util.Arrays.stream(Axis.values()).filter(a -> !get(a).measured()).toList();
  }

  /** 表示順を固定して並べる。画面ごとに順番が変わると読みにくい。 */
  public List<AxisScore> inDisplayOrder() {
    return java.util.Arrays.stream(Axis.values()).map(this::get).toList();
  }
}
