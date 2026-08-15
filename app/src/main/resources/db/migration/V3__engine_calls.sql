-- LLM を呼んだ記録。応答時間の計測（第1段階 Q5 の指示）。
--
-- なぜ表を分けるか
--
--   turns に列で足すと、「1つの回答に対して LLM を2回呼んだ」場合に
--   記録が入らない。深掘りの生成と、回答の分析は別の呼び出しになる。
--   分析だけ遅い、といった切り分けができなくなる。
--
-- 目標値（第1段階で決めたもの）
--   相槌   即時（LLM を呼ばないので、そもそもここに記録が出ない）
--   深掘り 3秒以内に「表示が始まる」こと
--
-- 【重要】測るのは first_token_ms であって total_ms ではない。
-- ストリーミング表示なので、利用者が待つのは最初の文字が出るまで。
-- 全部出来上がるまでの時間は、体感には効かない。
-- total_ms しか測らないと、体感が良いのに「遅い」と判断してしまう。
CREATE TABLE engine_calls (
  id                bigserial PRIMARY KEY,
  session_id        bigint NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  -- どの往復のための呼び出しか。まだ turn が無い時点の呼び出しもある。
  turn_id           bigint REFERENCES turns(id) ON DELETE SET NULL,

  -- 何のために呼んだか。
  --   NEXT_QUESTION   次の質問を作る
  --   ANALYZE_ANSWER  回答を分析する
  purpose           text NOT NULL
                      CHECK (purpose IN ('NEXT_QUESTION','ANALYZE_ANSWER')),

  engine_kind       text NOT NULL CHECK (engine_kind IN ('STUB','CLAUDE')),
  model             text NOT NULL DEFAULT '',

  started_at        timestamptz NOT NULL DEFAULT now(),
  -- 最初の文字が届くまで。ストリーミングでないなら total と同じ値になる。
  first_token_ms    integer,
  -- 生成が終わるまで。
  total_ms          integer,

  ok                boolean NOT NULL DEFAULT true,
  -- 失敗したときの理由。人が読む文言。
  --
  -- 【重要】失敗を記録して、面接自体は続ける。
  -- 面接の途中で例外を投げて落ちるのは、体験として最悪。
  -- 深掘りが作れなければ、相槌でつないで次へ進める。
  error_note        text NOT NULL DEFAULT ''
);

CREATE INDEX engine_calls_session_idx ON engine_calls (session_id, started_at);

-- 目標値を超えた呼び出しだけを拾う。
-- 3秒（3000ms）は第1段階で決めた深掘りの目標値。
CREATE INDEX engine_calls_slow_idx ON engine_calls (started_at)
  WHERE first_token_ms > 3000;
