package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 制限時間と沈黙の判定。
 *
 * <p>時刻を渡す作りにしてあるので、90秒の制限時間を試すのに90秒待たなくてよい。
 * {@code System.currentTimeMillis()} を直接呼ぶ作りだと、このテストは書けない。
 */
class AnswerClockTest {

  /** 制限30秒・沈黙5秒・猶予2秒。試験で読みやすい値。 */
  private final TimingRules rules = new TimingRules(30_000, 5_000, 2_000);

  private static final long T0 = 1_000_000L;

  @Nested
  @DisplayName("壊れた設定を作れないこと")
  class Guards {

    @Test
    @DisplayName("沈黙の打ち切りが制限時間以上だと作れない")
    void silenceMustBeShorterThanLimit() {
      // 制限のほうが先に来るので、沈黙で打ち切られることが無くなる。
      // 沈黙の検出が死んでいることに気づけない。
      assertThrows(IllegalArgumentException.class, () -> new TimingRules(30_000, 30_000, 2_000));
      assertThrows(IllegalArgumentException.class, () -> new TimingRules(30_000, 40_000, 2_000));
    }

    @Test
    @DisplayName("猶予が沈黙の打ち切り以上だと作れない")
    void graceMustBeShorterThanSilence() {
      assertThrows(IllegalArgumentException.class, () -> new TimingRules(30_000, 5_000, 5_000));
    }
  }

  @Test
  @DisplayName("最初の入力までの猶予は、沈黙に数えないこと")
  void graceIsNotCountedAsSilence() {
    // 【重要】質問を聞いて考え始めるまでの数秒は、詰まったのではなく、ふつうの間。
    // ここを数えると全員が減点される。
    AnswerClock c = AnswerClock.started(rules, T0);
    assertEquals(0, c.silenceMs(T0 + 1_000), "猶予の中なのに沈黙が積まれている");
    assertEquals(0, c.silenceMs(T0 + 2_000));
    assertEquals(1_000, c.silenceMs(T0 + 3_000), "猶予を過ぎたぶんが数えられていない");
  }

  @Test
  @DisplayName("入力があれば、沈黙の起点が動くこと")
  void inputResetsSilence() {
    AnswerClock c = AnswerClock.started(rules, T0).onInput(T0 + 4_000);
    assertEquals(0, c.silenceMs(T0 + 4_000));
    assertEquals(3_000, c.silenceMs(T0 + 7_000));
  }

  @Test
  @DisplayName("一度入力したあとは、猶予を差し引かないこと")
  void graceAppliesOnlyBeforeFirstInput() {
    // 話し始めたあとの5秒は、正真正銘の沈黙。
    AnswerClock c = AnswerClock.started(rules, T0).onInput(T0 + 3_000);
    assertEquals(2_000, c.silenceMs(T0 + 5_000), "入力後なのに猶予が引かれている");
  }

  @Nested
  @DisplayName("打ち切り")
  class Cutoff {

    @Test
    @DisplayName("制限時間で打ち切ること")
    void cutsAtTimeLimit() {
      AnswerClock c = AnswerClock.started(rules, T0).onInput(T0 + 29_000);
      assertFalse(c.cutoff(T0 + 29_500).shouldCut());
      var cut = c.cutoff(T0 + 30_000);
      assertTrue(cut.shouldCut());
      assertTrue(cut.reason().contains("制限時間"), cut.reason());
    }

    @Test
    @DisplayName("話が止まったら打ち切ること")
    void cutsOnSilenceAfterSpeaking() {
      AnswerClock c = AnswerClock.started(rules, T0).onInput(T0 + 3_000);
      assertFalse(c.cutoff(T0 + 7_000).shouldCut());
      var cut = c.cutoff(T0 + 8_000);
      assertTrue(cut.shouldCut());
      assertTrue(cut.reason().contains("入力が止まった"), cut.reason());
    }

    @Test
    @DisplayName("一度も入力が無いままでも打ち切ること")
    void cutsWhenFrozenFromTheStart() {
      // 【重要】無言のまま固まったときに、制限時間いっぱい待たせない。
      AnswerClock c = AnswerClock.started(rules, T0);
      assertFalse(c.cutoff(T0 + 6_000).shouldCut(), "猶予2秒＋沈黙5秒＝7秒より前に切れている");
      var cut = c.cutoff(T0 + 7_000);
      assertTrue(cut.shouldCut());
      assertTrue(cut.reason().contains("何も入力されない"), cut.reason());
    }

