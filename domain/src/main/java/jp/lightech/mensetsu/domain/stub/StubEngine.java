package jp.lightech.mensetsu.domain.stub;

import java.util.List;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.Question;
import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.InterviewerEngine;
import jp.lightech.mensetsu.domain.port.Specificity;

/**
 * LLM を呼ばない実装（仕様書8章②「テスト用のスタブ実装も必ず用意すること」）。
 *
 * <h2>何のためにあるか</h2>
 *
 * <ol>
 *   <li>第3段階で、ステートマシンだけを検証する。LLM の揺らぎを排除する。
 *   <li>自動テストで使う。LLM を呼ぶテストは、遅く・不安定で・課金される。
 *   <li>API キーが無くても動くことを担保する。
 * </ol>
 *
 * <h2>観察の作り方</h2>
 *
 * 回答の文字列を、決まった規則で見るだけ。LLM の代わりをしようとしていない。
 * 「それらしく」振る舞わせると、スタブで通ったのに本物で落ちる、という一番困る
 * 状態になる。ここは規則が単純で予測できることのほうが大事。
 *
 * <p>規則は {@link StubRules} に分けてある。特定の経路（3段掘って答え切れない、
 * 圧が上がり続ける）を狙って再現できるようにするため。これが無いと第6・第7段階の
 * 検証ができない。
 */
public final class StubEngine implements InterviewerEngine {

  private final StubRules rules;

  public StubEngine() {
    this(StubRules.byKeyword());
  }

  public StubEngine(StubRules rules) {
    this.rules = rules;
  }

  @Override
  public String kind() {
    return "STUB";
  }

  @Override
  public Question nextQuestion(InterviewState state) {
    return switch (state.phase()) {
      case INTRO -> Question.generated("自己紹介をお願いします。");
      case PROBE -> probeQuestion(state);
      case PRESSURE ->
          Question.generated(
              "先ほどのお話ですが、それはご自身の判断ですか。（圧 %d）".formatted(state.pressure()));
      case REVERSE -> Question.generated("最後に、何かご質問はありますか。");
      case CLOSING -> Question.generated("本日は以上です。ありがとうございました。");
      case RESULT -> throw new IllegalStateException("RESULT では質問を作らない");
      // default を書かない。フェーズを足したらここがコンパイルエラーになる。
    };
  }

  private Question probeQuestion(InterviewState state) {
    var probe = state.probe();
    if (!probe.hasCurrent()) {
      // 掘る対象が無い。ここに来るのは、回答に技術用語が1つも出てこなかったとき。
      return Question.generated("直近のお仕事で、technical に判断したことを教えてください。");
    }
    String term = probe.currentTerm();
    int depth = probe.askedDepth();
    // 段が深くなるほど、逃げにくい問いにする。仕様書4-1の例に沿った形。
    String text =
        switch (Math.min(depth, 3)) {
          case 1 -> "%s を選んだのは、なぜですか。".formatted(term);
          case 2 -> "その選定で、何を捨てましたか。".formatted();
          default -> "%s を使わない判断は、どこで検討しましたか。".formatted(term);
        };
    return Question.probe(text, term, depth);
  }

  @Override
  public Analysis analyzeAnswer(Answer answer, InterviewState state) {
    if (answer.isSilent()) {
      return Analysis.empty("無言または打ち切り");
    }
    String text = answer.text();
    List<String> terms = rules.extractTerms(text);
    Specificity spec =
        new Specificity(
            rules.hasNumber(text), rules.hasProperNoun(text), rules.isFirstPerson(text));
    boolean substantive = rules.isSubstantive(text);
    boolean contradiction = rules.contradicts(text, state);

    return new Analysis(
        terms,
        spec,
        substantive,
        contradiction,
        contradiction ? "以前の回答" : "",
        "StubEngine による規則ベースの観察");
  }
}
