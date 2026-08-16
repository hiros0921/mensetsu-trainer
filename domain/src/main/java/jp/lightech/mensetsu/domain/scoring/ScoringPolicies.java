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
      case ENGLISH -> ScoringPolicy.adoptedEnglish();
    };
  }

  /** そのモードの基準が決まっているか。3モードとも決まった（第8段階）。 */
  public static boolean isDecided(Mode mode) {
    return true;
  }
}
