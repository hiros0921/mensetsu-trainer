package jp.lightech.mensetsu.domain.interview;

/**
 * 技術用語1つを掘り終えた結果（仕様書4-1）。
 *
 * @param term 掘った用語。「React」など。
 * @param askedDepth 何段まで問いを投げたか。
 * @param answeredDepth 実質的に答えられた、いちばん深い段。1段目で「モダンだから
 *     です」と答えたなら 0。
 * @param maxDepth その面接官が掘ると決めていた段数。
 */
public record TermResult(String term, int askedDepth, int answeredDepth, int maxDepth) {

  /**
   * 答え切れなかったか。
   *
   * <p>仕様書4-1「3段掘って答え切れなければ、知識が浅いと判定する」。
   * 判定するのは「最後まで答えられたかどうか」であって、途中で詰まったかどうかでは
   * ない。1段目で詰まっても、2段目で答え直せたなら、そこまでは分かっている。
   */
  public boolean failed() {
    return answeredDepth < maxDepth;
  }
}
