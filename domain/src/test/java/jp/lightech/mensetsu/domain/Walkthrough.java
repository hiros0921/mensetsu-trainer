package jp.lightech.mensetsu.domain;

import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InterviewMachine;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Outcome;
import jp.lightech.mensetsu.domain.interview.Phase;
import jp.lightech.mensetsu.domain.interview.PhaseTransition;
import jp.lightech.mensetsu.domain.interview.Question;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.interview.TermResult;
import jp.lightech.mensetsu.domain.stub.StubEngine;

/**
 * スタブだけで面接を通し、やりとりを表示する。
 *
 * <pre>
 *   ./gradlew :domain:walkthrough
 * </pre>
 *
 * <p>テストは「通ったかどうか」しか教えてくれない。どんな面接になっているかは、
 * 実際に並べて読まないと分からない。ラウンド数が長すぎないか、掘りが噛み合って
 * いるか、圧の動きが不自然でないか。数字だけでは判断できない。
 *
 * <p>Spring も DB も LLM も使わない。ここに出るものが、ステートマシンだけで
 * 成立している面接の全体。
 */
public final class Walkthrough {

  private static final String GOOD =
      "私が React を選びました。3人のチームで、2か月の納期に対して学習コストが最も低いと判断したためです。";
  private static final String EMPTY = "モダンだからです。";

  public static void main(String[] args) {
    run("エンジニア面接／具体的に答え続ける", Mode.ENGINEER, InterviewerProfile.engineerStandard(), GOOD);
    run("エンジニア面接／中身の無い回答", Mode.ENGINEER, InterviewerProfile.engineerStandard(), EMPTY);
    run("圧迫面接／中身の無い回答", Mode.PRESSURE, InterviewerProfile.pressureHard(), EMPTY);
    run("圧迫面接／具体的に答え続ける", Mode.PRESSURE, InterviewerProfile.pressureHard(), GOOD);
    run("英語面接／具体的に答え続ける", Mode.ENGLISH, InterviewerProfile.englishStandard(), GOOD);
  }

  private static void run(String title, Mode mode, InterviewerProfile profile, String answer) {
    System.out.println();
    System.out.println("=".repeat(78));
    System.out.println("  " + title);
    System.out.println("=".repeat(78));

    InterviewMachine machine = new InterviewMachine(new StubEngine());
    Step s = machine.begin(mode, profile);
    printQuestion(s.state());

    int guard = 0;
    while (!s.state().isFinished() && guard++ < 40) {
      Phase before = s.state().phase();
      s = machine.submit(s.state(), Answer.of(answer));

      System.out.println("                 回答  : " + shorten(answer));

      PhaseTransition t = s.transition();
      if (t.to() != before) {
        System.out.printf("      ---- %s → %s ／ %s ／ %s%n", before, t.to(), t.reason(), t.detail());
      }
      if (!s.state().isFinished()) {
        printQuestion(s.state());
      }
    }
    printOutcome(mode, s.state().result().orElseThrow());
  }

  private static void printQuestion(InterviewState s) {
    Question q = s.pendingQuestion().orElseThrow();
    String dig = q.depth() > 0 ? "  <%s %d段目>".formatted(q.topic(), q.depth()) : "";
    System.out.printf("  [%-8s 圧%3d] 面接官: %s%s%n", s.phase(), s.pressure(), q.text(), dig);
  }

  private static void printOutcome(Mode mode, Outcome o) {
    System.out.println("  " + "-".repeat(74));
    System.out.printf(
        "  往復 %d ／ 圧 最高%d・最終%d ／ 到達段 %d ／ 答え切れず %d件 ／ 無言 %d回%n",
        o.turnCount(), o.pressurePeak(), o.pressureFinal(),
        o.deepestAnswered(), o.failedTerms(), o.silentAnswers());

    if (o.terms().isEmpty()) {
      System.out.println("  掘った用語: なし（技術用語が出てこなかった）");
    } else {
      System.out.println("  掘った用語:");
      for (TermResult r : o.terms()) {
        System.out.printf(
            "      %-12s %d段投げて %d段答えた%s%n",
            r.term(), r.askedDepth(), r.answeredDepth(), r.failed() ? "   ← 答え切れず" : "");
      }
    }
    if (mode.hasPressurePhase()) {
      String verdict =
          o.brokenByPressure() ? "押し切られた"
              : o.survivedPressure() ? "耐え切った"
                  : "PRESSURE に入らなかった";
      System.out.println("  圧迫: " + verdict);
    }
    System.out.println("  判定（S/A/B/C/D）は第5段階で決めるので、ここには出ない。");
  }

  private static String shorten(String s) {
    return s.length() <= 44 ? s : s.substring(0, 44) + "…";
  }

  private Walkthrough() {}
}
