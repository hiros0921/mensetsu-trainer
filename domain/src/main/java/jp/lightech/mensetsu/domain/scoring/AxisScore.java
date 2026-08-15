package jp.lightech.mensetsu.domain.scoring;

import java.util.Objects;

/**
 * 軸1つの素点。
 *
 * <h2>【重要】measured を必ず持つこと</h2>
 *
 * 「0点」と「測れなかった」はまったく違う。
 *
 * <p>技術用語を一度も口にしなかった面接では、掘る対象が無いので <b>深さは測れない</b>。
 * これを0点として扱うと、「深く聞かれて答えられなかった人」と「そもそも聞かれなかった人」が
 * 同じ扱いになる。前者は実力の問題、後者は面接の運の問題で、まったく別のことを意味する。
 *
 * <p>測れなかった軸をどう扱うかは、重みの配り直しの問題になる。{@link ScoringPolicy} を参照。
 *
 * <h2>why を必ず持つこと</h2>
 *
 * 仕様書7章「この内訳表示が、アプリの価値の中心です。判定だけなら、既存のチャットで足ります」。
 *
 * <p>数字だけ出しても、次に何を直せばよいか分からない。「8回中3回に数字が入っていました」
 * まで出して、初めて練習の材料になる。
 *
 * @param axis どの軸か
 * @param value 0〜100。measured が false のときは 0 で、意味を持たない
 * @param measured 測れたか
 * @param why なぜその数字になったか。画面にそのまま出す
 */
public record AxisScore(Axis axis, int value, boolean measured, String why) {

  public AxisScore {
    Objects.requireNonNull(axis, "axis");
    why = why == null ? "" : why;
    if (measured && (value < 0 || value > 100)) {
      throw new IllegalArgumentException(axis + " の素点が範囲外: " + value);
    }
    if (!measured) {
      value = 0;
    }
  }

  public static AxisScore of(Axis axis, int value, String why) {
    return new AxisScore(axis, value, true, why);
  }

  /** 測れなかった。理由を必ず添える。 */
  public static AxisScore notMeasured(Axis axis, String why) {
    return new AxisScore(axis, 0, false, why);
  }
}
