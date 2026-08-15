package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 圧の設定が満たすべき関係。
 *
 * <p>幅の値そのものは第7段階で決め直す。ここで固定するのは、どの案でも
 * 崩してはいけない関係だけ。
 */
class PressureConfigTest {

  @Test
  @DisplayName("黙るほうが得になる設定を作れないこと")
  void silenceMustNotBeCheaperThanTheWorstAnswer() {
    // 実際に踏んだ。riseSilent=15 に対し、曖昧な回答は 12+8=20 で上回っていた。
    assertThrows(
        IllegalArgumentException.class,
        () -> new PressureConfig(12, 8, 15, 6, 4, 6, 75, 95),
        "無言が曖昧な回答より軽い設定が通ってしまう");

    assertDoesNotThrow(() -> new PressureConfig(12, 8, 20, 6, 4, 6, 75, 95));
  }

  @Test
  @DisplayName("暫定値がその関係を満たしていること")
  void provisionalIsConsistent() {
    PressureConfig c = PressureConfig.provisional();
    assertTrue(c.riseSilent() >= c.riseVague() + c.riseNoFirstPerson());
    assertTrue(c.forceAt() < c.breakAt());
  }

  @Test
  @DisplayName("強制遷移より先に負けが来る設定を作れないこと")
  void forceMustComeBeforeBreak() {
    // 逆だと、PRESSURE フェーズに一度も入らずに負けが決まる。
    assertThrows(
        IllegalArgumentException.class, () -> new PressureConfig(12, 8, 22, 6, 4, 6, 95, 75));
  }
}
