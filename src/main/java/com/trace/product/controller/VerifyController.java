package com.trace.product.controller;

import com.trace.product.common.Result;
import com.trace.product.common.ResultCode;
import com.trace.product.dto.VerifyResultVO;
import com.trace.product.service.VerifyService;
import com.trace.product.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 防伪验证接口
 * <p>
 * 该路径受 VerifyRateLimitInterceptor 保护：同一 IP 每分钟最多 30 次。
 */
@RestController
@RequestMapping("/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final VerifyService verifyService;

    @GetMapping("/{code}")
    public Result<VerifyResultVO> verify(@PathVariable String code, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        int scanSource = (userAgent != null && userAgent.toLowerCase().contains("micromessenger")) ? 1 : 2;
        String ip = IpUtils.getClientIp(request);

        VerifyResultVO vo = verifyService.verify(code, ip, userAgent, scanSource);
        if (Boolean.TRUE.equals(vo.getFakeWarning()) || Boolean.TRUE.equals(vo.getVoidWarning())) {
            return Result.fail(ResultCode.FAKE_WARNING, vo);
        }
        return Result.success(vo);
    }
}
