package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.lightech.mensetsu.domain.scoring.Axis;
import jp.lightech.mensetsu.domain.scoring.AxisParams;
import jp.lightech.mensetsu.domain.scoring.ScoreBreakdown;
import jp.lightech.mensetsu.domain.scoring.Scorer;
import jp.lightech.mensetsu.domain.scoring.ScoringPolicies;
import jp.lightech.mensetsu.domain.stub.StubEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 沈黙の軸が、全モードで測れていること。
 *
 * <h2>なぜこのテストがあるか</h2>
 *
 * 第8段階で英語面接に制限時間を入れたとき、時計を英語面接官にだけ持たせた。
 * その結果、日本語の2モードでは時間が記録されず、沈黙の軸が「測れなかった」になった。
 * 測れなかった軸は0点として数えるので（諏訪の判断②）、圧迫面接は沈黙の25点を
 * 必ず落とし、満点が75点になっていた。基準を選んだときの材料では沈黙は測れていたので、
 * 前提と実物が食い違っていたことになる。
 *
 * <p>諏訪の指摘（第8段階の確認）:
 *
 * <blockquote>今回は、モードを追加したときに他モードの前提が落ちる型でした。
 * 同じことがまた起きます。</blockquote>
 *
 * <p>だから「モードごとに」ではなく「全モードを回して」確かめる。
 * モードを足したら、このテストが自動的にそのモードも見る。
 */
class SilenceIsMeasuredEverywhereTest {

  private static final long T0 = 1_000_000L;

  @ParameterizedTest
  @EnumSource(Mode.class)
  @DisplayName("どのモードでも、時計が用意されること")
  void everyModeGetsAClock(Mode mode) {
    // 【重要】null を返す道を作らない。「打ち切らない」は「測らない」ではない。
    AnswerClock clock = AnswerClock.forProfile(profileFor(mode), T0);
    assertNotNull(clock, mode + " に時計が無い");

    // 3秒の猶予を過ぎたぶんは、どのモードでも沈黙として積まれる。
    assertEquals(2_000, clock.silenceMs(T0 + 5_000), mode + " で沈黙が積まれていない");
    assertEquals(5_000, clock.elapsedMs(T0 + 5_000), mode + " で経過時間が測れていない");
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @DisplayName("打ち切るのは英語面接だけであること")
  void onlyEnglishCutsOff(Mode mode) {
    // エンジニア面接で90秒の制限をかけると、技術選定の説明が途中で切れる。
    AnswerClock clock = AnswerClock.forProfile(profileFor(mode), T0);
    boolean cut = clock.cutoff(T0 + 600_000).shouldCut();
    if (mode == Mode.ENGLISH) {
      assertTrue(cut, "英語面接で打ち切られない");
    } else {
      assertFalse(cut, mode + " が10分で打ち切られた");
    }
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @DisplayName("面接を1回通したとき、沈黙の軸が測れていること")
  void silenceAxisIsMeasuredAfterAFullRun(Mode mode) {
    // ここが「測れなかった」に戻ると、圧迫面接は重み25、エンジニア面接は重み10を
    // 黙って落とす。合計点だけ見ていると気づけない。
    InterviewState state = runThrough(mode);
    AxisParams params = ScoringPolicies.forMode(mode).params();
    ScoreBreakdown breakdown = new Scorer(params).score(state);

    assertTrue(
        breakdown.get(Axis.SILENCE).measured(),
        mode + " の沈黙が測れていない: " + breakdown.get(Axis.SILENCE).why());
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @DisplayName("沈黙の重みが、どのモードでも捨てられていないこと")
  void silenceWeightIsNeverSilentlyLost(Mode mode) {
    InterviewState state = runThrough(mode);
    var policy = ScoringPolicies.forMode(mode);
    var score = policy.evaluate(new Scorer(policy.params()).score(state));
    var silence =
        score.contributions().stream()
            .filter(c -> c.axis() == Axis.SILENCE)
            .findFirst()
            .orElseThrow();

    assertEquals(
        policy.weights().of(Axis.SILENCE),
        silence.weight(),
        mode + " で沈黙の重みが画面から消えている");
  }

  /**
   * スタブだけで最後まで通す。LLM も DB も要らない。
   *
   * <p>【重要】経過時間と沈黙は、決め打ちの数字ではなく<b>時計から取る</b>。
   * 本番（InterviewService）と同じ経路にしてある。決め打ちにすると、時計が
   * 測らなくなってもこのテストは通ってしまい、回帰を素通りさせる。
   */
  private static InterviewState runThrough(Mode mode) {
    InterviewerProfile profile = profileFor(mode);
    InterviewMachine machine = new InterviewMachine(new StubEngine());
    Step s = machine.begin(mode, profile);
    int i = 0;
    long now = T0;
    while (!s.state().isFinished() && i < 30) {
      i++;
      // 質問を出した時点で時計を作り、話しているあいだ入力を知らせ、30秒で答え終える。
      AnswerClock clock = AnswerClock.forProfile(profile, now);
      clock = clock.onInput(now + 28_000);
      now += 30_000;
      s =
          machine.submit(
              s.state(),
              new Answer(
                  "PostgreSQL の実行計画を読んで、N+1 を1本の JOIN に直しました。" + i,
                  InputMethod.VOICE,
                  (int) clock.elapsedMs(now),
                  (int) clock.silenceMs(now),
                  false));
    }
    return s.state();
  }

  private static InterviewerProfile profileFor(Mode mode) {
    return switch (mode) {
      case ENGINEER -> InterviewerProfile.engineerStandard();
      case PRESSURE -> InterviewerProfile.pressureHard();
      case ENGLISH -> InterviewerProfile.englishStandard();
    };
  }

  @Test
  @DisplayName("測るだけの設定に、猶予が入っていること")
  void measurementOnlyStillHasGrace() {
    // 猶予が0だと、質問を聞いて考え始めるまでの数秒が全員の減点になる。
    assertTrue(TimingRulesRegistry.measurementOnly().graceMs() > 0);
  }
}
