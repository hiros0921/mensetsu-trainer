package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jp.lightech.mensetsu.domain.stub.StubEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ステートマシンの検証。
 *
 * <p>【重要】このテストは Spring を使わない。DB も要らない。LLM も呼ばない。
 * new して回すだけで、面接が INTRO から RESULT まで通ることを確かめる。
 * 仕様書8章①「フレームワークなしで単体テストできる状態にすること」。
 */
class InterviewMachineTest {

  private final InterviewMachine machine = new InterviewMachine(new StubEngine());

  /** 具体的な回答。数字・技術名・自分の行動がそろっている。 */
  private static final String GOOD =
      "私が React を選びました。3人のチームで、2か月の納期に対して学習コストが最も低いと判断したためです。";

  /** 中身の無い回答。仕様書4-1の例そのもの。 */
  private static final String EMPTY = "モダンだからです。";

  /** 技術用語を含まない、そこそこの長さの回答。 */
  private static final String NO_TERM = "私が現場の運用を見直して、手順書を作り直しました。担当は3名です。";

  // ── 通し ──

  @Nested
  @DisplayName("INTRO から RESULT まで通ること")
  class Walkthrough {

    @Test
    @DisplayName("エンジニア面接：具体的に答え続けたとき")
    void engineerWithGoodAnswers() {
      Trace t = run(Mode.ENGINEER, InterviewerProfile.engineerStandard(), GOOD, 30);

      assertEquals(Phase.RESULT, t.last().phase(), "RESULT に到達していない");
      assertTrue(t.last().result().isPresent(), "結果が入っていない");
      // 通った順路。ENGINEER に PRESSURE は無い。
      assertEquals(
          List.of(Phase.INTRO, Phase.PROBE, Phase.REVERSE, Phase.CLOSING, Phase.RESULT),
          t.phases(),
          "通ったフェーズが違う");
    }

    @Test
    @DisplayName("圧迫面接：曖昧に答え続けたとき、PRESSURE を通る")
    void pressureWithVagueAnswers() {
      Trace t = run(Mode.PRESSURE, InterviewerProfile.pressureHard(), EMPTY, 30);

      assertEquals(Phase.RESULT, t.last().phase());
      assertTrue(t.phases().contains(Phase.PRESSURE), "PRESSURE を通っていない");
    }

    @Test
    @DisplayName("英語面接：RESULT まで通る")
    void english() {
      Trace t = run(Mode.ENGLISH, InterviewerProfile.englishStandard(), GOOD, 30);

      assertEquals(Phase.RESULT, t.last().phase());
      assertFalse(t.phases().contains(Phase.PRESSURE), "ENGLISH に PRESSURE は無いはず");
    }

    @Test
    @DisplayName("技術用語を一度も出さなくても、PROBE を素通りしないこと")
    void probeIsNotSkippedWhenNoTermAppears() {
      // 【重要】ここが素通りすると、いちばん掘るべき相手がいちばん掘られない。
      Trace t = run(Mode.ENGINEER, InterviewerProfile.engineerStandard(), NO_TERM, 30);

      long probeRounds = t.steps.stream().filter(s -> s.before == Phase.PROBE).count();
      assertEquals(
          5, probeRounds, "PROBE の上限（ENGINEER は5往復）まで問い続けるはずが " + probeRounds + " 往復で抜けた");
      assertEquals(Phase.RESULT, t.last().phase());
    }
  }

  // ── 3段掘り（仕様書4-1） ──

  @Nested
  @DisplayName("3段掘りのカウント")
  class ProbeDepth {

    @Test
    @DisplayName("段数はアプリ側が数えていること")
    void depthIsCountedByApp() {
      Step s = machine.begin(Mode.ENGINEER, InterviewerProfile.engineerStandard());
      s = machine.submit(s.state(), Answer.of(GOOD)); // INTRO → PROBE

      // 1段目
      assertEquals(1, s.state().pending().depth());
      assertEquals("React", s.state().pending().topic());

      s = machine.submit(s.state(), Answer.of(GOOD));
      assertEquals(2, s.state().pending().depth(), "2段目になっていない");

      s = machine.submit(s.state(), Answer.of(GOOD));
      assertEquals(3, s.state().pending().depth(), "3段目になっていない");
    }

