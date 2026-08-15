package jp.lightech.mensetsu.app;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第2段階の動作確認用。
 *
 * <p>「起動した」だけでは確認にならない。Spring が上がっても、DB に繋がって
 * いなければ次の段階で必ず詰まる。ここで表とマイグレーションの適用状況まで
 * 見に行き、目で確かめられるようにする。
 *
 * <p>第9段階の仕上げまで残す。デモの前に叩いて、繋がっているかを確かめる。
 */
@RestController
public class StatusController {

  private final JdbcTemplate jdbc;

  StatusController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("app", "mendan-training");

    // どのマイグレーションまで当たっているか。
    out.put(
        "migration",
        jdbc.queryForObject(
            "SELECT max(version) FROM flyway_schema_history WHERE success", String.class));

    // 表が実際にできているか。名前だけ並べる。
    out.put(
        "tables",
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
            String.class));

    // 面接官の初期設定が入っているか。
    out.put(
        "interviewerProfiles",
        jdbc.queryForList("SELECT code FROM interviewer_profiles ORDER BY id", String.class));

    return out;
  }
}
