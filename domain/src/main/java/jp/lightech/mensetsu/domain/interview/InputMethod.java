package jp.lightech.mensetsu.domain.interview;

/**
 * 入力方式。
 *
 * <p>記録として残すだけで、進行の判断には使わない（仕様書8章④「メタ情報として
 * 記録するに留める」）。音声が非対応の環境ではテキストに落ちるので、ここで
 * 挙動を分けると、その環境だけ面接の中身が変わってしまう。
 */
public enum InputMethod {
  TEXT,
  VOICE
}
