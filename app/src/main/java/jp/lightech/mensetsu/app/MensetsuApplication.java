package jp.lightech.mensetsu.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 起動口。
 *
 * <p>このクラスがあるのは app モジュールで、domain モジュールからは見えない。
 * 依存の向きは app → domain の一方向だけにしてある。
 */
@SpringBootApplication
// 放置された面接を中断として片付けるため（StaleSessionSweeper）。
// これ以外に定期実行は無い。
@org.springframework.scheduling.annotation.EnableScheduling
public class MensetsuApplication {

  public static void main(String[] args) {
    SpringApplication.run(MensetsuApplication.class, args);
  }
}