    @Test
    @DisplayName("3段とも中身が無ければ、答え切れなかったと記録されること")
    void failsWhenNeverSubstantive() {
      Step s = machine.begin(Mode.ENGINEER, InterviewerProfile.engineerStandard());
      // 技術用語だけは出させる。掘る対象が要るため。
      s = machine.submit(s.state(), Answer.of("React です。"));
      for (int i = 0; i < 3; i++) {
        s = machine.submit(s.state(), Answer.of(EMPTY));
      }

      List<TermResult> done = s.state().probe().finished();
      assertEquals(1, done.size(), "用語を掘り終えていない");
      TermResult r = done.get(0);
      assertEquals("React", r.term());
      assertEquals(0, r.answeredDepth(), "一度も答えられていないのに段が進んでいる");
      assertTrue(r.failed(), "答え切れなかった判定になっていない");
    }

    @Test
    @DisplayName("3段とも答え切れたら、失敗にならないこと")
    void clearsWhenSubstantive() {
      Step s = machine.begin(Mode.ENGINEER, InterviewerProfile.engineerStandard());
      s = machine.submit(s.state(), Answer.of(GOOD));
      for (int i = 0; i < 3; i++) {
        s = machine.submit(s.state(), Answer.of(GOOD));
      }

      List<TermResult> done = s.state().probe().finished();
      assertEquals(1, done.size());
      assertEquals(3, done.get(0).answeredDepth(), "3段目まで答えた記録になっていない");
      assertFalse(done.get(0).failed());
    }

    @Test
    @DisplayName("答え切れなかったことが、遷移理由に残ること")
    void depthFailedIsRecordedAsReason() {
      Trace t = new Trace();
      Step s = machine.begin(Mode.ENGINEER, InterviewerProfile.engineerStandard());
      s = machine.submit(s.state(), Answer.of("React です。"));
      for (int i = 0; i < 3; i++) {
        Phase before = s.state().phase();
        s = machine.submit(s.state(), Answer.of(EMPTY));
        t.add(before, s);
      }

      PhaseTransition leaving =
          t.steps.stream()
              .map(x -> x.step.transition())
              .filter(x -> x.reason() == TransitionReason.DEPTH_FAILED)
              .findFirst()
              .orElseThrow(() -> new AssertionError("DEPTH_FAILED が記録されていない"));
      assertTrue(leaving.detail().contains("React"), "何を答えられなかったかが残っていない: " + leaving.detail());
    }
  }

  // ── 圧（仕様書4-2） ──

  @Nested
  @DisplayName("圧")
  class Pressure {

    @Test
    @DisplayName("曖昧な回答で上がり、具体的な回答で下がること")
    void movesWithAnswerQuality() {
      Step s = machine.begin(Mode.PRESSURE, InterviewerProfile.pressureHard());
      int base = s.state().pressure();

      s = machine.submit(s.state(), Answer.of(EMPTY));
      int afterVague = s.state().pressure();
      assertTrue(afterVague > base, "曖昧な回答で圧が上がっていない: " + base + " → " + afterVague);

      s = machine.submit(s.state(), Answer.of(GOOD));
      assertTrue(
          s.state().pressure() < afterVague,
          "具体的な回答で圧が下がっていない: " + afterVague + " → " + s.state().pressure());
    }

    @Test
    @DisplayName("上限に達したら PRESSURE へ強制遷移すること")
    void forcesTransitionAtLimit() {
      Trace t = run(Mode.PRESSURE, InterviewerProfile.pressureHard(), EMPTY, 30);

      PhaseTransition forced =
          t.steps.stream()
              .map(x -> x.step.transition())
              .filter(x -> x.reason() == TransitionReason.PRESSURE_MAX)
              .findFirst()
              .orElseThrow(() -> new AssertionError("強制遷移が起きていない"));
      assertEquals(Phase.PRESSURE, forced.to());
    }

    @Test
    @DisplayName("エンジニア面接では、圧が上がっても PRESSURE へ行かないこと")
    void engineerNeverEntersPressurePhase() {
      Trace t = run(Mode.ENGINEER, InterviewerProfile.engineerStandard(), EMPTY, 30);
      assertFalse(t.phases().contains(Phase.PRESSURE));
    }
  }

