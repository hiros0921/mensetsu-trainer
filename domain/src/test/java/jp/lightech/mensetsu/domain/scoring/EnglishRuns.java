package jp.lightech.mensetsu.domain.scoring;

import java.util.List;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InputMethod;
import jp.lightech.mensetsu.domain.interview.InterviewMachine;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.InterviewerEngine;
import jp.lightech.mensetsu.domain.port.Specificity;
import jp.lightech.mensetsu.domain.port.Star;
import jp.lightech.mensetsu.domain.stub.StubEngine;

/**
 * 語数と STAR を狙って与えるための道具。
 *
 * <h2>なぜスタブでは足りないか</h2>
 *
 * {@link StubEngine} は回答の文字列から観察を作る。語数を80、STARを「結果だけ欠け」に
 * したい、といった細かい指定ができない。
 *
 * <p>簡潔さの測り方を確かめたいだけなので、観察をそのまま渡せる面接官を用意する。
 * これはスタブの代わりではなく、スタブの手前にある「観察を固定した面接官」。
 */
final class EnglishRuns {

  /** 1往復ぶんの観察。 */
  record Turn(int wordCount, Star star) {}

  private EnglishRuns() {}

  /** 与えた観察がそのまま返る面接で、英語面接を1回通す。 */
  static InterviewState run(List<Turn> turns) {
    InterviewMachine machine = new InterviewMachine(new Fixed(turns));
    Step s = machine.begin(Mode.ENGLISH, InterviewerProfile.englishStandard());
    int i = 0;
    while (!s.state().isFinished() && i < 20) {
      i++;
      s = machine.submit(
          s.state(),
          new Answer("answer " + i, InputMethod.VOICE, 40_000, 1_000, false));
    }
    return s.state();
  }

  /** 観察を固定した面接官。発言はスタブに任せる。 */
  private static final class Fixed implements InterviewerEngine {
    private final List<Turn> turns;
    private final StubEngine questions = new StubEngine();
    private int i = 0;

    Fixed(List<Turn> turns) {
      this.turns = turns;
    }

    @Override
    public String kind() {
      return "STUB";
    }

    @Override
    public jp.lightech.mensetsu.domain.interview.Question nextQuestion(InterviewState state) {
      return questions.nextQuestion(state);
    }

    @Override
    public Analysis analyzeAnswer(Answer answer, InterviewState state) {
      Turn t = turns.get(Math.min(i++, turns.size() - 1));
      return new Analysis(
          List.of(),
          new Specificity(true, true, true),
          true,
          false,
          "",
          "固定の観察",
          t.star(),
          t.wordCount());
    }
  }
}
