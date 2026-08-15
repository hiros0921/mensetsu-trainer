package jp.lightech.mensetsu.domain.scoring;

import jp.lightech.mensetsu.domain.interview.Mode;

/**
 * モードごとの基準を引く。
 *
 * <h2>【重要】決まっていないモードでは止まること</h2>
 *
 * エンジニア面接の基準だけが決まっている（第5段階）。圧迫面接と英語面接は、
 * 第7・第8段階で案を出して決める。
 *
 * <p>決まっていないモードで、エンジニア面接の基準を黙って流用してはいけない。
 * 点は出るし、エラーも出ない。「圧迫面接でB判定でした」と表示され、
 * <b>その数字が別のモードの基準で出たことは誰にも分からない</b>。
 *
 * <p>だからここで止める。止まるほうが、静かに間違った点を出すよりよい。
 */
public final class ScoringPolicies {

  private ScoringPolicies() {}

  /**
   * そのモードの採用済み基準。
   *
   * @throws IllegalStateException まだ基準が決まっていないモードのとき
   */
  public static ScoringPolicy forMode(Mode mode) {
    return switch (mode) {
      case ENGINEER -> ScoringPolicy.adoptedEngineer();
      case PRESSURE -> ScoringPolicy.adoptedPressure();
      case ENGLISH ->
          throw new IllegalStateException(
              "英語面接の基準はまだ決まっていません（第8段階で案を出して決めます）。"
                  + "簡潔さと沈黙が主軸になる見込みで、エンジニア面接の基準とは重みが違います");
    };
  }

  /** そのモードの基準が決まっているか。画面で「まだ判定を出せません」と伝えるために使う。 */
  public static boolean isDecided(Mode mode) {
    return mode != Mode.ENGLISH;
  }
}
