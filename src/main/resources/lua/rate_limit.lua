-- 限流 Lua 脚本（固定窗口 + TTL）
-- KEYS[1] = 限流 key
-- ARGV[1] = 最大次数
-- ARGV[2] = 过期时间（秒）
-- 返回：1=允许 0=拒绝
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local expire = tonumber(ARGV[2])
local current = tonumber(redis.call('get', key) or '0')
if current >= limit then
    return 0
end
current = redis.call('incr', key)
if current == 1 then
    redis.call('expire', key, expire)
end
return 1
