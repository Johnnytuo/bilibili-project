package com.xw.bilibili.service;


import com.xw.bilibili.dao.FileDao;
import com.xw.bilibili.domain.File;
import com.xw.bilibili.service.util.FastDFSUtil;
import com.xw.bilibili.service.util.MD5Util;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;


@Service
public class FileService {

    @Autowired
    private FastDFSUtil fastDFSUtil;

    @Autowired
    private FileDao fileDao;


    public String uploadFileBySlices(MultipartFile slice,
                                     String fileMD5,
                                     Integer sliceNo,
                                     Integer totalSliceNo) throws Exception {
        //通过md5在数据库中获取文件
        File dbFileMD5 = fileDao.getFileByMD5(fileMD5);
        //如果确实获取到了文件，不是null，说明之前已经传输过这个文件，直接返回对应的文件路径，无需重新上传
        if(dbFileMD5 != null) {
            return dbFileMD5.getUrl();
        }
        //如果没有获取到文件，说明没有传输过，则用util功能中的上传再次传输
        String url = fastDFSUtil.uploadFileBySlices(slice, fileMD5, sliceNo, totalSliceNo);
        //如果上传成功，则能够获取到非空的url，判断并创建数据库中的信息
        if(!StringUtil.isNullOrEmpty(url)) {
            dbFileMD5 = new File();
            dbFileMD5.setCreateTime(new Date());
            dbFileMD5.setUrl(url);
            dbFileMD5.setMd5(fileMD5);
            dbFileMD5.setType(fastDFSUtil.getFileType(slice));
            fileDao.addFile(dbFileMD5);
        }
        return url;
    }

    public String getFileMD5(MultipartFile file) throws IOException {
        return MD5Util.getFileMD5(file);
    }
}
