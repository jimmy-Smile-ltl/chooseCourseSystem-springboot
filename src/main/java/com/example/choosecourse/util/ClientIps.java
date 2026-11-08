package com.example.choosecourse.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIps {

    private ClientIps() {
    }

    /**
     * 读取客户端 IP。生产环境如果前面有可信反向代理，只应信任代理写入的
     * X-Forwarded-For，并确保代理会覆盖而不是透传客户端自带的值。
     */
    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
