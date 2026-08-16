package jp.lightech.mensetsu.app.llm;

import java.util.List;
import jp.lightech.mensetsu.domain.interview.Exchange;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Phase;

/**
 * LLM に渡す文言の組み立て。
 *
 * <h2>【重要】ここに遷移の判断を書かない</h2>
 *
 * 仕様書3章「遷移条件をドメインロジックとして分離すること。LLMのプロンプトの中に遷移判断を
 * 埋め込まない」。
 *
 * <p>このクラスがプロンプトに書いてよいのは「今どういう状況か」と「どういう発言をしてほしいか」
 * だけ。「次はどのフェーズに行くべきか」「もう掘るのをやめるべきか」は一切書かない。
 * それを決めるのは {@link jp.lightech.mensetsu.domain.interview.InterviewMachine}。
 *
 * <p>この線引きが崩れると、面接の進行が「プロンプトの中の曖昧な指示」に依存するようになり、
 * 単体テストで確かめられなくなる。
 */
final class Prompts {

  private Prompts() {}

  /** 直近何往復ぶんを文脈として渡すか。全部渡すと長くなり、遅くなる。 */
  private static final int CONTEXT_TURNS = 6;

  /**
   * 面接官の役。全モードで共通の土台。
   *
   * <p>短く保つ。長い前置きは、応答の最初の文字が出るまでを遅くする。
   */
  static String system(InterviewState state) {
    StringBuilder b = new StringBuilder();
    b.append("あなたは中途採用の面接官です。相手は実務経験のあるエンジニアです。\n\n");

    b.append(switch (state.mode()) {
      case ENGINEER ->
          "技術選定の理由を確かめる面接です。相手が出した技術用語について、"
              + "なぜそれを選んだのか、何を捨てたのかを掘ります。\n";
      case PRESSURE ->
          "厳しめの面接です。曖昧な回答、数字の無い回答、自分の行動として語られていない回答には、"
              + "そこを突きます。人格を否定しないこと。詰めるのは中身だけです。\n";
      case ENGLISH ->
          "This is an interview conducted in English. Ask questions in English only. "
              + "Keep each question to one or two sentences.\n";
    });

    b.append("""

        発言の作り方:
        - 1回の発言は1〜2文。長い前置きを付けない
        - 質問は1つだけ。複数を並べない
        - 相手の回答に出てきた言葉を使う。一般論に逃げない
        - 評価やコメントを述べない。面接官は質問するだけ

        出力の形式:
        - 面接官の発言そのものだけを書く。見出し、箇条書き、鍵括弧を付けない
        - 内部用のタグや、考えた過程を出力に含めない
        """);
    return b.toString();
  }

  /**
   * 次の発言を作らせる指示。
   *
   * <p>今どのフェーズか、何を何段目まで掘っているか、圧はいくつか。事実だけを渡す。
   * 「次は〜すべき」とは書かない。
   */
  static String nextQuestion(InterviewState state) {
    StringBuilder b = new StringBuilder();
    b.append(history(state));
    b.append("\n---\n");

    switch (state.phase()) {
      case INTRO -> b.append("面接の冒頭です。自己紹介を求めてください。");
      case PROBE -> {
        var probe = state.probe();
        if (probe.hasCurrent()) {
          b.append(
              "「%s」について %d 段目の質問をしてください。".formatted(probe.currentTerm(), probe.askedDepth()));
          b.append(switch (Math.min(probe.askedDepth(), 3)) {
            case 1 -> "なぜそれを選んだのかを問います。";
            case 2 -> "その選定で何を捨てたのか、比較した対象は何かを問います。";
            default -> "使わない判断がありえたか、どこで検討したかを問います。";
          });
        } else {
          b.append("相手がまだ具体的な技術の話をしていません。"
              + "直近の仕事で技術的に判断したことを1つ挙げてもらってください。");
        }
      }
      case PRESSURE ->
          b.append("圧をかける場面です（現在の圧 %d／100）。".formatted(state.pressure())
              + "直前の回答の曖昧なところ、または以前の発言との食い違いを、短く突いてください。");
      case REVERSE -> b.append("面接の終わりです。逆質問があるかを尋ねてください。");
      case CLOSING -> b.append("面接を締めてください。挨拶だけで構いません。");
      case RESULT -> throw new IllegalStateException("RESULT では発言を作らない");
    }
    return b.toString();
  }

