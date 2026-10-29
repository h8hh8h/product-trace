package com.trace.product.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.SQLException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 当前请求描述（method + uri + query），用于日志定位 */
    private String req() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "-";
            }
            HttpServletRequest r = attrs.getRequest();
            String qs = r.getQueryString();
            return r.getMethod() + " " + r.getRequestURI() + (qs == null ? "" : "?" + qs);
        } catch (Exception e) {
            return "-";
        }
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [{}]: {}", req(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 数据库访问异常（含 MyBatis BadSqlGrammarException 等）：
     * 打印最底层 cause（通常是 SQLSyntaxErrorException，含具体错误如 Unknown column）和完整堆栈
     */
    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccessException(DataAccessException e) {
        Throwable root = e.getMostSpecificCause();
        // e.getMessage() 里通常带有 "### SQL: xxx" 与 "### Cause"，据此可以直接定位出错语句
        log.error("数据库异常 [{}]（含出错 SQL）: {}", req(), e.getMessage());
        log.error("数据库异常 [{}] 最底层原因: {} - {}", req(),
                root.getClass().getSimpleName(), root.getMessage(), e);
        int code = 0;
        if (root instanceof SQLException sql) {
            code = sql.getErrorCode();
            log.error("SQLState={}, ErrorCode={}", sql.getSQLState(), code);
        }
        String hint = "";
        String msg = root.getMessage() == null ? "" : root.getMessage();
        if (code == 1142 || code == 1143 || msg.contains("denied")) {
            hint = "（应用数据库账号缺少该表权限，请执行 GRANT SELECT,INSERT,UPDATE,DELETE ON product_trace.* TO 'product_trace'@'localhost';）";
        } else if (code == 1054 || msg.contains("Unknown column")) {
            hint = "（数据库缺少字段，请按最新 deploy/init-all.sql 核对表结构）";
        } else if (code == 1146 || msg.contains("doesn't exist")) {
            hint = "（数据表不存在，请确认连接的库是否正确，并执行 deploy/init-all.sql）";
        } else if (code == 1045) {
            hint = "（数据库账号或密码错误，请检查 application-prod.yml 的 datasource 配置）";
        } else if (code == 1062 || msg.contains("Duplicate entry")) {
            hint = "（数据重复：唯一键冲突）";
        }
        return Result.fail(ResultCode.FAIL.getCode(), "数据库操作失败：" + msg + hint);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    public Result<Void> handleRateLimitException(RateLimitException e) {
        return Result.fail(ResultCode.RATE_LIMITED.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常 [{}]", req(), e);
        return Result.fail(ResultCode.FAIL.getCode(), "系统繁忙，请稍后再试");
    }
}
