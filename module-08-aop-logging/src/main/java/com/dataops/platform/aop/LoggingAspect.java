package com.dataops.platform.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LoggingAspect {

    private final ObjectMapper mapper = new ObjectMapper();

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controller() {}

    @Pointcut("execution(* com.dataops.platform..service..*(..))")
    public void serviceLayer() {}

    @Around("controller() || serviceLayer()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();


        log.info("→ {} | args: {}", method, argsToSimpleString(pjp.getArgs()));

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            log.info("← {} | duration: {} ms", method, durationMs);
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.error("✘ {} | failed after {} ms | {}", method, durationMs, ex.toString());
            throw ex;
        }
    }

    @AfterThrowing(pointcut = "controller() || serviceLayer()", throwing = "ex")
    public void logAfterThrowing(JoinPoint jp, Throwable ex) {
        log.error("✘ {} threw {}", jp.getSignature().toShortString(), ex.toString(), ex);
    }

    private String argsToSimpleString(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else if (arg.getClass().isPrimitive() || arg instanceof String || arg instanceof Number) {
                sb.append(arg);
            } else {
                sb.append(arg.getClass().getSimpleName()).append("@").append(System.identityHashCode(arg));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}