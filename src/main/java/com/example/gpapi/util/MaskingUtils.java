package com.example.gpapi.util;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    /** 계좌번호의 오른쪽에서 4·5·6번째 숫자만 마스킹하고 구분자는 유지한다. */
    public static String maskAccount(String account) {
        if (account == null || account.isEmpty()) {
            return "";
        }

        char[] chars = account.toCharArray();
        int digitPositionFromRight = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            if (Character.isDigit(chars[i])) {
                digitPositionFromRight++;
                if (digitPositionFromRight >= 4 && digitPositionFromRight <= 6) {
                    chars[i] = '*';
                }
            }
        }
        return new String(chars);
    }
}
