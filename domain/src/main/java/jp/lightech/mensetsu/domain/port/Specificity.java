package jp.lightech.mensetsu.domain.port;

/**
 * 具体性の観察（仕様書4-2・7章）。
 *
 * <p>3つとも「有無」であって、良し悪しではない。良し悪しの重みづけは
 * 第5段階でスコアリングが決める。ここで点数にしてしまうと、基準を変えたときに
 * 過去の観察まで作り直すことになる。
 *
 * @param hasNumber 数字があるか。「3人で」「2か月で」「30%」。
 * @param hasProperNoun 固有名詞があるか。技術名・製品名・部署名。
 * @param firstPerson 自分の行動として語っているか。「私が」対「チームが」「〜と聞いています」。
 */
public record Specificity(boolean hasNumber, boolean hasProperNoun, boolean firstPerson) {

  public static Specificity none() {
    return new Specificity(false, false, false);
  }

  /** 3つとも揃っている。圧を下げる方向にいちばん効く回答。 */
  public boolean isConcrete() {
    return hasNumber && hasProperNoun && firstPerson;
  }

  /** 1つも無い。圧を上げる方向にいちばん効く回答。 */
  public boolean isVague() {
    return !hasNumber && !hasProperNoun && !firstPerson;
  }
}
