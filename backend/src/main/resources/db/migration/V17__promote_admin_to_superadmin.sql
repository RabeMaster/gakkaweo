-- 기존 ADMIN 계정을 SUPERADMIN으로 일괄 승격
-- 권한 분리 도입 시점에 기존 ADMIN을 SUPERADMIN으로 옮긴다.
-- 이후 신규 SUPERADMIN은 운영 SQL로만 부여 가능 (API/시딩에서는 새 SUPERADMIN을 생성하지 않음).
UPDATE members
SET role = 'SUPERADMIN'
WHERE role = 'ADMIN';
