package jp.lightech.mensetsu.domain.interview;

import java.util.List;

/**
 * 面接1回の結果。RESULT に到達した時点で確定する。
 *
 * <h2>【重要】ここに判定（S/A/B/C/D）は入っていません</h2>
 *
 * 入っているのは事実だけ。何往復したか、何段まで答えられたか、圧はどこまで
 * 上がったか、何回黙ったか。
 *
 * <p>これを何点として、どの判定にするかは第5段階で決める。仕様書7章
 * 「スコアリングの重み・5段階の閾値を、AIが独自に決定しないこと」。
 * 事実の収集と、値踏みを分けてある。
 *
 * <p>分けておくと、基準を変えたときに過去のセッションを再評価できる。
 * ここに判定まで焼き込むと、基準を変えるたびに面接をやり直すことになる。
 *
 * @param turnCount 往復数。
 * @param pressurePeak 圧がいちばん高かったところ。
 * @param pressureFinal 終わったときの圧。表情の最終状態にも使う。
 * @param terms 掘った用語ごとの結果。
 * @param deepestAnswered 到達したいちばん深い段。
 * @param failedTerms 答え切れなかった用語の数。
 * @param survivedPressure 圧迫を耐え切ったか。圧迫モード以外では false。
 * @param brokenByPressure 押し切られたか。
 * @param silentAnswers 何も言えなかった回数。
 * @param totalSilenceMs 詰まっていた時間の合計。
 */
public record Outcome(
    int turnCount,
    int pressurePeak,
    int pressureFinal,
    List<TermResult> terms,
    int deepestAnswered,
    long failedTerms,
    boolean survivedPressure,
    boolean brokenByPressure,
    int silentAnswers,
    int totalSilenceMs) {

  public Outcome {
    terms = terms == null ? List.of() : List.copyOf(terms);
  }
}
