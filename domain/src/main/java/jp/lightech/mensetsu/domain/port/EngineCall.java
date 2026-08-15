package jp.lightech.mensetsu.domain.port;

/**
 * LLM を1回呼んだ記録（第1段階 Q5「応答時間の計測とログ記録」）。
 *
 * <p>DB の engine_calls に対応する。
 *
 * <h2>【重要】測るのは firstTokenMs であって totalMs ではない</h2>
 *
 * ストリーミング表示なので、利用者が待つのは最初の文字が出るまで。全部出来上がるまでの時間は、
 * 体感には効かない。totalMs しか測らないと、体感が良いのに「遅い」と判断してしまう。
 *
 * <p>目標値は第1段階で決めた「深掘りは3秒以内に表示が始まること」。超えたものだけを
 * あとから拾えるように、両方を残す。
 *
 * @param purpose 何のために呼んだか。NEXT_QUESTION か ANALYZE_ANSWER。
 * @param engineKind どの実装か。STUB か CLAUDE。
 * @param model 実際に使ったモデル。スタブなら空。
 * @param firstTokenMs 最初の文字が届くまで。ストリーミングでないなら totalMs と同じ。
 * @param totalMs 生成が終わるまで。
 * @param ok 成功したか。
 * @param errorNote 失敗したときの理由。人が読む文言。
 */
public record EngineCall(
    String purpose,
    String engineKind,
    String model,
    long firstTokenMs,
    long totalMs,
    boolean ok,
    String errorNote) {

  public static final String NEXT_QUESTION = "NEXT_QUESTION";
  public static final String ANALYZE_ANSWER = "ANALYZE_ANSWER";

  public EngineCall {
    errorNote = errorNote == null ? "" : errorNote;
    model = model == null ? "" : model;
  }

  /** 目標値を超えたか。境目は呼ぶ側が持つ（設定で変わるため）。 */
  public boolean slowerThan(long targetMs) {
    return ok && firstTokenMs > targetMs;
  }
}
