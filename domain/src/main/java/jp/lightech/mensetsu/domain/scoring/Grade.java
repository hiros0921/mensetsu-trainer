package jp.lightech.mensetsu.domain.scoring;

/**
 * 5段階の判定（仕様書7章）。
 *
 * <p>【重要】{@link #B} を必ず残すこと。通過／不通過の2択だと1回で飽きる、と仕様書にある。
 * 段階を減らす改修をするときは、ここを見ること。
 */
public enum Grade {
  S("即内定"),
  A("内定"),
  B("保留（あと一歩）"),
  C("見送り"),
  D("お祈り");

  private final String meaning;

  Grade(String meaning) {
    this.meaning = meaning;
  }

  public String meaning() {
    return meaning;
  }
}
