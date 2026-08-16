package jp.lightech.mensetsu.app.web;

import jp.lightech.mensetsu.app.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 開いたまま放置された面接を、中断として片付ける。
 *
 * <h2>なぜ要るか</h2>
 *
 * 切断は {@code afterConnectionClosed} で拾っているが、<b>拾えない切れ方がある</b>。
 * アプリを落とす、端末が落ちる、回線が切れる。実測で残っていた: RUNNING のまま
 * 1日以上経った行が1件あり、これは「切断されたセッションは中断として記録すること」
 * （第1段階 Q4）を満たしていない。
 *
 * <p>履歴は {@code completed_sessions} ビューで弾いているので、放置されていても
 * 集計は汚れない。それでも直すのは、<b>記録として嘘になる</b>から。
 * 実際は誰も答えていない面接が、いつまでも「進行中」に見える。
 *
 * <h2>閾値は30分。これは私の判断です</h2>
 *
 * 面接は長くても15分ほどなので、間を空けて戻ってくる人を切らない長さにしました。
 * 採点の基準ではないので、こちらで決めています。変えたい場合は {@link #IDLE_MINUTES} だけ。
 */
@Component
public class StaleSessionSweeper {

  private static final Logger log = LoggerFactory.getLogger(StaleSessionSweeper.class);

  /** これだけ動きが無ければ中断とみなす。 */
  static final int IDLE_MINUTES = 30;

  private final SessionStore store;

  public StaleSessionSweeper(SessionStore store) {
    this.store = store;
  }

  /**
   * 起動したとき。
   *
   * <p>【重要】前回アプリを落とした時点で開いていた面接は、ここでしか片付かない。
   * 落ちた瞬間にはコードが動かないので、次に起きたときに拾う。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    sweep("起動時");
  }

  /** 動いている間。5分ごと。 */
  @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
  public void periodically() {
    sweep("定期");
  }

  private void sweep(String when) {
    int n = store.abandonStale(IDLE_MINUTES);
    if (n > 0) {
      log.info("{}: {}分動きの無い面接 {}件を中断として記録しました", when, IDLE_MINUTES, n);
    }
  }
}
