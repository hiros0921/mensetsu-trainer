package jp.lightech.mensetsu.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.lightech.mensetsu.app.store.SessionStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 過去の面接を返す。
 *
 * <h2>【重要】中断した面接は混ぜない（第1段階 Q4）</h2>
 *
 * 諏訪の指示:
 *
 * <blockquote>未完了のまま RESULT に到達していないセッションが、履歴やスコア集計に
 * 混ざらないようにしてください。</blockquote>
 *
 * <p>弾いているのは DB のビュー（{@code completed_sessions}）。ここで条件を書くと、
 * 別の画面を足した人が同じ条件を書き忘れる。SQL の側に持たせてある。
 *
 * <p>ただし<b>あったことは見せる</b>。中断が何件あったかは返す。混ぜないことと、
 * 無かったことにするのは違う。
 */
@RestController
public class HistoryController {

  private final SessionStore store;
  private final ObjectMapper json;

  public HistoryController(SessionStore store, ObjectMapper json) {
    this.store = store;
    this.json = json;
  }

  @GetMapping("/api/history")
  public Map<String, Object> history(@RequestParam(defaultValue = "20") int limit) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> r : store.completedHistory(Math.clamp(limit, 1, 100))) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", String.valueOf(r.get("public_id")));
      m.put("mode", r.get("mode"));
      m.put("startedAt", String.valueOf(r.get("started_at")));
      m.put("grade", r.get("grade"));
      m.put("total", r.get("total"));
      m.put("version", r.get("threshold_version"));
      // 内訳は jsonb。文字列のまま返すと画面で二重にパースすることになる。
      m.put("axes", readAxes(r.get("breakdown")));
      rows.add(m);
    }
    return Map.of("sessions", rows, "abandoned", store.abandonedCount());
  }

  private Object readAxes(Object breakdown) {
    if (breakdown == null) {
      return List.of();
    }
    try {
      return json.readTree(breakdown.toString());
    } catch (Exception e) {
      // 読めない内訳より、履歴が出ないほうが困る。空で返して一覧は生かす。
      return List.of();
    }
  }
}
