package com.csr.gaia.android.library;

/**
 * Created by linky on 15-9-16.
 */
public class Utils {

    /**
     * 从 int 转化为十六进制数
     * @param command 待转化的数
     * @return 转化之后的数
     */
    public static String intToHex(int command) {
        String hex = Integer.toHexString(command).toUpperCase();
        String newHex = null;
        switch(hex.length()){
            case 1:
                newHex = "0x000" + hex;
                break;
            case 2:
                newHex = "0x00" + hex;
                break;
            case 3:
                newHex = "0x0" + hex;
                break;
            case 4:
                newHex = "0x" + hex;
                break;
        }

        return newHex;
    }

    /**
     * 将数从 byte 数组转化为十六进形式并输出；
     * @param b byte 数组
     * @return
     */
    public static String printHexString(byte[] b) {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toUpperCase());
        }

        return sb.toString();
    }
}
