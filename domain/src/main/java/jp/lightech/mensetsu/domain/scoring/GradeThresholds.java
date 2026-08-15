package jp.lightech.mensetsu.domain.scoring;

/**
 * 5段階の境目。
 *
 * <p>合計点が {@code s} 以上なら S、{@code a} 以上なら A、というふうに上から見る。
 * どれにも当たらなければ D。
 *
 * @param s S（即内定）の下限
 * @param a A（内定）の下限
 * @param b B（保留）の下限
 * @param c C（見送り）の下限。これを下回れば D
 */
public record GradeThresholds(int s, int a, int b, int c) {

  public GradeThresholds {
    if (!(s > a && a > b && b > c && c >= 0 && s <= 100)) {
      // 順序が壊れていると、到達できない段階ができる。
      // たとえば A の下限が S の下限より高いと、A が一度も出ない。
      throw new IllegalArgumentException(
          "境目の順序が壊れている: S%d > A%d > B%d > C%d であること".formatted(s, a, b, c));
    }
  }

  public Grade gradeOf(int total) {
    if (total >= s) {
      return Grade.S;
    }
    if (total >= a) {
      return Grade.A;
    }
    if (total >= b) {
      return Grade.B;
    }
    if (total >= c) {
      return Grade.C;
    }
    return Grade.D;
  }

  public String describe() {
    return "S%d以上 / A%d以上 / B%d以上 / C%d以上 / それ未満はD".formatted(s, a, b, c);
  }
}
