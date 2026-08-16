package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 相槌の選び方。LLM を呼ばない部分なので、ここは完全に決まった動きになる。 */
class CannedLinesTest {

  @Test
  @DisplayName("同じ相槌を続けて返さないこと")
  void neverRepeatsImmediately() {
    // 「なるほど」が3回続くと、聞いていないことが露骨に伝わる。
    String previous = "";
    for (int i = 0; i < 20; i++) {
      String line = CannedLines.pick(Mode.ENGINEER, Phase.PROBE, 30, true, previous);
      assertNotEquals(previous, line, i + "回目で同じ相槌が続いた");
      previous = line;
    }
  }

  @Test
  @DisplayName("圧が高いと短くなること")
  void shortensUnderPressure() {
    String calm = CannedLines.pick(Mode.ENGINEER, Phase.PROBE, 20, true, "");
    String tense = CannedLines.pick(Mode.ENGINEER, Phase.PROBE, 90, true, "");
    assertTrue(tense.length() <= calm.length(), "圧90が圧20より長い: 「" + tense + "」対「" + calm + "」");
  }

  @Test
  @DisplayName("PRESSURE フェーズでは圧が低くても短いこと")
  void shortInPressurePhase() {
    String line = CannedLines.pick(Mode.ENGINEER, Phase.PRESSURE, 10, true, "");
    assertTrue(line.length() <= 8, "PRESSURE なのに長い: " + line);
  }

  @Test
  @DisplayName("中身の無い回答のあとは、間を置く言い方になること")
  void pausesAfterEmptyAnswer() {
    String line = CannedLines.pick(Mode.ENGINEER, Phase.PROBE, 20, false, "");
    assertTrue(line.contains("…"), "間が入っていない: " + line);
  }

  @ParameterizedTest
  @EnumSource(Phase.class)
  @DisplayName("どのフェーズでも空文字を返さないこと")
  void neverBlank(Phase phase) {
    if (phase.isTerminal()) {
      return; // RESULT では相槌を打たない
    }
    for (int pressure = 0; pressure <= 100; pressure += 10) {
      for (boolean substantive : new boolean[] {true, false}) {
        String line = CannedLines.pick(Mode.ENGINEER, phase, pressure, substantive, "");
        assertFalse(line.isBlank(), phase + " 圧" + pressure + " で空文字");
      }
    }
  }

  @Test
  @DisplayName("英語面接では英語の相槌を返すこと")
  void englishModeSpeaksEnglish() {
    // 【重要】実測で見つけた。英語で質問しているのに「なるほど。」と返っていた。
    // 深掘りは LLM が作るので言語が合う。相槌はこちらが持っているので、
    // こちらで合わせないと合わない。定型を持つ側の責任。
    for (Phase phase : Phase.values()) {
      if (phase.isTerminal()) {
        continue;
      }
      for (boolean substantive : new boolean[] {true, false}) {
        String line = CannedLines.pick(Mode.ENGLISH, phase, 40, substantive, "");
        assertFalse(hasJapanese(line), phase + " で日本語の相槌が出た: " + line);
      }
    }
  }

  @Test
  @DisplayName("日本語のモードでは日本語の相槌を返すこと")
  void japaneseModesSpeakJapanese() {
    for (Mode mode : new Mode[] {Mode.ENGINEER, Mode.PRESSURE}) {
      String line = CannedLines.pick(mode, Phase.PROBE, 40, true, "");
      assertTrue(hasJapanese(line), mode + " で日本語でない相槌が出た: " + line);
    }
  }

  /** かな・漢字が含まれているか。 */
  private static boolean hasJapanese(String s) {
    return s.codePoints().anyMatch(c ->
        (c >= 0x3040 && c <= 0x30FF) || (c >= 0x4E00 && c <= 0x9FFF));
  }

  @Test
  @DisplayName("同じ状態からは同じ結果が出ること（乱数を使っていない）")
  void isDeterministic() {
    // 乱数を使うと、試験で追えなくなる。
    Set<String> results = new HashSet<>();
    for (int i = 0; i < 10; i++) {
      results.add(CannedLines.pick(Mode.ENGINEER, Phase.PROBE, 40, true, "なるほど。"));
    }
    assertTrue(results.size() == 1, "同じ入力から違う結果が出た: " + results);
  }
}
