package jp.lightech.mensetsu.domain.interview;

import java.util.Objects;

/**
 * 面接官の発言1つ。
 *
 * @param text 画面に出す文言。
 * @param kind 定型か、生成か。{@link QuestionKind} を参照。
 * @param topic この発言が掘っている技術用語。掘っていなければ空。
 * @param depth その用語について何段目か。掘っていなければ 0。
 */
public record Question(String text, QuestionKind kind, String topic, int depth) {

  public Question {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(kind, "kind");
    topic = topic == null ? "" : topic;
    if (depth < 0) {
      throw new IllegalArgumentException("depth が負: " + depth);
    }
  }

  public static Question generated(String text) {
    return new Question(text, QuestionKind.GENERATED, "", 0);
  }

  /** 深掘り。何を何段目で掘っているかを持つ。 */
  public static Question probe(String text, String topic, int depth) {
    return new Question(text, QuestionKind.GENERATED, topic, depth);
  }

  /** 相槌・つなぎ。LLM を呼ばずに返すもの。 */
  public static Question canned(String text) {
    return new Question(text, QuestionKind.CANNED, "", 0);
  }
}
