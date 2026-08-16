package jp.lightech.mensetsu.domain.interview;

import java.util.EnumMap;
import java.util.Map;

/**
 * 各フェーズの上限ラウンドと、モードごとの経路。
 *
 * <h2>【重要】ここの数値は案です</h2>
 *
 * 第1段階の報告で「数値は案。第3段階で根拠を持って提案し直す」と書いた、その提案が
 * これ。採否は諏訪が決める。値を変えるときは、このクラスだけを直せばよい。
 * ステートマシン本体には数値を書いていない。
 *
 * <h2>何を根拠に置いたか</h2>
 *
 * 判断の軸は「1回の面接が何往復になるか」。練習アプリなので、長すぎると
 * 続かず、短すぎると練習にならない。1往復を1分と見て、8往復で約8分。
 * 通勤前に1回できる長さに置いた。
 *
 * <pre>
 *   ENGINEER  1 + 5 + 1 + 1 = 8 往復
 *   PRESSURE  1 + 3 + 3 + 1 + 1 = 9 往復
 *   ENGLISH   1 + 4 + 1 + 1 = 7 往復
 * </pre>
 *
 * <p>PROBE をモードごとに変えてある理由。
 *
 * <ul>
 *   <li>ENGINEER は 5。3段掘りを1周すると3往復を使う（仕様書4-1）。1つの用語しか
 *       掘れないと、たまたま得意な用語に当たった回で通ってしまう。2つ目に入れる
 *       余裕として 5 にした。
 *   <li>PRESSURE は 3。PRESSURE フェーズに 3 を割いているので、その前を長くすると
 *       全体が伸びる。掘るのはここでの主眼ではない。
 *   <li>ENGLISH は 4。制限時間があるぶん1往復が短い。往復数で埋め合わせる。
 * </ul>
 *
 * <p>この見立ては実際に通してみないと分からない。第6段階で1モードを通したときに、
 * 長さの実感を報告する。そこで直すのが自然だと考えている。
 */
public final class PhaseRules {

  private final Map<Phase, Integer> maxRounds;
  private final Mode mode;

  private PhaseRules(Mode mode, Map<Phase, Integer> maxRounds) {
    this.mode = mode;
    this.maxRounds = maxRounds;
  }

  public static PhaseRules forMode(Mode mode) {
    Map<Phase, Integer> m = new EnumMap<>(Phase.class);
    m.put(Phase.INTRO, 1);
    m.put(Phase.REVERSE, 1);
    m.put(Phase.CLOSING, 1);
    m.put(Phase.RESULT, 0);

    switch (mode) {
      case ENGINEER -> {
        m.put(Phase.PROBE, 5);
        m.put(Phase.PRESSURE, 0); // 通らない
      }
      case PRESSURE -> {
        m.put(Phase.PROBE, 3);
        m.put(Phase.PRESSURE, 3);
      }
      case ENGLISH -> {
        m.put(Phase.PROBE, 4);
        m.put(Phase.PRESSURE, 0); // 通らない
      }
      // default を書かない。モードを足したら、ここがコンパイルエラーになる。
    }
    return new PhaseRules(mode, m);
  }

  public Mode mode() {
    return mode;
  }

  /** そのフェーズで何往復までか。 */
  public int maxRounds(Phase phase) {
    return maxRounds.getOrDefault(phase, 1);
  }

  /**
   * そのフェーズの上限に達したか。
   *
   * <p>「達したら次へ」なので比較は {@code >=}。上限が 1 のフェーズで1往復したら、
   * その時点で次へ進む。
   */
  public boolean reachedLimit(Phase phase, int round) {
    return round >= maxRounds(phase);
  }

  /**
   * 平常時に、そのフェーズの次はどこか。
   *
   * <p>圧による強制遷移や、打ち切りは含まない。それは {@link InterviewMachine} が
   * 判断する。ここが答えるのは「順当に進んだ場合の順路」だけ。
   */
  public Phase nextOf(Phase phase) {
    return switch (phase) {
      case INTRO -> Phase.PROBE;
      // PRESSURE フェーズを通らないモードでは、PROBE の次は REVERSE。
      case PROBE -> mode.hasPressurePhase() ? Phase.PRESSURE : Phase.REVERSE;
      case PRESSURE -> Phase.REVERSE;
      case REVERSE -> Phase.CLOSING;
      case CLOSING -> Phase.RESULT;
      case RESULT -> Phase.RESULT; // 終端。ここから先は無い
      // default を書かない。フェーズを足したら、ここがコンパイルエラーになる。
    };
  }
}
