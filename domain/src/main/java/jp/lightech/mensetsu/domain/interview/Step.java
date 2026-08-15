package jp.lightech.mensetsu.domain.interview;

/**
 * ステートマシンを1回進めた結果。
 *
 * <p>遷移を戻り値に含めているのは、保存側が「何が起きたか」を後から推測しなくて
 * よいようにするため。前後の状態を比べてフェーズが変わったかを調べる作りにすると、
 * 「変わらなかった（同じフェーズに留まった）」ときの理由が失われる。
 *
 * @param state 進めたあとの状態。
 * @param transition 何が起きたか。session_phases にそのまま入る。
 */
public record Step(InterviewState state, PhaseTransition transition) {

  /** フェーズが実際に移ったか。留まったなら false。 */
  public boolean phaseChanged(Phase before) {
    return transition.to() != before;
  }
}
