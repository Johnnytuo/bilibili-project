package com.xw.bilibili.service.util;

import jdk.internal.util.xml.impl.Input;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * MD5加密
 * 单向加密算法，加密结果不可逆，无法还原加密前明文的数据
 * 特点：加密速度快，不需要秘钥，但是安全性不高，需要搭配随机盐值使用
 *
 */
public class MD5Util {
    public static String sign(String content, String salt, String charset) {
        content = content + salt;
        return DigestUtils.md5Hex(getContentBytes(content, charset));
    }

    public static boolean verify(String content, String sign, String salt, String charset) {
        content = content + salt;
        String mysign = DigestUtils.md5Hex(getContentBytes(content, charset));
        return mysign.equals(sign);
    }

    private static byte[] getContentBytes(String content, String charset) {
        if (!"".equals(charset)) {
            try {
                return content.getBytes(charset);
            } catch (UnsupportedEncodingException var3) {
                throw new RuntimeException("MD5签名过程中出现错误,指定的编码集错误");
            }
        } else {
            return content.getBytes();
        }
    }

    public static String getFileMD5(MultipartFile file) throws IOException {
        InputStream fis = file.getInputStream();
        //把输入流的内容写入输出流，这里用一个字节数组相关的输出流，方便后续获取字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        //设置一个变量存储每次的读取长度
        int byteRead;
        //读取inputStream的内容,如果读取的buffer的大小大于0，说明还有内容可以读，如果等于-1说明读取完毕，用哪种判断都可以
        while((byteRead = fis.read(buffer)) > 0){
            //写入输出流，这里的0是偏移量，每次都是从buffer的开头开始读，所以是0
            baos.write(buffer, 0, byteRead);
        }
        fis.close();
        //把读出的内容进行MD5加密，具体方法就是对字节数组先进行MD5加密，再转换成16进制字符串
        return DigestUtils.md5Hex(baos.toByteArray());
    }
}
