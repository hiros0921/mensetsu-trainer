package jp.lightech.mensetsu.domain.port;

/**
 * STAR構造の観察（仕様書4-3）。
 *
 * <p>英語面接で「構造立てて答える」練習をするための軸。4つの要素が入っているかを見る。
 *
 * <h2>【重要】これも観察であって判断ではない</h2>
 *
 * 「4つ揃っているか」までを答える。4つ揃っていることが良いのか、3つでも十分なのかは
 * スコアリングが決める。ここで点にしない。
 *
 * <h2>英語面接以外では観察しない</h2>
 *
 * 毎回聞くと、その分だけ観察が遅くなり、費用も増える。STAR は英語面接モードの軸なので、
 * そのモードでだけ聞く。観察していない場合は {@link #notObserved()} を使う。
 *
 * @param situation どういう状況だったか
 * @param task 何をすべきだったか
 * @param action 自分が何をしたか
 * @param result どうなったか
 * @param observed 観察したか。false なら4つの値に意味は無い
 */
public record Star(
    boolean situation, boolean task, boolean action, boolean result, boolean observed) {

  /** 観察していない。英語面接以外のモード。 */
  public static Star notObserved() {
    return new Star(false, false, false, false, false);
  }

  public static Star of(boolean situation, boolean task, boolean action, boolean result) {
    return new Star(situation, task, action, result, true);
  }

  /** 揃っている数。0〜4。観察していなければ 0。 */
  public int count() {
    if (!observed) {
      return 0;
    }
    int n = 0;
    if (situation) {
      n++;
    }
    if (task) {
      n++;
    }
    if (action) {
      n++;
    }
    if (result) {
      n++;
    }
    return n;
  }

  /** 欠けている要素を並べる。画面に出して、次に何を足せばよいかを伝えるため。 */
  public String missing() {
    if (!observed) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    if (!situation) {
      b.append("状況");
    }
    if (!task) {
      appendSep(b).append("課題");
    }
    if (!action) {
      appendSep(b).append("行動");
    }
    if (!result) {
      appendSep(b).append("結果");
    }
    return b.toString();
  }

  private static StringBuilder appendSep(StringBuilder b) {
    if (b.length() > 0) {
      b.append("・");
    }
    return b;
  }
}
