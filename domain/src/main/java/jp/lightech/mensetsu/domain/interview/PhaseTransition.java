package jp.lightech.mensetsu.domain.interview;

import java.util.Objects;

/**
 * 遷移1回分。どこへ、なぜ、その具体。
 *
 * @param to 移った先。現在と同じなら、そのフェーズに留まったということ。
 * @param reason 種別。集計や絞り込みに使う。
 * @param detail 人が読む説明。「3段目で答えが出なかった（React の選定理由）」など。
 */
public record PhaseTransition(Phase to, TransitionReason reason, String detail) {

  public PhaseTransition {
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(reason, "reason");
    // detail は空でよいが null は許さない。保存側で null 判定を書きたくない。
    detail = detail == null ? "" : detail;
  }

  public static PhaseTransition to(Phase to, TransitionReason reason, String detail) {
    return new PhaseTransition(to, reason, detail);
  }

  /** そのフェーズに留まった（フェーズは変わっていない）。 */
  public boolean isStay(Phase current) {
    return to == current;
  }
}