  // ── サーバー側で管理していること（仕様書3章） ──

  @Nested
  @DisplayName("フェーズはサーバー側で管理されていること")
  class ServerSideControl {

    @Test
    @DisplayName("終了した面接に回答を送れないこと")
    void rejectsAnswerAfterResult() {
      Trace t = run(Mode.ENGINEER, InterviewerProfile.engineerStandard(), GOOD, 30);
      InterviewState done = t.last();

      assertThrows(IllegalStateException.class, () -> machine.submit(done, Answer.of(GOOD)));
    }

    @Test
    @DisplayName("質問を出していない状態で回答を受け取らないこと")
    void rejectsAnswerWithoutQuestion() {
      InterviewState fresh =
          InterviewState.initial(Mode.ENGINEER, InterviewerProfile.engineerStandard());
      assertThrows(IllegalStateException.class, () -> machine.submit(fresh, Answer.of(GOOD)));
    }

    @Test
    @DisplayName("すべての遷移に理由が付いていること")
    void everyTransitionHasReason() {
      Trace t = run(Mode.PRESSURE, InterviewerProfile.pressureHard(), EMPTY, 30);
      for (Recorded r : t.steps) {
        PhaseTransition tr = r.step.transition();
        assertTrue(tr.detail() != null && !tr.detail().isBlank(), "理由の説明が空: " + tr);
      }
    }
  }

  // ── 前の回答を後のフェーズが参照できること（仕様書3章） ──

  @Test
  @DisplayName("履歴がフェーズをまたいで残っていること")
  void historySurvivesPhaseChanges() {
    Trace t = run(Mode.ENGINEER, InterviewerProfile.engineerStandard(), GOOD, 30);
    InterviewState done = t.last();

    assertFalse(done.exchangesIn(Phase.INTRO).isEmpty(), "INTRO の回答が残っていない");
    assertFalse(done.exchangesIn(Phase.PROBE).isEmpty(), "PROBE の回答が残っていない");
    assertEquals(done.turnNo(), done.history().size(), "往復数と履歴の数が合わない");
  }

  @Test
  @DisplayName("結果に判定（S/A/B/C/D）が入っていないこと")
  void outcomeHasNoGradeYet() {
    // 第5段階で決める。ここで勝手に判定を作らない（仕様書7章）。
    Outcome o = run(Mode.ENGINEER, InterviewerProfile.engineerStandard(), GOOD, 30)
        .last().result().orElseThrow();
    assertTrue(o.turnCount() > 0);
    // Outcome に grade という項目自体が無いことは、コンパイルが通っている時点で確定。
  }

  // ── 回すための道具 ──

  private record Recorded(Phase before, Step step) {}

  private static final class Trace {
    final List<Recorded> steps = new ArrayList<>();

    void add(Phase before, Step s) {
      steps.add(new Recorded(before, s));
    }

    InterviewState last() {
      return steps.get(steps.size() - 1).step.state();
    }

    /** 通ったフェーズを、重複を潰して順に返す。 */
    List<Phase> phases() {
      List<Phase> out = new ArrayList<>();
      for (Recorded r : steps) {
        if (out.isEmpty() || out.get(out.size() - 1) != r.before) {
          out.add(r.before);
        }
      }
      Phase end = last().phase();
      if (out.isEmpty() || out.get(out.size() - 1) != end) {
        out.add(end);
      }
      return out;
    }
  }

  /**
   * 同じ回答を返し続けて、終わるまで回す。
   *
   * @param maxSteps 打ち切り。ここに引っかかったら、終わらない経路があるということ。
   */
  private Trace run(Mode mode, InterviewerProfile profile, String answer, int maxSteps) {
    Trace t = new Trace();
    Step s = machine.begin(mode, profile);
    for (int i = 0; i < maxSteps && !s.state().isFinished(); i++) {
      Phase before = s.state().phase();
      s = machine.submit(s.state(), Answer.of(answer));
      t.add(before, s);
    }
    if (!s.state().isFinished()) {
      throw new AssertionError(
          maxSteps + " 往復しても終わらない。無限に続く経路がある。最後のフェーズ: " + s.state().phase());
    }
    return t;
  }
}
