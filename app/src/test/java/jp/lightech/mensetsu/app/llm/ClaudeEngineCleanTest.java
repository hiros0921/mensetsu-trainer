package jp.lightech.mensetsu.app.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 生成物の後始末。API を呼ばずに確かめられる部分。
 *
 * <p>思考を切って動かすと、内部用のタグが出力に混ざることがある。プロンプトで抑えてあるが、
 * プロンプトだけに頼らない。ここは、抑えきれなかったぶんを落とす最後の関門。
 */
class ClaudeEngineCleanTest {

  @Test
  @DisplayName("思考タグを落とすこと")
  void stripsThinkingTags() {
    String raw = "<thinking>React について2段目を聞こう</thinking>その選定で、何を捨てましたか。";
    assertEquals("その選定で、何を捨てましたか。", ClaudeEngine.clean(raw));
  }

  @Test
  @DisplayName("閉じていないタグも落とすこと")
  void stripsStrayTags() {
    assertFalse(ClaudeEngine.clean("<answer>なぜ選んだのですか。").contains("<"));
  }

  @Test
  @DisplayName("鍵括弧の囲みを外すこと")
  void unwrapsQuotes() {
    assertEquals("なぜ選んだのですか。", ClaudeEngine.clean("「なぜ選んだのですか。」"));
    assertEquals("Why did you choose it?", ClaudeEngine.clean("\"Why did you choose it?\""));
  }

  @Test
  @DisplayName("文章中の不等号を壊さないこと")
  void keepsInlineComparisons() {
    // 「レイテンシが 100ms < 200ms だったので」のような文を壊してはいけない。
    String s = "応答時間が 100ms < 200ms だった理由を教えてください。";
    assertEquals(s, ClaudeEngine.clean(s));
  }

  @Test
  @DisplayName("前後の空白を落とすこと")
  void trims() {
    assertEquals("はい。", ClaudeEngine.clean("\n\n  はい。  \n"));
  }

  @Test
  @DisplayName("普通の発言はそのまま通すこと")
  void leavesPlainTextAlone() {
    String s = "PostgreSQL を選んだのは、なぜですか。";
    assertEquals(s, ClaudeEngine.clean(s));
  }
}
