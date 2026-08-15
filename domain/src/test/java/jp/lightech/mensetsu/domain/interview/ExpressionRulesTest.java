package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 表情の切り替え。圧だけで決めていないことを確かめる。 */
class ExpressionRulesTest {

  private final ExpressionRules rules = ExpressionRules.proposalE1();

  @Test
  @DisplayName("圧が高ければ厳しくなること")
  void highPressureIsStern() {
    assertEquals(Expression.STERN, rules.pick(90, true, true, 5));
    assertEquals(Expression.STERN, rules.pick(75, true, true, 5));
  }

  @Test
  @DisplayName("圧が低くても、中身が無ければ訝しむこと")
  void emptyAnswerIsDoubtedEvenWhenCalm() {
    // 仕様書6章「圧迫モード以外でも、回答内容に応じて表情が変わること（関心 / 訝しむ）」。
    // 圧だけで切り替えると、エンジニア面接では表情が固まったままになる。
    assertEquals(Expression.DOUBTFUL, rules.pick(0, false, false, 0));
    assertEquals(Expression.DOUBTFUL, rules.pick(20, false, true, 0));
  }

  @Test
  @DisplayName("好意的は、中身のある回答が続いて初めて出ること")
  void favorableNeedsAStreak() {
    // 1回では出ない。実際の面接官も、1回良い答えをしただけでは空気が変わらない。
    assertNotEquals(Expression.FAVORABLE, rules.pick(10, true, true, 1));
    assertNotEquals(Expression.FAVORABLE, rules.pick(10, true, true, 2));
    assertEquals(Expression.FAVORABLE, rules.pick(10, true, true, 3));
    assertEquals(Expression.FAVORABLE, rules.pick(0, true, false, 5));
  }

  @Test
  @DisplayName("技術の話が出れば関心を示すこと")
  void technicalTalkDrawsInterest() {
    // 好意的になるほど低くはないが、中身があって技術の話が出ている場面。
    assertEquals(Expression.INTERESTED, rules.pick(30, true, true, 1));
    assertEquals(Expression.CALM, rules.pick(30, true, false, 1));
    // 圧が低くても、続いていなければ関心のまま
    assertEquals(Expression.INTERESTED, rules.pick(5, true, true, 1));
  }

  @Test
  @DisplayName("エンジニア面接の圧の幅でも、表情が動くこと")
  void expressionsMoveInEngineerMode() {
    // 【重要】エンジニア面接では圧がほとんど動かない（実測: 20→0→2）。
    // 圧だけで切り替える作りだと、表情が最初から最後まで同じになる。
    Set<Expression> seen = EnumSet.noneOf(Expression.class);
    for (int pressure : new int[] {20, 4, 0, 2}) {
      for (boolean substantive : new boolean[] {true, false}) {
        for (boolean term : new boolean[] {true, false}) {
          for (int streak : new int[] {0, 1, 2, 3, 5}) {
            seen.add(rules.pick(pressure, substantive, term, streak));
          }
        }
      }
    }
    assertEquals(EnumSet.of(Expression.DOUBTFUL, Expression.CALM,
            Expression.INTERESTED, Expression.FAVORABLE),
        seen,
        "エンジニア面接の圧の幅で出る表情が想定と違う: " + seen);
  }

  @Test
  @DisplayName("圧迫面接の圧の幅で、すべての表情が出ること")
  void allExpressionsReachableInPressureMode() {
    Set<Expression> seen = EnumSet.noneOf(Expression.class);
    for (int pressure = 0; pressure <= 100; pressure += 5) {
      for (boolean substantive : new boolean[] {true, false}) {
        for (boolean term : new boolean[] {true, false}) {
          for (int streak : new int[] {0, 1, 3}) {
            seen.add(rules.pick(pressure, substantive, term, streak));
          }
        }
      }
    }
    assertEquals(EnumSet.allOf(Expression.class), seen, "出ない表情がある");
  }

  @Test
  @DisplayName("開始時は圧だけで決めること")
  void atStartUsesPressureOnly() {
    // まだ回答が無いので、中身の有無を見ようがない。
    assertEquals(Expression.STERN, rules.atStart(80));
    assertEquals(Expression.DOUBTFUL, rules.atStart(55)); // 圧迫面接官の初期値
    assertEquals(Expression.CALM, rules.atStart(20)); // エンジニア面接官の初期値
  }

  @Test
  @DisplayName("境目の順序が壊れていると作れないこと")
  void guardsAgainstBrokenOrder() {
    assertThrows(IllegalArgumentException.class, () -> new ExpressionRules(45, 75, 25, 3));
    assertThrows(IllegalArgumentException.class, () -> new ExpressionRules(75, 25, 45, 3));
    assertThrows(IllegalArgumentException.class, () -> new ExpressionRules(75, 45, 25, 0));
  }

  @Test
  @DisplayName("画像のファイル名が表情ごとに違うこと")
  void fileKeysAreDistinct() {
    Set<String> keys = new java.util.HashSet<>();
    for (Expression e : Expression.values()) {
      assertTrue(keys.add(e.fileKey()), "ファイル名が重複: " + e.fileKey());
      assertNotEquals("", e.fileKey());
    }
  }
}
