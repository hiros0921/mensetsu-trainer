package jp.lightech.mensetsu.domain.scoring;

import java.util.List;
import jp.lightech.mensetsu.domain.interview.InputMethod;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.port.Star;

/**
 * 英語面接モードの基準案を、同じ受験者に当てて比べる。
 *
 * <pre>
 *   ./gradlew :domain:englishpolicy
 * </pre>
 *
 * <h2>なぜ後から作ったか</h2>
 *
 * 第8段階では、英語面接の3案を<b>重みの表と理由だけ</b>で出していました。
 * エンジニア面接（policycompare）と圧迫面接（pressurepolicy）では、同じ受験者を
 * 各案で採点した分布を見てから選んでいただいたのに、英語面接だけそれが無い。
 *
 * <p>諏訪の指摘「基準を選んだ前提が崩れていないか確認したい」に対して、
 * 英語面接だけは<b>比べる材料そのものが無かった</b>ので、ここで作りました。
 *
 * <h2>この表で見えること</h2>
 *
 * 案E-1 を落とした理由（テキスト入力だと沈黙35点が自動的に満点になる）が、
 * 言葉ではなく点数で出ます。同じ内容を音声で受けた場合とテキストで受けた場合を
 * 並べてあるので、差がそのまま「入力方式で動く点数」です。
 */
public final class EnglishPolicyCompare {

  private EnglishPolicyCompare() {}

  private static final Star FULL = Star.of(true, true, true, true);
  private static final Star NO_RESULT = Star.of(true, true, true, false);
  private static final Star ACTION_ONLY = Star.of(false, false, true, false);
  private static final Star NONE = Star.of(false, false, false, false);

  /** 受験者の型。 */
  private record Candidate(String label, List<EnglishRuns.Turn> turns, int silenceMs,
      boolean muteOne) {}

  private static final List<Candidate> CANDIDATES =
      List.of(
          new Candidate(
              "型どおりに話せている",
              turns(new int[] {90, 110, 95, 100, 85}, FULL),
              1_000,
              false),
          new Candidate(
              "話は長いが型がない",
              turns(new int[] {230, 260, 240, 220, 250}, ACTION_ONLY),
              1_000,
              false),
          new Candidate(
              "型はあるが詰まる",
              turns(new int[] {85, 95, 90, 80, 90}, NO_RESULT),
              7_000,
              true),
          new Candidate(
              "短く固まる", turns(new int[] {18, 22, 15, 20, 16}, NONE), 9_000, true));

  private static List<EnglishRuns.Turn> turns(int[] words, Star star) {
    return java.util.Arrays.stream(words)
        .mapToObj(w -> new EnglishRuns.Turn(w, star))
        .toList();
  }

  public static void main(String[] args) {
    List<ScoringPolicy> policies =
        List.of(
            ScoringPolicy.proposalEn1(), ScoringPolicy.proposalEn2(), ScoringPolicy.proposalEn3());

    line();
    System.out.println("  英語面接モードの基準案（採用は案E-2）");
    line();
    for (ScoringPolicy p : policies) {
      System.out.println("  " + p.describe().replace("\n", "\n  "));
    }

    line();
    System.out.println("  同じ受験者を各案で採点したとき（音声入力）");
    line();
    header(policies);
    for (Candidate c : CANDIDATES) {
      row(c.label(), score(c, InputMethod.VOICE), policies);
    }

    line();
    System.out.println("  同じ受験者が、テキスト入力で受けたとき");
    System.out.println("  （沈黙が発生しないので、沈黙の軸は満点に張り付く）");
    line();
    header(policies);
    for (Candidate c : CANDIDATES) {
      row(c.label(), scoreAsText(c), policies);
    }

    line();
    System.out.println("  入力方式を変えると、合計点が何点動くか");
    line();
    System.out.printf("  %-22s", "");
    for (ScoringPolicy p : policies) {
      System.out.printf("%-14s", shortName(p));
    }
    System.out.println();
    System.out.println("  " + "-".repeat(80));
    for (Candidate c : CANDIDATES) {
      InterviewState voice = score(c, InputMethod.VOICE);
      InterviewState text = scoreAsText(c);
      System.out.printf("  %-22s", c.label());
      for (ScoringPolicy p : policies) {
        int diff = total(p, text) - total(p, voice);
        System.out.printf("%-14s", (diff >= 0 ? "+" : "") + diff + "点");
      }
      System.out.println();
    }
    System.out.println();
    System.out.println("  ここが大きいほど、「何を話したか」より「どう入力したか」で判定が動く。");
    System.out.println("  案E-1 を落とした理由が、この行に出ています。");
    line();
  }

  private static InterviewState score(Candidate c, InputMethod input) {
    return EnglishRuns.run(c.turns(), input, c.silenceMs(), c.muteOne());
  }

  /** テキスト入力。落ち着いて書けるので沈黙は起きず、打ち切りも起きない。 */
  private static InterviewState scoreAsText(Candidate c) {
    return EnglishRuns.run(c.turns(), InputMethod.TEXT, 0, false);
  }

  private static Score evaluate(ScoringPolicy p, InterviewState state) {
    // 帯（語数の下限・上限）は案ごとに違うので、Scorer も案ごとに作る。
    return p.evaluate(new Scorer(p.params()).score(state));
  }

  private static int total(ScoringPolicy p, InterviewState state) {
    return evaluate(p, state).total();
  }

  private static void header(List<ScoringPolicy> policies) {
    System.out.printf("  %-22s", "");
    for (ScoringPolicy p : policies) {
      System.out.printf("%-14s", shortName(p));
    }
    System.out.println();
    System.out.println("  " + "-".repeat(80));
  }

  private static void row(String label, InterviewState state, List<ScoringPolicy> policies) {
    System.out.printf("  %-22s", label);
    for (ScoringPolicy p : policies) {
      Score s = evaluate(p, state);
      System.out.printf("%-14s", "%s %3d点".formatted(s.grade().name(), s.total()));
    }
    System.out.println();
  }

  private static String shortName(ScoringPolicy p) {
    return p.label().length() > 12 ? p.label().substring(0, 12) : p.label();
  }

  private static void line() {
    System.out.println("=".repeat(84));
  }
}
