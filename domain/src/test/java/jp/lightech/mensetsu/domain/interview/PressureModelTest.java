package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.Specificity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 圧の計算だけを切り離して確かめる。
 *
 * <p>【重要】ここで確かめているのは「向き」と「範囲」であって、幅の値ではない。
 * 幅は第7段階で決め直す（{@link PressureConfig} を参照）。値そのものをテストで
 * 固定すると、決め直すたびにテストが赤くなり、直すのが面倒になって値のほうを
 * 変えなくなる。
 */
class PressureModelTest {

  private final PressureModel model = new PressureModel(PressureConfig.provisional());

  private static Analysis analysis(boolean num, boolean noun, boolean first) {
    return new Analysis(List.of(), new Specificity(num, noun, first), true, false, "", "");
  }

  @Test
  @DisplayName("曖昧な回答で上がること")
  void risesOnVague() {
    int after = model.apply(50, Answer.of("なんとなくです"), analysis(false, false, false));
    assertTrue(after > 50, "50 → " + after);
  }

  @Test
  @DisplayName("具体的な回答で下がること")
  void dropsOnConcrete() {
    int after = model.apply(50, Answer.of("私が3人で Go を選びました"), analysis(true, true, true));
    assertTrue(after < 50, "50 → " + after);
  }

  @Test
  @DisplayName("無言はいちばん上がること")
  void silenceRisesMost() {
    int silent = model.apply(50, new Answer("", InputMethod.VOICE, 30000, 30000, true), analysis(false, false, false));
    int vague = model.apply(50, Answer.of("なんとなくです"), analysis(false, false, false));
    assertTrue(silent > vague, "無言 " + silent + " が 曖昧 " + vague + " を上回っていない");
  }

  @Test
  @DisplayName("0 を下回らず、100 を超えないこと")
  void staysInRange() {
    assertEquals(0, model.apply(2, Answer.of("私が3人で Go を選びました"), analysis(true, true, true)));
    assertEquals(100, model.apply(98, Answer.of("なんとなく"), analysis(false, false, false)));
  }

  @Test
  @DisplayName("上げ幅が下げ幅より大きいこと")
  void risesFasterThanItDrops() {
    // 一度上がった空気は、一言では元に戻らない。
    // この非対称が崩れると、圧迫面接が成立しなくなる。
    int up = model.apply(50, Answer.of("なんとなく"), analysis(false, false, false)) - 50;
    int down = 50 - model.apply(50, Answer.of("私が3人で Go を選びました"), analysis(true, true, true));
    assertTrue(up > down, "上げ幅 " + up + " が下げ幅 " + down + " 以下になっている");
  }

  @Test
  @DisplayName("強制遷移の境目より、押し切られる境目が上にあること")
  void forceComesBeforeBreak() {
    // 逆だと、PRESSURE フェーズに一度も入らずに負けが決まる。
    assertThrows(
        IllegalArgumentException.class,
        () -> new PressureConfig(12, 8, 15, 6, 4, 6, 90, 80));
  }

  @Test
  @DisplayName("境目の判定")
  void thresholds() {
    PressureConfig c = PressureConfig.provisional();
    assertFalse(model.shouldForcePressure(c.forceAt() - 1));
    assertTrue(model.shouldForcePressure(c.forceAt()));
    assertFalse(model.isBroken(c.breakAt() - 1));
    assertTrue(model.isBroken(c.breakAt()));
  }
}
