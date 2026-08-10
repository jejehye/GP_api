package com.example.gpapi.util;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    /** 계좌번호의 뒤쪽 숫자 최대 4개를 마스킹하고 구분자는 유지한다. */
    public static String maskAccount(String account) {
        if (account == null || account.isEmpty()) {
            return "";
        }

        char[] chars = account.toCharArray();
        int maskedDigits = 0;
        for (int i = chars.length - 1; i >= 0 && maskedDigits < 4; i--) {
            if (Character.isDigit(chars[i])) {
                chars[i] = '*';
                maskedDigits++;
            }
        }
        return new String(chars);
    }
}
