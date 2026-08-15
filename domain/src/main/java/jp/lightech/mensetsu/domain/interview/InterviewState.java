package jp.lightech.mensetsu.domain.interview;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jp.lightech.mensetsu.domain.port.Analysis;

/**
 * 面接の状態。ある瞬間のすべて。
 *
 * <h2>不変にしてある理由</h2>
 *
 * 状態を書き換えず、新しい値を返す。理由は3つ。
 *
 * <ul>
 *   <li>テストが書きやすい。ある状態を作って1回進めれば、前と後を並べて比べられる。
 *   <li>WebSocket は複数のスレッドから触られる。書き換える作りだと、そこで壊れる。
 *   <li>「前の回答を後のフェーズが参照する」（仕様書3章）とき、履歴が途中で
 *       書き換わっていないことを保証できる。
 * </ul>
 *
 * <h2>永続化を知らない</h2>
 *
 * このクラスは DB を知らない。保存は app 側の仕事。ステートマシンの単体テストに
 * DB を要求しないため（仕様書8章①）。
 *
 * @param profile 面接官の設定。
 * @param rules フェーズの上限と順路。
 * @param phase 今どこにいるか。
 * @param phaseRound 今のフェーズで何往復したか。
 * @param turnNo これまでの往復数。
 * @param pressure 今の圧。
 * @param pressurePeak いちばん高かった圧。
 * @param probe 何を何段目まで掘っているか。
 * @param history これまでのやりとり。古い順。
 * @param pending 回答待ちの質問。RESULT に着いたら null。
 * @param outcome 結果。RESULT に着くまで null。
 */
public record InterviewState(
    InterviewerProfile profile,
    PhaseRules rules,
    Phase phase,
    int phaseRound,
    int turnNo,
    int pressure,
    int pressurePeak,
    ProbeState probe,
    List<Exchange> history,
    Question pending,
    Outcome outcome) {

  public InterviewState {
    history = history == null ? List.of() : List.copyOf(history);
  }

  /** 開始時の状態。まだ質問は出していない。 */
  public static InterviewState initial(Mode mode, InterviewerProfile profile) {
    return new InterviewState(
        profile,
        PhaseRules.forMode(mode),
        Phase.INTRO,
        0,
        0,
        profile.pressureBase(),
        profile.pressureBase(),
        ProbeState.start(profile.probeDepth()),
        List.of(),
        null,
        null);
  }

  public Mode mode() {
    return rules.mode();
  }

  /** 回答待ちの質問。RESULT に着いていれば空。 */
  public Optional<Question> pendingQuestion() {
    return Optional.ofNullable(pending);
  }

  public Optional<Outcome> result() {
    return Optional.ofNullable(outcome);
  }

  public boolean isFinished() {
    return phase.isTerminal();
  }

  /**
   * 直近の回答を、新しいほうから順に返す。
   *
   * <p>仕様書3章「前のフェーズの回答を、後のフェーズが参照できること」。
   * LLM に渡す文脈を組み立てるときに使う。全部渡すと長くなりすぎるので、
   * 呼ぶ側が必要なぶんだけ取る。
   */
  public List<Exchange> recent(int n) {
    int from = Math.max(0, history.size() - n);
    return List.copyOf(history.subList(from, history.size()));
  }

  /** あるフェーズでのやりとりだけを取り出す。矛盾を突くときに使う。 */
  public List<Exchange> exchangesIn(Phase target) {
    return history.stream().filter(e -> e.phase() == target).toList();
  }

  // ── 以下、状態を進めるための生成メソッド ──
  // ステートマシン（InterviewMachine）からのみ呼ぶ。

  InterviewState withPending(Question question) {
    return new InterviewState(
        profile, rules, phase, phaseRound, turnNo, pressure, pressurePeak, probe, history,
        question, outcome);
  }

  /** 回答を1つ受け取った状態。フェーズはまだ動かさない。 */
  InterviewState recordAnswer(Answer answer, Analysis analysis, int newPressure, ProbeState newProbe) {
    List<Exchange> next = new ArrayList<>(history);
    next.add(new Exchange(turnNo + 1, phase, pending, answer, analysis));
    return new InterviewState(
        profile,
        rules,
        phase,
        phaseRound + 1,
        turnNo + 1,
        newPressure,
        Math.max(pressurePeak, newPressure),
        newProbe,
        next,
        null,
        outcome);
  }

  /** フェーズを移した状態。ラウンド数は 0 に戻る。 */
  InterviewState movedTo(Phase next) {
    if (next == phase) {
      return this;
    }
    return new InterviewState(
        profile, rules, next, 0, turnNo, pressure, pressurePeak, probe, history, pending, outcome);
  }

  InterviewState withProbe(ProbeState newProbe) {
    return new InterviewState(
        profile, rules, phase, phaseRound, turnNo, pressure, pressurePeak, newProbe, history,
        pending, outcome);
  }

  InterviewState finished(Outcome result) {
    return new InterviewState(
        profile, rules, Phase.RESULT, 0, turnNo, pressure, pressurePeak, probe, history, null,
        result);
  }
}
