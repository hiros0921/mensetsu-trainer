package jp.lightech.mensetsu.domain.interview;

/**
 * 面接官の表情（仕様書6章）。
 *
 * <h2>圧だけで決めないこと</h2>
 *
 * 仕様書6章に「圧迫モード以外でも、回答内容に応じて表情が変わること（関心 / 訝しむ）」とある。
 * エンジニア面接では圧がほとんど動かないので、圧だけで切り替えると表情が固まったままになる。
 *
 * <p>だから圧に加えて「直前の回答に中身があったか」も見る。これで、圧が低いままでも
 * 「関心」と「訝しむ」が出る。
 *
 * <h2>画像はここに書かない</h2>
 *
 * このクラスが持つのは表情の種類だけ。どのファイルを表示するかは画面側の設定。
 * 差し替え可能にするため（仕様書6章183行）。
 */
public enum Expression {
  /** 平常。 */
  CALM("平常"),
  /** 関心。中身のある回答が返ってきた。 */
  INTERESTED("関心"),
  /** 訝しむ。中身が薄い、または空気が硬くなってきた。 */
  DOUBTFUL("訝しむ"),
  /** 厳しい。圧が高い。 */
  STERN("厳しい"),
  /** 好意的。空気が和らぎ、かつ中身のある回答が続いている。 */
  FAVORABLE("好意的");

  private final String label;

  Expression(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** 画面が読むファイル名の元。{@code /img/calm.svg} のように組み立てる。 */
  public String fileKey() {
    return name().toLowerCase();
  }
}
