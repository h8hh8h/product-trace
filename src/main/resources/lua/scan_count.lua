-- ============================================================
-- 一物一码扫码次数原子累加脚本
-- 作用：Redis 不存在计数时先用数据库历史值初始化，再原子自增，
--       避免并发下计数丢失或从 0 重新开始
-- KEYS[1]：trace:code:scan:{code}
-- ARGV[1]：scan_limit（0 表示不限次数）
-- ARGV[2]：ttl 秒数（计数 key 的过期时间，默认 7 天）
-- ARGV[3]：initial_count（数据库中的历史扫码次数）
-- 返回：{ current_count, is_fake }
--        is_fake = 1 表示超过扫码上限，触发假货预警
-- ============================================================
local key     = KEYS[1]
local limit   = tonumber(ARGV[1])
local ttl     = tonumber(ARGV[2])
local initial = tonumber(ARGV[3])

if redis.call('EXISTS', key) == 0 then
    redis.call('SET', key, initial)
    if ttl > 0 then
        redis.call('EXPIRE', key, ttl)
    end
end

local current = redis.call('INCR', key)
local isFake = 0

if limit > 0 and current > limit then
    isFake = 1
end

return { current, isFake }
