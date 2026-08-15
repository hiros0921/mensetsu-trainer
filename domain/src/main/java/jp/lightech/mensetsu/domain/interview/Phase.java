package jp.lightech.mensetsu.domain.interview;

/**
 * 面接のフェーズ（仕様書3章）。
 *
 * <p>ここが仕様書3章の言う「不可逆のフェーズ」。普通のチャットボットは、どの発言も
 * 対等に扱う。面接は違う。INTRO で言ったことを PROBE で掘り、PROBE の矛盾を
 * PRESSURE で突く。前が後ろに効く。
 *
 * <h2>なぜ第1段階で提案した sealed interface をやめたか</h2>
 *
 * 第1段階では {@code sealed interface Phase} を提案した。実装に入って、enum に変えた。
 * 理由は2つ。
 *
 * <ul>
 *   <li>enum は DB の text 値と1対1で対応する。sealed interface だと、型と保存値の
 *       対応表をどこかに書くことになり、書けばずれる。
 *   <li>網羅性の検査は enum でも効く。default を書かない switch 式にすれば、
 *       フェーズを足した瞬間にコンパイルが通らなくなる。sealed にする理由が、
 *       この目的では無かった。
 * </ul>
 *
 * <p>sealed が要るのは、状態ごとに違うデータを持たせたいとき。今回、フェーズが
 * 持つのは名前だけで、進行の状態は {@link InterviewState} 側にある。
 */
public enum Phase {
  /** 導入・自己紹介。 */
  INTRO,
  /** 深掘り。ここを複数ラウンド繰り返す。 */
  PROBE,
  /** 圧・矛盾の指摘。モードにより有無。 */
  PRESSURE,
  /** 逆質問。 */
  REVERSE,
  /** 終了の挨拶。 */
  CLOSING,
  /** 評価表示。終端。 */
  RESULT;

  /** 終端かどうか。ここに入ったら、もう回答を受け付けない。 */
  public boolean isTerminal() {
    return this == RESULT;
  }
}
