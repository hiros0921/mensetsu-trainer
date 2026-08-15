package jp.lightech.mensetsu.app.llm;

/**
 * LLM の設定。
 *
 * <h2>【重要】APIキーはここに書かない</h2>
 *
 * 値は環境変数（{@code ANTHROPIC_API_KEY}）から SDK が直接読む。このクラスは
 * キーを保持しない。保持しないので、うっかりログに出す事故が起きない（仕様書10章）。
 *
 * @param model 使うモデル。
 * @param thinkingMode 思考の扱い。{@link #THINKING_OFF} か {@link #THINKING_ADAPTIVE}。
 * @param effort 深さ。low / medium / high。
 * @param firstTokenTargetMs 最初の文字が出るまでの目標値。超えたものは記録から拾える。
 * @param maxTokens 1回の応答の上限。面接官の1発言は短いので小さくてよい。
 */
public record LlmSettings(
    String model, String thinkingMode, String effort, long firstTokenTargetMs, int maxTokens) {

  /**
   * 思考を切る。最初の文字がいちばん速く出る。
   *
   * <p>【重要】思考を切ると、内部用のタグが応答に漏れることがある。プロンプト側で
   * 「内部用のタグを出さない」と明示して抑える。{@link Prompts} を参照。
   */
  public static final String THINKING_OFF = "off";

  /** 思考を任せる。質は上がるが、最初の文字が出るまで待つ。 */
  public static final String THINKING_ADAPTIVE = "adaptive";

  /**
   * 面接官の発話用の既定。
   *
   * <p>面接官の1発言は1〜2文で、難しい推論は要らない。体感速度を優先して、
   * 思考を切り、深さを低くする。この判断が正しいかは第4段階で実測して確かめる。
   */
  public static LlmSettings fastDefault(String model, long targetMs) {
    return new LlmSettings(model, THINKING_OFF, "low", targetMs, 400);
  }
}
