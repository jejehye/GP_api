package com.example.gpapi.dto;

import com.example.gpapi.util.MaskingUtils;

import java.time.LocalDateTime;

/**
 * 좌측 "최근 API 요청" 테이블 한 행을 표현하는 DTO.
 * 비밀번호 원문은 보관하지 않고 길이만 저장 — UI에서는 마스킹된 형태로만 노출.
 */
public record RequestLog(
        LocalDateTime timestamp,
        String account,
        int pwLength,
        boolean success,
        String message
) {
    public String maskedAccount() {
        return MaskingUtils.maskAccount(account);
    }

    public String maskedPw() {
        if (pwLength <= 0) return "(빈값)";
        return "*".repeat(pwLength) + " (" + pwLength + "자리)";
    }
}
