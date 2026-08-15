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
 * @param conciseMinChars これより短い回答は「答えていない」とみなす下限。
 * @param conciseMaxChars これより長い回答は「冗長」とみなす上限。
 * @param silenceToleranceMs 1回の回答で、ここまでの詰まりは減点しない。
 */
public record AxisParams(int conciseMinChars, int conciseMaxChars, long silenceToleranceMs) {

  public AxisParams {
    if (conciseMinChars < 0 || conciseMaxChars <= conciseMinChars) {
      throw new IllegalArgumentException(
          "簡潔さの帯が壊れている: %d〜%d".formatted(conciseMinChars, conciseMaxChars));
    }
    if (silenceToleranceMs < 0) {
      throw new IllegalArgumentException("沈黙の許容が負: " + silenceToleranceMs);
    }
  }
}
