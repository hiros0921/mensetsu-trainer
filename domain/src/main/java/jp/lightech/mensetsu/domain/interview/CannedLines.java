package jp.lightech.mensetsu.domain.interview;

import java.util.List;

/**
 * 相槌・つなぎの定型（第1段階 Q5）。
 *
 * <h2>なぜアプリ側に持つか</h2>
 *
 * 面接官の発話は2種類ある。相槌・つなぎは質が要らない。深掘りの質問と矛盾の指摘は質が要る。
 * 前者を LLM に作らせると、要らない質のために待つことになる。
 *
 * <p>実際の面接官も、次を考えている間はこれをやっている。相槌を先に返し、その裏で深掘りを作れば、
 * 待ち時間が「間」として自然に見える。質を落として速くするのではなく、遅さを設計で吸収する。
 *
 * <h2>ここが domain にある理由</h2>
 *
 * 文言そのものは表示に近いが、「どの場面でどれを言うか」は面接の進行の一部で、
 * フェーズと圧に依存する。app 側に置くと、進行の知識が2箇所に散る。
 *
 * <p>切り替えの判断も含めて、ここで完結させる。LLM も HTTP も要らない。
 *
 * <h2>【重要】同じ相槌を続けて出さないこと</h2>
 *
 * 「なるほど」が3回続くと、聞いていないことが露骨に伝わる。実際の面接でも同じ。
 * 直前に使ったものを避けるだけで、かなり不自然さが減る。
 */
public final class CannedLines {

  // 平常時。次を考えている間のつなぎ。
  private static final List<String> NEUTRAL =
      List.of(
          "なるほど。",
          "ありがとうございます。",
          "承知しました。",
          "はい。",
          "ええ。");

  // 掘っている最中。もう一段いくことを匂わせる。
  private static final List<String> PROBING =
      List.of(
          "なるほど、もう少し伺えますか。",
          "そこをもう少し。",
          "ふむ。",
          "そのあたり、詳しく。");

  // 圧が高い。短く、冷たく。
  private static final List<String> PRESSURED =
      List.of(
          "……。",
          "はい。",
          "それで。",
          "続けてください。");

  // 中身が無い回答を受けたとき。間を置く。
  private static final List<String> AFTER_EMPTY =
      List.of(
          "……そうですか。",
          "はい……。",
          "なるほど。");

  private CannedLines() {}

  /**
   * 今この場面で言う相槌を1つ選ぶ。
   *
   * @param phase 今のフェーズ
   * @param pressure 今の圧
   * @param answerWasSubstantive 直前の回答に中身があったか
   * @param previous 直前に使った相槌。同じものを避けるために渡す。無ければ空文字
   */
  public static String pick(
      Phase phase, int pressure, boolean answerWasSubstantive, String previous) {
    List<String> pool = poolFor(phase, pressure, answerWasSubstantive);
    return choose(pool, previous);
  }

  private static List<String> poolFor(Phase phase, int pressure, boolean substantive) {
    if (!substantive) {
      return AFTER_EMPTY;
    }
    if (phase == Phase.PRESSURE || pressure >= 70) {
      return PRESSURED;
    }
    if (phase == Phase.PROBE) {
      return PROBING;
    }
    return NEUTRAL;
  }

  /**
   * 直前と違うものを選ぶ。
   *
   * <p>乱数を使わない。同じ状態から同じ結果が出るほうが、試験で追いやすい。
   * 直前の文字列の長さを種にして回すだけで、繰り返しは十分に避けられる。
   */
  private static String choose(List<String> pool, String previous) {
    String prev = previous == null ? "" : previous;
    int start = Math.abs(prev.hashCode()) % pool.size();
    for (int i = 0; i < pool.size(); i++) {
      String candidate = pool.get((start + i) % pool.size());
      if (!candidate.equals(prev)) {
        return candidate;
      }
    }
    return pool.get(0);
  }
}
