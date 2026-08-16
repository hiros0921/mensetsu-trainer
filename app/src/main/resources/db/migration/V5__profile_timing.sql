-- 制限時間を面接官の設定として持つ（第8段階・諏訪さんの判断）。
--
--   「この3つの値を、設定として変更できる形にしておいてください。
--     interviewer_profiles に持たせれば済むはずです。既定はT1。
--     慣れてきたらT2に上げる、という遊び方ができます。ハードコードしないこと」
--
-- 圧の初期値や掘る段数と同じ扱いにする。面接官の性格の一部として持てば、
-- 「厳しい英語面接官」を後から足すのに、コードを触らずに済む。

ALTER TABLE interviewer_profiles
  -- 1問あたりの回答制限時間。NULL なら時間を測らない。
  --
  -- 【重要】0 ではなく NULL にすること。
  -- 0 だと「制限時間ゼロ＝即打ち切り」と読めてしまう。
  -- 「測らない」と「0秒」はまったく違う。
  ADD COLUMN answer_limit_ms  integer,
  -- 入力（発話）が止まってから、回答終了とみなすまで。
  ADD COLUMN silence_cutoff_ms integer,
  -- 質問を出してから最初の入力までの猶予。ここは沈黙に数えない。
  ADD COLUMN grace_ms          integer;

-- 3つは揃っているか、揃って無いかのどちらか。
-- 片方だけ入っていると、どう動くべきか決まらない。
ALTER TABLE interviewer_profiles
  ADD CONSTRAINT profile_timing_all_or_none CHECK (
    (answer_limit_ms IS NULL AND silence_cutoff_ms IS NULL AND grace_ms IS NULL)
    OR (answer_limit_ms IS NOT NULL AND silence_cutoff_ms IS NOT NULL AND grace_ms IS NOT NULL)
  );

-- 順序が壊れた設定を保存できないようにする。
--
-- 沈黙の打ち切りが制限時間以上だと、制限のほうが先に来て、
-- 沈黙では一度も打ち切られない。沈黙の検出が死んでいることに気づけない。
--
-- 猶予が沈黙の打ち切り以上だと、猶予中の沈黙が一切数えられなくなる。
ALTER TABLE interviewer_profiles
  ADD CONSTRAINT profile_timing_ordered CHECK (
    answer_limit_ms IS NULL
    OR (grace_ms >= 0 AND grace_ms < silence_cutoff_ms AND silence_cutoff_ms < answer_limit_ms)
  );

-- 英語面接官に案T1を入れる。諏訪さんが選ばれた既定値。
--
--   「このモードの価値は、実際のAI面接の体験を再現することにある。
--     だったら、実物に近い設定を選ぶのが筋」
--
-- 案T2（60/5/2秒）に上げたいときは、この行を UPDATE するだけ。コードは触らない。
UPDATE interviewer_profiles
   SET answer_limit_ms = 90000, silence_cutoff_ms = 8000, grace_ms = 3000
 WHERE code = 'english_standard';

-- 日本語のモードは時間を測らない。
-- エンジニア面接で90秒の制限をかけると、技術選定の説明が途中で切れる。
-- （既定が NULL なので明示の UPDATE は不要。意図として書き残す）
