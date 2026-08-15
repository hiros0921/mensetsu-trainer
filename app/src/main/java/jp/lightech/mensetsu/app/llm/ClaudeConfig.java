package jp.lightech.mensetsu.app.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Claude のクライアントを1つだけ作る。
 *
 * <h2>【重要】キーが無ければ何も作らない</h2>
 *
 * キーが無いときに空のクライアントを作ると、最初の呼び出しで初めて落ちる。
 * 面接の途中で落ちるのが最悪なので、起動時に「無い」と分かる形にする。
 *
 * <p>クライアントが無いときは、{@link jp.lightech.mensetsu.app.web.InterviewService} が
 * スタブに切り替わる。第3段階までと同じ動きになるので、面接自体は成立する。
 * 画面には「スタブで動いています」と出す。
 *
 * <h2>キーはここでも保持しない</h2>
 *
 * SDK が環境変数 {@code ANTHROPIC_API_KEY} から直接読む。このクラスは
 * 「設定されているか」だけを見て、値を変数に入れない（仕様書10章）。
 */
@Configuration
public class ClaudeConfig {

  @Bean
  public AnthropicClient anthropicClient(@Value("${mensetsu.llm.api-key:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      // Bean を作らない。null を返すと Spring は Bean を登録しない。
      // 受け取る側は @Autowired(required = false) で受ける。
      return null;
    }
    return AnthropicOkHttpClient.fromEnv();
  }
}
