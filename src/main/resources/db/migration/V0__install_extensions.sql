-- gen_random_uuid()는 PostgreSQL 13+ 내장 함수 (pgcrypto extension 불필요)
-- uuid_generate_v7()는 외부 extension 없이 순수 SQL로 구현
CREATE OR REPLACE FUNCTION uuid_generate_v7()
RETURNS UUID
LANGUAGE plpgsql
PARALLEL SAFE
AS $$
DECLARE
  unix_ts_ms BYTEA;
  uuid_bytes BYTEA;
BEGIN
  -- 현재 시각을 밀리초 단위 Unix timestamp (48bit)로 변환
  unix_ts_ms = substring(int8send(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3);
  -- 랜덤 UUID v4를 베이스로 사용
  uuid_bytes = uuid_send(gen_random_uuid());
  -- 상위 6바이트를 타임스탬프로 덮어씀
  uuid_bytes = overlay(uuid_bytes PLACING unix_ts_ms FROM 1 FOR 6);
  -- version 필드를 0111(7)로 설정
  uuid_bytes = set_byte(uuid_bytes, 6, (b'0111' || get_byte(uuid_bytes, 6)::bit(4))::bit(8)::int);
  RETURN encode(uuid_bytes, 'hex')::uuid;
END
$$;