  /**
   * 回答を観察させる指示。
   *
   * <p>【重要】ここで求めるのは観察であって判断ではない。「次に進むべきか」「浅いと判定すべきか」は
   * 聞かない。聞くのは、数字があるか、固有名詞があるか、自分の行動として語っているか、
   * といった見れば分かることだけ。値踏みはドメイン層がやる。
   */
  static String analyze(InterviewState state, String answerText) {
    return """
        %s

        ---
        直前の質問: %s
        利用者の回答: %s

        この回答を観察してください。評価も判定もしないでください。見て分かることだけを答えます。

        - technicalTerms: 回答に出てきた技術の名前。製品名・言語名・サービス名。無ければ空
        - hasNumber: 数字が入っているか
        - hasProperNoun: 固有名詞が入っているか
        - firstPerson: 自分がやったこととして語っているか（「私が」「担当した」）。
          伝聞（「〜と聞いています」「チームが」）なら false
        - substantive: 直前の質問に実質的に答えているか。
          「モダンだからです」「なんとなく」のように中身が無ければ false
        - hasContradiction: これまでの発言と食い違っているか
        - contradictionWith: 何と食い違っているか。無ければ空文字
        - note: 所見。20字以内
        %s
        """
        .formatted(history(state), lastQuestion(state), answerText, starPart(state));
  }

  /**
   * 英語面接でだけ、語数と STAR構造を聞く（仕様書4-3）。
   *
   * <p>毎回聞くと、その分だけ観察が遅くなり、費用も増える。STAR は英語面接モードの軸なので、
   * そのモードでだけ聞く。
   */
  private static String starPart(InterviewState state) {
    if (state.mode() != Mode.ENGLISH) {
      return "- wordCount: 0（このモードでは数えません）\n"
          + "- starSituation / starTask / starAction / starResult: すべて false（このモードでは見ません）";
    }
    return """
        - wordCount: 回答の語数。空白で区切った単語の数
        - starSituation: 状況（どういう場面だったか）が述べられているか
        - starTask: 課題（何をすべきだったか）が述べられているか
        - starAction: 行動（自分が何をしたか）が述べられているか
        - starResult: 結果（どうなったか）が述べられているか

        STAR は「4つ揃っているか」を見るだけです。揃っていることが良いかどうかの判断はしません。        """;
  }

  private static String lastQuestion(InterviewState state) {
    return state.pendingQuestion().map(q -> q.text()).orElse("(なし)");
  }

  /**
   * これまでのやりとりを、直近ぶんだけ文字列にする。
   *
   * <p>仕様書3章「前のフェーズの回答を、後のフェーズが参照できること」。INTRO で言ったことを
   * PROBE で掘り、PROBE の矛盾を PRESSURE で突くために、履歴を渡す。
   *
   * <p>全部渡さないのは長さのため。長いほど最初の文字が出るまで遅くなる。
   */
  private static String history(InterviewState state) {
    List<Exchange> recent = state.recent(CONTEXT_TURNS);
    if (recent.isEmpty()) {
      return "これまでのやりとり: まだありません。";
    }
    StringBuilder b = new StringBuilder("これまでのやりとり:\n");
    for (Exchange e : recent) {
      b.append("面接官: ").append(e.question().text()).append('\n');
      b.append("相手  : ").append(e.answer().isSilent() ? "(無言)" : e.answer().text()).append('\n');
    }
    // 矛盾を突く場面では、もっと前まで見せる必要がある。
    if (state.phase() == Phase.PRESSURE && state.history().size() > CONTEXT_TURNS) {
      b.append("\n（それ以前のやりとりは省略。矛盾を突くなら、上に見えている範囲で）\n");
    }
    return b.toString();
  }

  /** 英語面接では、生成物も英語にする。system 側で指示済みだが、念のため確かめる用。 */
  static boolean isEnglish(Mode mode) {
    return mode == Mode.ENGLISH;
  }
}
