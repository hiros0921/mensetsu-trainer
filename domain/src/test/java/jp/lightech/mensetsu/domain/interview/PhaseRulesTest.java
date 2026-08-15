package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 順路と上限。数値そのものではなく、順路の形と、終端に着くことを確かめる。 */
class PhaseRulesTest {

  @ParameterizedTest
  @EnumSource(Mode.class)
  @DisplayName("どのモードでも、順路をたどれば RESULT に着くこと")
  void everyModeReachesResult(Mode mode) {
    PhaseRules rules = PhaseRules.forMode(mode);
    Phase p = Phase.INTRO;
    for (int i = 0; i < 10 && !p.isTerminal(); i++) {
      p = rules.nextOf(p);
    }
    assertTrue(p.isTerminal(), mode + " が RESULT に着かない。止まった場所: " + p);
  }

  @Test
  @DisplayName("PRESSURE フェーズを通るのは圧迫モードだけ")
  void onlyPressureModeHasPressurePhase() {
    assertEquals(Phase.PRESSURE, PhaseRules.forMode(Mode.PRESSURE).nextOf(Phase.PROBE));
    assertEquals(Phase.REVERSE, PhaseRules.forMode(Mode.ENGINEER).nextOf(Phase.PROBE));
    assertEquals(Phase.REVERSE, PhaseRules.forMode(Mode.ENGLISH).nextOf(Phase.PROBE));
  }

  @Test
  @DisplayName("RESULT から先へは進まないこと")
  void resultIsTerminal() {
    for (Mode m : Mode.values()) {
      assertEquals(Phase.RESULT, PhaseRules.forMode(m).nextOf(Phase.RESULT));
    }
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  @DisplayName("PROBE に上限があること（無限に掘り続けない）")
  void probeHasLimit(Mode mode) {
    PhaseRules rules = PhaseRules.forMode(mode);
    int max = rules.maxRounds(Phase.PROBE);
    assertTrue(max >= 1, mode + " の PROBE 上限が 0 以下: " + max);
    assertFalse(rules.reachedLimit(Phase.PROBE, max - 1));
    assertTrue(rules.reachedLimit(Phase.PROBE, max));
  }

  @Test
  @DisplayName("モードごとに PROBE の長さを変えていること")
  void probeLengthDiffersByMode() {
    // 全部同じなら、モードごとに分けている意味が無い。
    int engineer = PhaseRules.forMode(Mode.ENGINEER).maxRounds(Phase.PROBE);
    int pressure = PhaseRules.forMode(Mode.PRESSURE).maxRounds(Phase.PROBE);
    assertNotEquals(engineer, pressure);
  }
}
