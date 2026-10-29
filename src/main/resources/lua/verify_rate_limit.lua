-- ============================================================
-- 防伪验证接口 IP 限流脚本（固定窗口）
-- 作用：同一 IP 在 window 秒内最多访问 limit 次
-- KEYS[1]：限流 key，例如 rate:verify:ip:1.2.3.4
-- ARGV[1]：limit   最大请求次数（默认 30）
-- ARGV[2]：window  时间窗口秒数（默认 60）
-- 返回：{ allowed, ttl, current }
--        allowed = 1 允许放行；0 已超限，返回 429
--        ttl     = 当前窗口剩余秒数，用于 Retry-After
--        current = 当前窗口已请求次数
-- ============================================================
local key    = KEYS[1]
local limit  = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local current = redis.call('INCR', key)

-- 第一次访问时设置窗口过期时间
if current == 1 then
    redis.call('EXPIRE', key, window)
end

local ttl = redis.call('TTL', key)
if ttl < 0 then
    redis.call('EXPIRE', key, window)
    ttl = window
end

if current > limit then
    return { 0, ttl, current }
end

return { 1, ttl, current }
