package com.example.choosecourse.auth;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class CaptchaService {

    public static final String SESSION_KEY = "LOGIN_CAPTCHA";

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final String[] COLORS = {"#1e293b", "#334155", "#1d4ed8", "#0f172a"};

    private final SecureRandom random = new SecureRandom();

    public String generateCode() {
        char[] code = new char[4];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }

    /**
     * 生成一个简单的 SVG 验证码。字符本身是随机字母数字，不需要额外转义。
     */
    public String renderSvg(String code) {
        int width = 120;
        int height = 40;
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(width).append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 120 40\">");
        svg.append("<rect width=\"120\" height=\"40\" fill=\"#f1f5f9\"/>");

        for (int i = 0; i < 5; i++) {
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            svg.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1)
                    .append("\" x2=\"").append(x2).append("\" y2=\"").append(y2)
                    .append("\" stroke=\"#cbd5e1\" stroke-width=\"1\" opacity=\"0.8\"/>");
        }

        for (int i = 0; i < code.length(); i++) {
            int x = 14 + i * 25 + random.nextInt(5) - 2;
            int y = 27 + random.nextInt(6) - 3;
            int rotate = random.nextInt(31) - 15;
            String color = COLORS[random.nextInt(COLORS.length)];
            svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" font-family=\"monospace\" font-size=\"22\" font-weight=\"bold\"")
                    .append(" fill=\"").append(color)
                    .append("\" transform=\"rotate(").append(rotate).append(" ")
                    .append(x).append(" ").append(y).append(")\">")
                    .append(code.charAt(i))
                    .append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }
}
