package jp.lightech.mensetsu.domain.scoring;

/**
 * 素点を出すときに要る境目。
 *
 * <h2>【重要】ここの数値も諏訪さんが決めるものです</h2>
 *
 * 重みと閾値だけが判断ではない。「何文字までを簡潔とみなすか」「何秒の沈黙までを許すか」も、
 * 面接の合否を左右する判断そのもの。
 *
 * <p>ここを私が黙って決めて、重みと閾値だけを提案する形にすると、
 * 「AIが独自に決定しないこと」（仕様書7章）を実質的に破ることになる。だから
 * この3つも案として出し、選んでいただく。
 *
 * <p>実装側は、この値をどこにも埋め込んでいない。{@link Scorer} は渡された値を使うだけ。
 *
 * <h2>英語面接では「簡潔さ」の中身が変わります</h2>
 *
 * 仕様書4-3の判定軸は「回答の語数、詰まった回数、STAR構造」。日本語の面接で使っている
 * 「文字数の帯」は、英語には合いません。語の区切りが違うためです。
 *
 * <p>そこで、軸を6つに増やすのではなく、<b>「簡潔さ」の測り方を差し替える</b>形にしました。
 * 仕様書7章が定める5軸を変えずに済みます。この判断も案です。
 *
 * @param conciseMinChars これより短い回答は「答えていない」とみなす下限。
 * @param conciseMaxChars これより長い回答は「冗長」とみなす上限。
 * @param silenceToleranceMs 1回の回答で、ここまでの詰まりは減点しない。
 * @param useWordsAndStar 「簡潔さ」を、文字数ではなく語数とSTAR構造で測るか
 * @param wordMin 語数の下限（{@code useWordsAndStar} のときだけ使う）
 * @param wordMax 語数の上限
 */
public record AxisParams(
    int conciseMinChars,
    int conciseMaxChars,
    long silenceToleranceMs,
    boolean useWordsAndStar,
    int wordMin,
    int wordMax) {

  /** 文字数で測る従来の形。日本語の面接で使う。 */
  public AxisParams(int conciseMinChars, int conciseMaxChars, long silenceToleranceMs) {
    this(conciseMinChars, conciseMaxChars, silenceToleranceMs, false, 0, Integer.MAX_VALUE);
  }

  /** 語数とSTARで測る形。英語面接で使う。 */
  public static AxisParams words(int wordMin, int wordMax, long silenceToleranceMs) {
    return new AxisParams(0, Integer.MAX_VALUE, silenceToleranceMs, true, wordMin, wordMax);
  }

  public AxisParams {
    if (useWordsAndStar && (wordMin < 0 || wordMax <= wordMin)) {
      throw new IllegalArgumentException("語数の帯が壊れている: %d〜%d".formatted(wordMin, wordMax));
    }
    if (conciseMinChars < 0 || conciseMaxChars <= conciseMinChars) {
      throw new IllegalArgumentException(
          "簡潔さの帯が壊れている: %d〜%d".formatted(conciseMinChars, conciseMaxChars));
    }
    if (silenceToleranceMs < 0) {
      throw new IllegalArgumentException("沈黙の許容が負: " + silenceToleranceMs);
    }
  }
}
