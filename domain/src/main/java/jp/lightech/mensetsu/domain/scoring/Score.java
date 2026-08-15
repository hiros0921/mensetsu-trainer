package jp.lightech.mensetsu.domain.scoring;

import java.util.List;
import java.util.Objects;

/**
 * 面接1回の評価。DB の scores に対応する。
 *
 * <h2>【重要】thresholdVersion を必ず持つこと</h2>
 *
 * 重みと閾値は変わりうる。版を残さないと、基準を変えた瞬間に過去のスコアの意味が変わる。
 * 「前回はBだったのに今回はC」が、実力の変化なのか基準の変化なのか区別できなくなる。
 *
 * @param grade 5段階の判定
 * @param total 合計点 0〜100
 * @param breakdown 軸ごとの素点と理由。仕様書7章「この内訳表示が、アプリの価値の中心」
 * @param thresholdVersion どの基準で出したか
 * @param contributions 軸ごとに合計点へ何点寄与したか。内訳表示に使う
 */
public record Score(
    Grade grade,
    int total,
    ScoreBreakdown breakdown,
    String thresholdVersion,
    List<Contribution> contributions) {

  public Score {
    Objects.requireNonNull(grade, "grade");
    Objects.requireNonNull(breakdown, "breakdown");
    thresholdVersion = thresholdVersion == null ? "" : thresholdVersion;
    contributions = contributions == null ? List.of() : List.copyOf(contributions);
    if (total < 0 || total > 100) {
      throw new IllegalArgumentException("合計点が範囲外: " + total);
    }
  }

  /**
   * 軸1つが合計点へ何点入れたか。
   *
   * <p>「具体性 62点 × 重み25 = 15.5点」まで見せる。合計点だけでは、次に何を直せばよいか
   * 分からない。寄与の大きい軸から直すのが早い。
   *
   * @param axis どの軸か
   * @param raw その軸の素点 0〜100
   * @param weight 実際に使われた重み（配り直しがあれば、その後の値）
   * @param points 合計点への寄与
   * @param measured 測れたか
   */
  public record Contribution(Axis axis, int raw, int weight, double points, boolean measured) {}

  /** 伸ばすと合計がいちばん上がる軸。「次にどこを直すか」の材料。 */
  public java.util.Optional<Contribution> biggestGap() {
    return contributions.stream()
        .filter(Contribution::measured)
        .max(java.util.Comparator.comparingDouble(c -> (100 - c.raw()) * c.weight() / 100.0));
  }
}
