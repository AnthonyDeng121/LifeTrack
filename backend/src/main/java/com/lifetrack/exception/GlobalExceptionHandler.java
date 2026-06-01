package com.lifetrack.exception;

import com.lifetrack.common.Result;
import com.lifetrack.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数校验异常 (Validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = bindingResult.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理 ConstraintViolationException (单参数校验)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<String> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("约束校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理 HTTP 消息不可读异常 (如 JSON 格式错误、请求体缺失)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HTTP 消息不可读: {}", e.getMessage());
        String msg = "请求参数格式错误";
        if (e.getMessage() != null && e.getMessage().contains("Required request body is missing")) {
            msg = "请求体不能为空";
        }
        return Result.error(400, msg);
    }

    /**
     * 处理业务非法参数异常 (如 Enum.valueOf 失败)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数: ", e);
        // 如果是枚举转换失败，给出友好提示
        if (e.getMessage().contains("No enum constant")) {
            return Result.error("非法的分类类型");
        }
        return Result.error(e.getMessage());
    }

    /**
     * 处理路径参数缺失异常 (如 /api/v1/tasks/ 后面没填 ID)
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public Result<String> handleMissingPathVariableException(MissingPathVariableException e) {
        log.warn("路径参数缺失: {}", e.getVariableName());
        return Result.error(400, "必需的路径参数缺失: " + e.getVariableName());
    }

    /**
     * 处理路径参数类型不匹配异常 (如 ID 需要 Long 却传了 String)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<String> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}, expected: {}", e.getName(), e.getRequiredType());
        return Result.error(400, "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(500, "服务器繁忙，请稍后再试");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        return Result.error(500, e.getMessage() != null ? e.getMessage() : "系统运行异常");
    }
}
