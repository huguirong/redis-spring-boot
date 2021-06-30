package com.ugrong.framework.redis.repository.lock.aop;

import java.util.concurrent.atomic.AtomicBoolean;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.Assert;

import com.ugrong.framework.redis.annotation.RedisLock;
import com.ugrong.framework.redis.domain.IRedisLockType;
import com.ugrong.framework.redis.repository.lock.IRedisLockRepository;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Slf4j
public class RedisLockAspect {

    private final IRedisLockRepository redisLockRepository;

    public RedisLockAspect(IRedisLockRepository redisLockRepository) {
        this.redisLockRepository = redisLockRepository;
    }

    @Pointcut("@annotation(com.ugrong.framework.redis.annotation.RedisLock)")
    public void redisLockPoint() {

    }

    @Around("redisLockPoint()")
    public Object processRedisLock(ProceedingJoinPoint joinPoint) throws Throwable {
        RedisLock redisLock = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(RedisLock.class);
        this.validRedisLock(redisLock);
        IRedisLockType lockType = redisLock::lockType;
        String lockField = redisLock.lockField();
        AtomicBoolean isLock = new AtomicBoolean(Boolean.FALSE);
        try {
            isLock.set(redisLockRepository.tryLock(lockType, lockField, redisLock.waitTime(), redisLock.timeout(), redisLock.timeUnit()));
            if (isLock.get()) {
                //获取到锁
                return joinPoint.proceed();
            }
            throw new IllegalArgumentException("Failed to get redis lock.");
        } catch (Exception e) {
            log.error("Failed to process redis lock.", e);
            throw e;
        } finally {
            //进行解锁
            if (isLock.get()) {
                redisLockRepository.unlock(lockType, lockField);
            }
        }
    }

    private void validRedisLock(RedisLock redisLock) {
        Assert.notNull(redisLock, "This redis lock field is required; it must not be null.");
        Assert.hasText(redisLock.lockType(), "This redis lock type is required; it must not be null.");
        Assert.hasText(redisLock.lockField(), "This redis lock field is required; it must not be null.");
        Assert.isTrue(redisLock.waitTime() >= 0 && redisLock.timeout() > 0, "This [timeout] and [waitTime] is required; it must not be null");
    }
}
