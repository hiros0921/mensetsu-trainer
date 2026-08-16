package jp.lightech.mensetsu.domain.interview;

/**
 * モードごとの制限時間。
 *
 * <h2>時間を測るのは英語面接だけ</h2>
 *
 * 仕様書4-3は「制限時間と沈黙の再現」を英語面接モードの主眼としており、他のモードでは
 * 求めていない。エンジニア面接で90秒の制限をかけると、技術選定の説明が途中で切れる。
 *
 * <p>時間を測らないモードでは、沈黙の軸が「測れなかった」になる。0点ではない。
 * 「詰まらなかった」と「測っていない」を混同すると、満点が付いてしまう。
 *
 * <h2>【重要】英語面接の設定はまだ決まっていません</h2>
 *
 * 第8段階で案（T1・T2・T3）を出し、諏訪さんに選んでいただく。
 * それまでは案T1を仮に使う。仮であることが分かるよう、ここに書いておく。
 */
public final class TimingRulesRegistry {

  private TimingRulesRegistry() {}

  /** そのモードの制限時間。時間を測らないモードでは null。 */
  public static TimingRules forMode(Mode mode) {
    return switch (mode) {
      case ENGLISH -> provisionalEnglish();
      case ENGINEER, PRESSURE -> null;
    };
  }

  /**
   * 英語面接の仮の設定（案T1）。<b>採用されていません。</b>
   *
   * <p>第8段階で3案を出す。動かして確かめられるよう、いまは案T1を入れてある。
   */
  public static TimingRules provisionalEnglish() {
    return TimingRules.proposalT1();
  }

  /** その設定が決まっているか。 */
  public static boolean isDecided(Mode mode) {
    return mode != Mode.ENGLISH;
  }
}
