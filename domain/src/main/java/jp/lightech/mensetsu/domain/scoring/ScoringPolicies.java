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
      // 【重要】まだ決まっていない。案E-3を仮に使う。
      //
      // 第7段階までは、決まっていないモードでは止めていた。第8段階では
      // 音声入力と打ち切りを実際に動かして確かめる必要があるので、仮の基準を入れる。
      //
      // 仮であることは version（english-e3）から分かる。採用したら
      // adoptedEnglish() を足して、そちらを返すように変える。
      case ENGLISH -> ScoringPolicy.proposalEn3();
    };
  }

  /**
   * そのモードの基準が決まっているか。
   *
   * <p>英語面接は動くが、基準は仮のもの。画面に「仮の基準です」と出すために使う。
   * 動くことと、決まっていることは違う。
   */
  public static boolean isDecided(Mode mode) {
    return mode != Mode.ENGLISH;
  }
}
