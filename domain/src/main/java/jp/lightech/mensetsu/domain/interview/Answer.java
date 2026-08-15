package jp.lightech.mensetsu.domain.interview;

import java.util.Objects;

/**
 * 利用者の回答1つ。
 *
 * <p>【重要】音声でもテキストでも、ここに入るのは文字列だけ（仕様書8章④）。
 * 音声はブラウザ内で文字列に変換され、サーバーへは文字列で届く。違うのは
 * {@code input} の値だけ。そうしておけば、音声が使えない環境にフォールバック
 * しても、ここから先が何も変わらない。
 *
 * @param text 回答の本文。無言のまま打ち切られたなら空。
 * @param input 入力方式。記録として残すだけで、進行の判断には使わない。
 * @param elapsedMs 質問が出てから回答が確定するまで。
 * @param silenceMs 入力（発話）が止まっていた時間。サーバーが計った値。
 * @param timedOut 制限時間切れで打ち切られたか。
 */
public record Answer(
    String text, InputMethod input, int elapsedMs, int silenceMs, boolean timedOut) {

  public Answer {
    Objects.requireNonNull(input, "input");
    text = text == null ? "" : text;
    if (elapsedMs < 0 || silenceMs < 0) {
      throw new IllegalArgumentException("時間が負: elapsed=" + elapsedMs + " silence=" + silenceMs);
    }
  }

  /** 試験で使う、時間を気にしない回答。 */
  public static Answer of(String text) {
    return new Answer(text, InputMethod.TEXT, 0, 0, false);
  }

  /** 何も言えなかった。制限時間切れ・無言での打ち切り。 */
  public boolean isSilent() {
    return text.isBlank();
  }
}
