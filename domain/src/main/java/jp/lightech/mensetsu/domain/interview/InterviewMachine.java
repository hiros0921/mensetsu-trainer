package jp.lightech.mensetsu.domain.interview;

import java.util.stream.Collectors;
import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.InterviewerEngine;

/**
 * 面接の進行そのもの。このアプリの技術的な主張（仕様書3章）。
 *
 * <h2>ここが守っていること</h2>
 *
 * <ul>
 *   <li><b>フェーズはサーバー側で管理する。</b> このクラスの外から遷移先を指定する
 *       方法は無い。{@link #submit} は回答しか受け取らない。
 *   <li><b>遷移条件をドメインロジックとして分離する。</b> 遷移の判断はこのクラスに
 *       あり、LLM のプロンプトには入っていない。LLM が返すのは観察
 *       （{@link Analysis}）だけで、それをどう扱うかはここが決める。
 *   <li><b>前のフェーズの回答を、後のフェーズが参照できる。</b> 履歴は
 *       {@link InterviewState#history()} に全部あり、{@link InterviewerEngine} には
 *       状態ごと渡している。
 *   <li><b>各フェーズに最大ラウンド数と打ち切り条件を持たせる。</b> 数値は
 *       {@link PhaseRules} と {@link PressureConfig} にあり、このクラスには
 *       ひとつも書いていない。値を変えるときにここを触る必要は無い。
 * </ul>
 *
 * <h2>Spring を知らない</h2>
 *
 * このクラスは new して使う。DI コンテナが要らない。だから
 * {@code new InterviewMachine(new StubEngine())} だけで単体テストが書ける。
 */
public final class InterviewMachine {

  private final InterviewerEngine engine;
  private final PressureModel pressureModel;

  public InterviewMachine(InterviewerEngine engine) {
    this(engine, new PressureModel(PressureConfig.provisional()));
  }

  public InterviewMachine(InterviewerEngine engine, PressureModel pressureModel) {
    this.engine = engine;
    this.pressureModel = pressureModel;
  }

  /** 面接を始める。最初の質問まで作って返す。 */
  public Step begin(Mode mode, InterviewerProfile profile) {
    InterviewState s = InterviewState.initial(mode, profile);
    Question q = engine.nextQuestion(s);
    return new Step(
        s.withPending(q),
        PhaseTransition.to(Phase.INTRO, TransitionReason.START, "面接を開始した"));
  }

  /**
   * 回答を1つ受け取って、状態を進める。
   *
   * <p>受け取るのは回答だけ。遷移先を外から指示する経路は無い（仕様書3章）。
   *
   * @throws IllegalStateException すでに終わっている面接に回答を送ったとき
   */
  public Step submit(InterviewState state, Answer answer) {
    if (state.isFinished()) {
      throw new IllegalStateException("終了した面接に回答は送れない");
    }
    if (state.pendingQuestion().isEmpty()) {
      throw new IllegalStateException("質問を出していない状態で回答は受け取れない");
    }

    // ① LLM に観察させる。判断はさせない。
    Analysis analysis = engine.analyzeAnswer(answer, state);

    // ② 圧を計算する。計算はアプリ側（PressureModel）。
    int pressure = pressureModel.apply(state.pressure(), answer, analysis);

    // ③ 掘る対象と段数を更新する。カウントはアプリ側（仕様書4-1）。
    ProbeState probe = state.probe().offer(analysis.technicalTerms());
    boolean wasProbing = state.pendingQuestion().map(q -> q.depth() > 0).orElse(false);
    if (wasProbing) {
      probe = probe.recordAnswer(analysis.substantive());
    }

    InterviewState answered = state.recordAnswer(answer, analysis, pressure, probe);

    // ④ 遷移を決める。ここがドメインロジック。
    PhaseTransition transition = decide(answered);
    InterviewState moved = answered.movedTo(transition.to());

    // ⑤ 終端に着いたら結果を確定する。
    if (moved.isFinished()) {
      return new Step(moved.finished(buildOutcome(moved)), transition);
    }

    // ⑥ 次の質問を用意する。掘る用語と段数を先に確定させてから engine を呼ぶ。
    //    順番が逆だと、engine が「何を何段目で掘るのか」を知らないまま作ることになる。
    InterviewState ready = prepareProbe(moved);
    Question q = engine.nextQuestion(ready);
    return new Step(ready.withPending(q), transition);
  }

  // ── 遷移の判断 ──

  private PhaseTransition decide(InterviewState s) {
    // default を書かない。フェーズを足したらここがコンパイルエラーになる。
    return switch (s.phase()) {
      case INTRO ->
          PhaseTransition.to(
              s.rules().nextOf(Phase.INTRO), TransitionReason.ANSWERED, "自己紹介を受け取った");
      case PROBE -> decideProbe(s);
      case PRESSURE -> decidePressure(s);
      case REVERSE ->
          PhaseTransition.to(Phase.CLOSING, TransitionReason.ANSWERED, "逆質問を受け取った");
      case CLOSING -> PhaseTransition.to(Phase.RESULT, TransitionReason.ANSWERED, "面接を終了した");
      case RESULT -> throw new IllegalStateException("RESULT からは遷移しない");
    };
  }

