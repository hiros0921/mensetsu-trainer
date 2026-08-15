package jp.lightech.mensetsu.domain.port;

/**
 * LLM の呼び出しを外から観察する口。
 *
 * <p>2つの役目がある。
 *
 * <ul>
 *   <li>生成中の文字を、届いたそばから流す（ストリーミング表示）
 *   <li>1回の呼び出しが終わったら、その記録を渡す（応答時間のログ）
 * </ul>
 *
 * <h2>なぜこれを分けるか</h2>
 *
 * ステートマシンは、文字が何回に分けて届いたかを知る必要が無い。知る必要があるのは
 * 画面と、記録を保存する側。だから {@link InterviewerEngine} の戻り値ではなく、
 * 別の口として渡す。
 *
 * <p>実装が何もしない既定を用意してあるので、試験では無視できる。
 */
public interface EngineObserver {

  /** 何もしない。試験や、記録が要らない場面で使う。 */
  EngineObserver NONE = new EngineObserver() {};

  /**
   * 生成中の文字が届いた。
   *
   * @param purpose どの呼び出しか。{@link EngineCall#NEXT_QUESTION} など
   * @param delta 届いた断片。行の途中で切れていることもある
   */
  default void onDelta(String purpose, String delta) {}

  /** 1回の呼び出しが終わった。成功でも失敗でも呼ばれる。 */
  default void onCall(EngineCall call) {}
}
