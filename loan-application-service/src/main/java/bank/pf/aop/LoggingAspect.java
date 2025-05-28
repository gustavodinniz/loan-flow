package bank.pf.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("within(bank.pf.service..*) || within(bank.pf.controller..*)")
    public void applicationPackagePointcut() {
    }

    @Around("applicationPackagePointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();

        log.info("ENTERING: {}.{}() with arguments = {}", className, methodName, Arrays.toString(joinPoint.getArgs()));
        log.info("Executing on thread: {}", Thread.currentThread());

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            log.error("EXCEPTION in {}.{}() : {}", className, methodName, throwable.getMessage(), throwable);
            throw throwable;
        }

        long timeTaken = System.currentTimeMillis() - startTime;

        if (result instanceof CompletableFuture) {
            CompletableFuture<?> cf = (CompletableFuture<?>) result;
            return cf.whenComplete((val, ex) -> {
                if (ex != null) {
                    log.error("ASYNC EXCEPTION in {}.{}() after {}ms : {}", className, methodName, timeTaken, ex.getMessage(), ex);
                } else {
                    log.info("ASYNC EXITING: {}.{}() with result = {} after {}ms", className, methodName, val, timeTaken);
                }
            });
        } else {
            log.info("EXITING: {}.{}() with result = {} after {}ms", className, methodName, result, timeTaken);
        }
        return result;
    }
}