  private PhaseTransition decideProbe(InterviewState s) {
    // 圧による強制遷移が最優先（仕様書4-2）。上限に達したら、掘る途中でも中断する。
    // 実際の圧迫面接も、掘りの途中で空気が変わる。
    if (s.mode().hasPressurePhase() && pressureModel.shouldForcePressure(s.pressure())) {
      return PhaseTransition.to(
          Phase.PRESSURE,
          TransitionReason.PRESSURE_MAX,
          "圧が %d に達した（強制遷移の境目 %d）"
              .formatted(s.pressure(), pressureModel.config().forceAt()));
    }

    // 「掘る対象が尽きた」と言えるのは、実際に掘り終えた用語があるとき。
    //
    // 【重要】ここを probe.isExhausted() だけで判定してはいけない。
    // 技術用語を一度も口にしなかった利用者は、最初から掘る対象がゼロなので、
    // 1往復目で「尽きた」ことになり、PROBE を素通りする。いちばん掘るべき相手が
    // いちばん掘られずに終わる、という逆の挙動になる。
    // 対象が現れないうちは、上限ラウンドまで問いを続けて機会を与える。
    boolean exhausted = s.probe().isExhausted() && !s.probe().finished().isEmpty();
    boolean limit = s.rules().reachedLimit(Phase.PROBE, s.phaseRound());
    if (!exhausted && !limit) {
      return PhaseTransition.to(Phase.PROBE, TransitionReason.ANSWERED, "まだ掘る");
    }

    Phase next = s.rules().nextOf(Phase.PROBE);

    // 答え切れなかった用語があるなら、それを理由にする。
    // 「上限に達した」より「答えられなかった」のほうが、あとから読んで意味がある。
    if (s.probe().failedCount() > 0) {
      String terms =
          s.probe().finished().stream()
              .filter(TermResult::failed)
              .map(t -> "%s（%d段目まで）".formatted(t.term(), t.answeredDepth()))
              .collect(Collectors.joining("、"));
      return PhaseTransition.to(
          next, TransitionReason.DEPTH_FAILED, "答え切れなかった: " + terms);
    }
    if (exhausted) {
      return PhaseTransition.to(next, TransitionReason.TOPIC_EXHAUSTED, "掘る対象が尽きた");
    }
    return PhaseTransition.to(
        next,
        TransitionReason.ROUND_LIMIT,
        "PROBE の上限 %d 往復に達した".formatted(s.rules().maxRounds(Phase.PROBE)));
  }

  private PhaseTransition decidePressure(InterviewState s) {
    // 押し切られた（負け）。逆質問まで行かずに終わる。
    // 実際の面接でも、こうなった時点で逆質問の時間は取られない。
    if (pressureModel.isBroken(s.pressure())) {
      return PhaseTransition.to(
          Phase.CLOSING,
          TransitionReason.BROKEN,
          "圧が %d に達した（押し切られる境目 %d）"
              .formatted(s.pressure(), pressureModel.config().breakAt()));
    }
    // 耐え切った（勝ち。仕様書4-2）。
    if (s.rules().reachedLimit(Phase.PRESSURE, s.phaseRound())) {
      return PhaseTransition.to(
          Phase.REVERSE,
          TransitionReason.SURVIVED,
          "%d 往復耐え切った（圧 %d）".formatted(s.phaseRound(), s.pressure()));
    }
    return PhaseTransition.to(Phase.PRESSURE, TransitionReason.ANSWERED, "まだ続く");
  }

  // ── 次の質問の下ごしらえ ──

  /**
   * PROBE にいるなら、掘る用語と段数を確定させる。
   *
   * <p>engine を呼ぶ前にやる。engine は「今どの用語の何段目か」を状態から読んで
   * 質問を作るので、先に確定していないと 0 段目のまま作られる。
   */
  private InterviewState prepareProbe(InterviewState s) {
    if (s.phase() != Phase.PROBE) {
      return s;
    }
    ProbeState p = s.probe();
    if (!p.hasCurrent()) {
      p = p.takeNext();
    }
    return s.withProbe(p.asked());
  }

  // ── 結果の確定 ──

  private Outcome buildOutcome(InterviewState s) {
    int silent = (int) s.history().stream().filter(e -> e.answer().isSilent()).count();
    int silenceMs = s.history().stream().mapToInt(e -> e.answer().silenceMs()).sum();

    // PRESSURE フェーズを実際に通ったか。圧迫モードでも、圧が上がらなければ通らない。
    boolean enteredPressure = s.history().stream().anyMatch(e -> e.phase() == Phase.PRESSURE);
    // 押し切られたか。最終的な圧が境目に達していたかで見る。
    boolean broken = s.mode().hasPressurePhase() && pressureModel.isBroken(s.pressure());
    // 耐え切ったか（仕様書4-2 の勝ち）。通ったうえで、押し切られなかった場合。
    boolean survived = enteredPressure && !broken;

    return new Outcome(
        s.turnNo(),
        s.pressurePeak(),
        s.pressure(),
        s.probe().finished(),
        s.probe().deepestAnswered(),
        s.probe().failedCount(),
        survived,
        broken,
        silent,
        silenceMs);
  }
}
