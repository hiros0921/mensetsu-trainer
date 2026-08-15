package jp.lightech.mensetsu.domain.port;

import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.Question;

/**
 * 面接官の発言を作る役。実装は LLM だったりスタブだったりする（仕様書8章②）。
 *
 * <h2>なぜインターフェースにするか</h2>
 *
 * このプロジェクトの技術的な主張はステートマシンであって、LLM 連携ではない
 * （仕様書3章）。LLM は手段。手段を差し替えられる形にしておかないと、
 * 「主役はどちらか」が構造に現れない。
 *
 * <p>実務上も要る。LLM を呼ぶテストは、遅く・不安定で・課金される。ステートマシンの
 * 検証を、そこに引きずられて壊れる作りにしたくない。
 *
 * <h2>ここに置いてある理由（依存の向き）</h2>
 *
 * このインターフェースは domain 側にあり、実装は app 側にある。domain は app を
 * 知らない。逆にすると、ステートマシンが HTTP クライアントに依存することになり、
 * 「フレームワークなしで単体テストできる」が成立しなくなる。
 *
 * <h2>失敗したときのこと</h2>
 *
 * LLM の呼び出しは失敗する（レート制限、タイムアウト、応答が壊れている）。
 * 面接の途中で例外が飛んで落ちるのは、体験として最悪。
 *
 * <p>だから、このインターフェースは例外を投げない約束にする。作れなければ、
 * 実装側が相槌（{@link Question#canned}）を返してつなぐ。失敗したこと自体は
 * app 側が engine_calls に記録する。ステートマシンは、失敗を知らなくても進める。
 */
public interface InterviewerEngine {

  /**
   * 次の発言を作る。
   *
   * <p>今どのフェーズか、圧はいくつか、何を何段目まで掘っているかは、すべて
   * {@code state} に入っている。実装がそこから次のフェーズを決めてはいけない。
   * 決めるのはステートマシン。
   */
  Question nextQuestion(InterviewState state);

  /**
   * 回答を観察する。
   *
   * <p>返すのは観察であって判断ではない。{@link Analysis} を参照。
   */
  Analysis analyzeAnswer(Answer answer, InterviewState state);

  /** どの実装か。DB の engine_kind に入る。スタブで作ったデータと本物を区別する。 */
  String kind();
}
