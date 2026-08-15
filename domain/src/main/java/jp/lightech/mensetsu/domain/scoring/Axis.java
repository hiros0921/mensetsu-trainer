package jp.lightech.mensetsu.domain.scoring;

/**
 * 評価の軸（仕様書7章）。
 *
 * <p>この5つ以外を足さないこと。足すなら、重みの合計をどう配り直すかを含めて決め直す必要がある。
 */
public enum Axis {
  /** 具体性 — 数字・固有名詞・自分の行動があるか。 */
  SPECIFICITY("具体性"),
  /** 簡潔さ — 冗長でないか。 */
  CONCISENESS("簡潔さ"),
  /** 一貫性 — 前の回答と矛盾していないか。 */
  CONSISTENCY("一貫性"),
  /** 深さ — 掘られて何段まで答えられたか。 */
  DEPTH("深さ"),
  /** 沈黙 — 詰まった回数と時間。 */
  SILENCE("沈黙");

  private final String label;

  Axis(String label) {
    this.label = label;
  }

  /** 画面に出す名前。 */
  public String label() {
    return label;
  }
}
