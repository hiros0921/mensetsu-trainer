package jp.lightech.mensetsu.domain.interview;

import jp.lightech.mensetsu.domain.port.Analysis;

/**
 * 往復1つ。質問と、それに対する回答と、その観察。
 *
 * <p>DB の turns に対応する。
 *
 * <p>【重要】履歴を持つのは、仕様書3章の「前のフェーズの回答を、後のフェーズが
 * 参照できること」のため。INTRO で言ったことを PROBE で掘り、PROBE の矛盾を
 * PRESSURE で突く。これができないと、ただのチャットになる。
 *
 * @param turnNo セッション内の通し番号。1 から。
 * @param phase このやりとりが起きたフェーズ。
 * @param question 面接官の発言。
 * @param answer 利用者の回答。
 * @param analysis 回答の観察。
 */
public record Exchange(
    int turnNo, Phase phase, Question question, Answer answer, Analysis analysis) {}