    @Test
    @DisplayName("制限時間のほうが先に来たら、そちらを理由にすること")
    void timeLimitTakesPrecedence() {
      // 入力し続けていれば沈黙では切れない。制限時間で切れる。
      AnswerClock c = AnswerClock.started(rules, T0).onInput(T0 + 29_900);
      assertTrue(c.cutoff(T0 + 30_000).reason().contains("制限時間"));
    }
  }

  @Test
  @DisplayName("残り時間が0を下回らないこと")
  void remainingNeverNegative() {
    AnswerClock c = AnswerClock.started(rules, T0);
    assertEquals(30_000, c.remainingMs(T0));
    assertEquals(0, c.remainingMs(T0 + 60_000));
  }

  @Test
  @DisplayName("時間を測るのは英語面接官だけであること")
  void onlyEnglishIsTimed() {
    // エンジニア面接で90秒の制限をかけると、技術選定の説明が途中で切れる。
    assertTrue(InterviewerProfile.englishStandard().isTimed());
    assertFalse(InterviewerProfile.engineerStandard().isTimed());
    assertFalse(InterviewerProfile.pressureHard().isTimed());
  }

  @Test
  @DisplayName("制限時間は面接官の設定から取ること（ハードコードしない）")
  void timingComesFromTheProfile() {
    // 【重要】諏訪の指示（第8段階）:
    //   「この3つの値を、設定として変更できる形にしておいてください。
    //     interviewer_profiles に持たせれば済むはずです。ハードコードしないこと」
    //
    // 本番では DB から読む。ここで確かめるのは「面接官が持っている」という構造。
    InterviewerProfile custom =
        new InterviewerProfile("custom", "厳しめ", 20, 2, 0, TimingRules.proposalT2());
    assertEquals(TimingRules.proposalT2(), TimingRulesRegistry.forProfile(custom));
    assertEquals(null, TimingRulesRegistry.forProfile(InterviewerProfile.engineerStandard()));
  }

  @Test
  @DisplayName("採用された既定が案T1であること")
  void adoptedDefaultIsT1() {
    // 諏訪が選んだ値。「実際のAI面接の体験を再現する」ため実物に近い設定。
    TimingRules t1 = TimingRulesRegistry.adoptedEnglishDefault();
    assertEquals(90_000, t1.answerLimitMs());
    assertEquals(8_000, t1.silenceCutoffMs());
    assertEquals(3_000, t1.graceMs());
    assertEquals(t1, InterviewerProfile.englishStandard().timing());
  }

  @Nested
  @DisplayName("測るだけの時計")
  class MeasureOnly {

    @Test
    @DisplayName("打ち切らないこと")
    void neverCuts() {
      // エンジニア面接で90秒の制限をかけると、技術選定の説明が途中で切れる。
      AnswerClock c = AnswerClock.measuring(rules, T0).onInput(T0 + 1_000);
      assertFalse(c.cutoff(T0 + 600_000).shouldCut(), "10分経っても打ち切ってはいけない");
    }

    @Test
    @DisplayName("それでも沈黙は測ること")
    void stillMeasuresSilence() {
      // 【重要】ここが 0 のままだと、沈黙の軸が「測れなかった」になる。
      // 測れない軸は0点として数えるので、圧迫面接は重み25を必ず落とす。
      // 打ち切らないことと、測らないことは別。
      AnswerClock c = AnswerClock.measuring(rules, T0).onInput(T0 + 3_000);
      assertEquals(9_000, c.silenceMs(T0 + 12_000));
      assertEquals(12_000, c.elapsedMs(T0 + 12_000));
    }

    @Test
    @DisplayName("残り時間を出さないこと")
    void showsNoTimer() {
      // 画面は -1 を見てタイマーを出さない。
      assertEquals(-1, AnswerClock.measuring(rules, T0).remainingMs(T0 + 1_000));
    }
  }

  @Test
  @DisplayName("3つの案が、いずれも壊れていないこと")
  void proposalsAreValid() {
    for (TimingRules r :
        java.util.List.of(
            TimingRules.proposalT1(), TimingRules.proposalT2(), TimingRules.proposalT3())) {
      assertTrue(r.silenceCutoffMs() < r.answerLimitMs(), r.describe());
      assertTrue(r.graceMs() < r.silenceCutoffMs(), r.describe());
    }
  }
}
