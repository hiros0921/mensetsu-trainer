package jp.lightech.mensetsu.domain.interview;

/**
 * 面接の種類（仕様書2章）。
 *
 * <p>名前は DB の sessions.mode の CHECK 制約と一致させてある。片方だけ足すと、
 * 保存の瞬間に落ちる。増やすときは両方直すこと。
 */
public enum Mode {
  /** 技術選定の理由を、掘られても答え切る。 */
  ENGINEER,
  /** 深掘りに耐え、曖昧な回答を避ける。 */
  PRESSURE,
  /** 制限時間内に、簡潔に構造立てて答える。 */
  ENGLISH;

  /**
   * このモードに PRESSURE フェーズがあるか。
   *
   * <p>圧そのものは全モードで動く。表情の切り替えに使うため（仕様書6章）。
   * ここが分けているのは「圧をかけるフェーズに入るかどうか」だけ。
   */
  public boolean hasPressurePhase() {
    return this == PRESSURE;
  }
}
