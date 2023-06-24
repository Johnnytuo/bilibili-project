package com.xw.bilibili.service.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.xw.bilibili.domain.exception.ConditionException;

import java.util.Calendar;
import java.util.Date;

public class TokenUtil {

    private static final String ISSUER = "issuer"; //签发者可以写所属机构名、个人名等


    //创建用户令牌的方法
    public static String generateToken(Long userId) throws Exception{//用户令牌主要用于标识用户身份，所以参数用userId
        Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey()); //这里我们用RSA加密方式，因为我们已经有一个RSAUtil设置了公私秘钥，可以直接使用
        Calendar calendar = Calendar.getInstance(); //用于生成过期时间
        calendar.setTime(new Date()); //获取当前系统时间
        calendar.add(Calendar.HOUR, 1); //系统时间加超时时间构成过期时间， 第一个参数设置时间单位，这里我们设置的过期时间是30秒,即30秒后token就不能使用了
        return JWT.create().withKeyId(String.valueOf(userId)) //添加用户id
                .withIssuer(ISSUER) //添加签发者
                .withExpiresAt(calendar.getTime()) //添加过期时间
                .sign(algorithm); //生成签名的方法，传入算法，指使用RSA加密把上面添加的这些信息做一个签名算法加密生成
    }

    //创建刷新token，跟一般token唯一的区别是刷新的时长
    public static String generateRefreshToken(Long userId) throws Exception{
        Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey()); //这里我们用RSA加密方式，因为我们已经有一个RSAUtil设置了公私秘钥，可以直接使用
        Calendar calendar = Calendar.getInstance(); //用于生成过期时间
        calendar.setTime(new Date()); //获取当前系统时间
        calendar.add(Calendar.DAY_OF_MONTH, 7); //系统时间加超时时间构成过期时间， 第一个参数设置时间单位，这里我们设置的过期时间是30秒,即30秒后token就不能使用了
        return JWT.create().withKeyId(String.valueOf(userId)) //添加用户id
                .withIssuer(ISSUER) //添加签发者
                .withExpiresAt(calendar.getTime()) //添加过期时间
                .sign(algorithm); //生成签名的方法，传入算法，指使用RSA加密把上面添加的这些信息做一个签名算法加密生成

    }
    //验证用户令牌的方法
    public static Long verifyToken(String token){
        //使用verifyToken可能因为token过期而失效，因此不能像上面一样直接抛出异常给前端，因为是一个通用异常，会直接展示错误码，不能进行进一步操作
        try {
            Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey());
            JWTVerifier verifier = JWT.require(algorithm).build(); //用上面的算法生成一个JWT验证类
            DecodedJWT jwt = verifier.verify(token); //引用verifier的方法直接验证，这里生成的jwt与上面新建的未加密前的jwt一样
            String userId = jwt.getKeyId(); //获取userId，是通过KeyId传入的
            return Long.valueOf(userId);
        }catch(TokenExpiredException e){
            throw new ConditionException("555", "Token expired.");
        }catch(Exception e){//除了token过期，其他情况先全部返回非法用户token
            throw new ConditionException("Invalid user token.");
        }
    }


    public static void verifyRefreshToken(String refreshToken) {
        TokenUtil.verifyToken(refreshToken);
    }
}
