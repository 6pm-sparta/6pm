-- seed-scale-seats.sql — 부하테스트 전용 좌석 대량 시드 (200석)
-- data.sql의 고정 5석(A1~B2)은 팀 공용 API 테스트용이라 건드리지 않고, 이 스크립트로 별도 200석을 추가한다.
-- showId는 data.sql과 동일한 고정값을 그대로 씀 (11111111-1111-1111-1111-111111111111)
-- 실행: docker exec -i 6pm-postgres-ticketing psql -U root -d ticketing_db -f /dev/stdin < k6/seed-scale-seats.sql
--   (또는 docker cp 후 컨테이너 안에서 psql -f 로 실행해도 됨)
INSERT INTO show_seats (id, show_id, seat_name, grade, price, order_id, created_at, updated_at)
SELECT
    gen_random_uuid(),
    '11111111-1111-1111-1111-111111111111',
    'SCALE-' || i,
    'S',
    80000,
    NULL,
    now(),
    now()
FROM generate_series(1, 200) AS i
ON CONFLICT (id) DO NOTHING;
