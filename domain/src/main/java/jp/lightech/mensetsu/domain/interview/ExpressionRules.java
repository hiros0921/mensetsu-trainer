package jp.lightech.mensetsu.domain.interview;

/**
 * 圧と回答の中身から、面接官の表情を決める。
 *
 * <h2>【重要】この境目も案です</h2>
 *
 * 圧の設定（{@link PressureConfig}）と同じで、面接の見え方を決めてしまう数値。
 * 私が黙って決めない。ただし仕様書6章に「ここに時間を使わないこと」とあるので、
 * 案は1つだけ用意し、境目を渡せる形にしてある。
 *
 * <p>圧の設定を変えると、表情の出方も変わる。圧を決めてから、こちらを調整するのが順番。
 *
 * @param sternAt これ以上なら「厳しい」
 * @param doubtfulAt これ以上なら「訝しむ」
 * @param favorableUnder これ未満で、かつ中身のある回答が続いていれば「好意的」
 * @param favorableStreak 「好意的」になるのに要る、中身のある回答の連続数
 */
public record ExpressionRules(
    int sternAt, int doubtfulAt, int favorableUnder, int favorableStreak) {

  public ExpressionRules {
    if (!(sternAt > doubtfulAt && doubtfulAt > favorableUnder && favorableUnder >= 0)) {
      // 順序が壊れると、到達できない表情ができる。
      throw new IllegalArgumentException(
          "境目の順序が壊れている: 厳しい%d > 訝しむ%d > 好意的%d であること"
              .formatted(sternAt, doubtfulAt, favorableUnder));
    }
    if (favorableStreak < 1) {
      throw new IllegalArgumentException("好意的になる連続数は1以上: " + favorableStreak);
    }
  }

  /**
   * 案E1。圧の暫定値（0〜100）を前提にした境目。
   *
   * <p>圧迫面接官の初期値が55なので、開始時は「訝しむ」から入る。
   * エンジニア面接官の初期値は20なので「平常」から入る。
   */
  public static ExpressionRules proposalE1() {
    return new ExpressionRules(75, 45, 25, 3);
  }

  /**
   * 表情を1つ選ぶ。
   *
   * <h2>「好意的」は1回では出さない</h2>
   *
   * 最初は「圧が低く、中身のある回答」で好意的にしていた。テストで見つけたが、
   * エンジニア面接では圧がほぼ25未満（実測: 20→4→0→2）なので、
   * 中身のある回答はすべて「好意的」になり、<b>「関心」と「平常」が一度も出なかった</b>。
   *
   * <p>実際の面接官も、1回良い答えをしただけでは好意的にならない。続いて初めて空気が変わる。
   * 「関心」は1つの回答に対する反応、「好意的」は流れに対する反応。意味が違う。
   *
   * @param pressure 今の圧
   * @param answerWasSubstantive 直前の回答に中身があったか
   * @param technicalTermAppeared 直前の回答に技術の名前が出たか
   * @param substantiveStreak 中身のある回答が何回続いているか
   */
  public Expression pick(
      int pressure,
      boolean answerWasSubstantive,
      boolean technicalTermAppeared,
      int substantiveStreak) {
    if (pressure >= sternAt) {
      return Expression.STERN;
    }
    if (!answerWasSubstantive) {
      // 【重要】中身が無ければ、圧が低くても訝しむ。
      // 仕様書6章「圧迫モード以外でも、回答内容に応じて表情が変わること」。
      // 圧だけで切り替えると、エンジニア面接では表情が固まったままになる。
      return Expression.DOUBTFUL;
    }
    if (pressure >= doubtfulAt) {
      return Expression.DOUBTFUL;
    }
    if (pressure < favorableUnder && substantiveStreak >= favorableStreak) {
      return Expression.FAVORABLE;
    }
    return technicalTermAppeared ? Expression.INTERESTED : Expression.CALM;
  }

  /** 面接が始まった時点。まだ回答がないので、圧だけで決める。 */
  public Expression atStart(int pressure) {
    if (pressure >= sternAt) {
      return Expression.STERN;
    }
    if (pressure >= doubtfulAt) {
      return Expression.DOUBTFUL;
    }
    return Expression.CALM;
  }
}
