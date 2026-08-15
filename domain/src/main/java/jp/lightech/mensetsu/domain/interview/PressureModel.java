package jp.lightech.mensetsu.domain.interview;

import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.Specificity;

/**
 * 圧の計算（仕様書4-2）。
 *
 * <p>【重要】判定は LLM、計算はここ。仕様書4-1の「掘る対象の抽出は LLM に任せて
 * よい。ただし何段目かのカウントはアプリ側で持つ」と同じ考え方を、圧にも当てはめる。
 *
 * <p>LLM が答えるのは「数字があるか」「自分の行動として語っているか」という
 * 観察だけ。それを何点として扱うかは、ここが決める。こうしておくと、圧の効き方を
 * 変えたいときにプロンプトを触らずに済む。プロンプトを変えると、圧以外の挙動まで
 * 一緒に動いてしまう。
 */
public final class PressureModel {

  private final PressureConfig config;

  public PressureModel(PressureConfig config) {
    this.config = config;
  }

  public PressureConfig config() {
    return config;
  }

  /**
   * 回答1つを受けて、新しい圧を返す。
   *
   * @param current 今の圧
   * @param answer 回答。無言かどうかを見る
   * @param analysis 観察
   */
  public int apply(int current, Answer answer, Analysis analysis) {
    // 無言は、内容の評価に進まない。何も言っていないので観察のしようがない。
    if (answer.isSilent()) {
      return config.clamp(current + config.riseSilent());
    }

    int delta = 0;
    Specificity s = analysis.specificity();

    // 上げる方向
    if (s.isVague()) {
      delta += config.riseVague();
    }
    if (!s.firstPerson()) {
      delta += config.riseNoFirstPerson();
    }

    // 下げる方向
    if (s.hasNumber()) {
      delta -= config.dropNumber();
    }
    if (s.hasProperNoun()) {
      delta -= config.dropProperNoun();
    }
    if (s.firstPerson()) {
      delta -= config.dropFirstPerson();
    }

    return config.clamp(current + delta);
  }

  /** 強制遷移の境目に達したか（仕様書4-2）。 */
  public boolean shouldForcePressure(int pressure) {
    return pressure >= config.forceAt();
  }

  /** 押し切られたか（負け）。 */
  public boolean isBroken(int pressure) {
    return pressure >= config.breakAt();
  }
}
