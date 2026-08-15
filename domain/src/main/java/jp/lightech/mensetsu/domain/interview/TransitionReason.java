package jp.lightech.mensetsu.domain.interview;

/**
 * なぜそのフェーズに入ったか（仕様書9章）。
 *
 * <p>あとから「なぜこの評価になったか」を追えるようにするために残す。
 *
 * <p>理由を後付けで書く作りにしないこと。遷移オブジェクト {@link PhaseTransition} が
 * 必ずこれを持ち、保存側はそれをそのまま入れる。書き忘れが起きる余地を残さない。
 *
 * <p>値の名前は DB の session_phases.entered_reason と一致させてある。
 */
public enum TransitionReason {
  /** セッションの開始。 */
  START,
  /** 単に回答を受け取った（INTRO・REVERSE・CLOSING）。 */
  ANSWERED,
  /** そのフェーズの上限ラウンドに達した。 */
  ROUND_LIMIT,
  /** 掘る対象が尽きた。 */
  TOPIC_EXHAUSTED,
  /** 規定の段数まで答え切れなかった（仕様書4-1）。 */
  DEPTH_FAILED,
  /** 圧が上限に達した。強制遷移（仕様書4-2）。 */
  PRESSURE_MAX,
  /** 圧迫を規定ラウンド耐え切った（仕様書4-2 の勝ち）。 */
  SURVIVED,
  /** 圧に押し切られた（仕様書4-2 の負け）。 */
  BROKEN
}
