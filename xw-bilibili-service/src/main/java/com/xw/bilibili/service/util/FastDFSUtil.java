package com.xw.bilibili.service.util;

import com.github.tobato.fastdfs.domain.fdfs.FileInfo;
import com.github.tobato.fastdfs.domain.fdfs.MetaData;
import com.github.tobato.fastdfs.domain.fdfs.StorePath;
import com.github.tobato.fastdfs.service.AppendFileStorageClient;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import com.xw.bilibili.domain.exception.ConditionException;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

@Configuration

public class FastDFSUtil {
    //fastDFS提供的用于服务器端与客户端交互的实体类
    //其中有上传一般文件（不推荐，当文件很大时出现中断可能需要重新上传，但是带宽很大时可以使用，这里是引用这个方法）、上传图片并生成缩略图、删除文件等方法
    @Autowired
    private FastFileStorageClient fastFileStorageClient;

    //专门用来断点续传服务的开发，但不能直接使用其中的方法完成全部断点续传的功能
    //如用uploadFile上传分片后的文件，用appendFile在已上传的分片文件后面继续上传后续的分片内容，用modifyFile可以修改文件而不上传新的文件
    @Autowired
    private AppendFileStorageClient appendFileStorageClient;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String DEFAULT_GROUP = "group1";

    private static final String PATH_KEY = "path-key:";

    private static final String UPLOADED_SIZE_KEY = "uploaded-size-key:";

    private static final String UPLOADED_NO_KEY = "uploaded-no-key";

    private static final int SLICE_SIZE = 1024 * 1024;


    //获取文件类型的方法
    // 上传需要传入文件类型作为参数，因此这里进行统一封装和使用
    //参数是multipartfile类型，是Springboot提供的实体类，是一个文件类型，与普通文件区别不大，也可以与普通文件转换
    public String getFileType(MultipartFile file){
        if(file == null){
            throw new ConditionException("Invalid document.");
        }
        //获取文件名
        String fileName = file.getOriginalFilename();
        //文件名包括后缀名所以需要提取type，万一文件名中有多个点，从最后一个点前截取
        int index = fileName.lastIndexOf(".");
        //取得后缀名前的点所在的索引后，从索引处截取type
        return fileName.substring(index+1);
    }

    //上传一般文件（即中小型文件）
    public String uploadCommonFile(MultipartFile file)throws Exception{
        Set<MetaData> metaDataSet = new HashSet<>();
        String fileType = this.getFileType(file);
        //存储文件上传后的路径信息
        StorePath storePath = fastFileStorageClient.uploadFile(file.getInputStream(), file.getSize(), fileType, metaDataSet);
        //我们最终只返回一个String即可，因此用自带方法获取路径
        return storePath.getPath();
    }

    //上传可以断点续传的文件
    //分片由前端完成，然后上传，上传时调用这个upload方法，上传完第一个第一个分片后返回一个路径
    public String uploadAppenderFile(MultipartFile file) throws Exception{
        //获取文件名称
        String fileName = file.getOriginalFilename();
        //获取文件类型
        String fileType = this.getFileType(file);
        //使用appendFileStorageClient中的断点续传功能，几个参数分别是组名（通常写group1即可,因此设一个常量）、输入流、文件大小、文件类型）
        StorePath storePath = appendFileStorageClient.uploadAppenderFile(DEFAULT_GROUP, file.getInputStream(), file.getSize(), fileType);
        return storePath.getPath();
    }

    //续传文件内容的添加，上传完第一个分片后，用返回的路径用这个方法进行后续分片文件的上传
    //参数offSet：偏移量，需要添加文件的点
    public void modifyAppenderFile(MultipartFile file, String filePath, Long offSet) throws Exception{
        appendFileStorageClient.modifyFile(DEFAULT_GROUP, filePath, file.getInputStream(), file.getSize(), offSet);
    }

    //文件分片
    //参数：上传的文件，MD5加密标识，当前要上传的分片是第几片，总分片数（帮助判断什么时候上传完毕）
    public String uploadFileBySlices(MultipartFile file, String fileMd5, Integer sliceNo, Integer totalSliceNo) throws Exception {
        if (file == null || sliceNo == null || totalSliceNo == null) {
            throw new ConditionException("Parameter Exception.");
        }
        //生成三个与redis相关的key
        //第一个分片上传成功后系统返回后的存储路径，暂时放在redis中，等全部上传后清空信息，通过Md5进行区分
        String pathKey = PATH_KEY + fileMd5;
        //当前已上传所有分片加起来的总大小
        String uploadedSizeKey = UPLOADED_SIZE_KEY + fileMd5;
        //上传片段的序号，目前已经上传了多少分片，用于与总分片数比对，相同时已完成所有分片上传，可结束上传流程
        String uploadedNoKey = UPLOADED_NO_KEY + fileMd5;
        //传入一个分片时，先判断目前已传输分片的大小，通过redisTemplate获取
        String uploadedSizeStr = redisTemplate.opsForValue().get(uploadedSizeKey);
        //如果是第一个分片，则uploadedSize一定为0，但如果不是第一个分片，需要获取当前已传输的size
        Long uploadedSize = 0L;
        if (!StringUtil.isNullOrEmpty(uploadedSizeStr)) {
            uploadedSize = Long.valueOf(uploadedSizeStr);
        }
        //获取文件类型
        String fileType = this.getFileType(file);
        //上传
        if (sliceNo == 1) {//上传的是第一个分片，需要跟其他分片进行区分处理，第一个分片用的是upload方法，其他是modify方法
            String path = this.uploadAppenderFile(file);
            //上传后返回path，如果返回的path是null，抛出异常
            if (StringUtil.isNullOrEmpty(path)) {
                throw new ConditionException("Upload failed.");
            }
            //上传成功后要更新redis中的信息，包括存储path，和更新已上传分片
            //把path存储到redis中
            redisTemplate.opsForValue().set(pathKey, path);
            //保存已上传的分片序号
            redisTemplate.opsForValue().set(uploadedNoKey, "1");
        } else {//如果上传的不是第一片
            //从redis获取路径（即上面存储的第一片的路径）
            String filePath = redisTemplate.opsForValue().get(pathKey);
            if (StringUtil.isNullOrEmpty(filePath)) {//如果路径不存在抛出异常
                throw new ConditionException("Upload failed.");
            }
            //第二片开始用modify方法
            //偏移量就是uploadedSize
            this.modifyAppenderFile(file, filePath, uploadedSize);
            //上传后更新redis中的信息
            //increment方法把key对应的value+1
            redisTemplate.opsForValue().increment(uploadedNoKey);
        }
        //更新redis中的信息
        //修改历史上传分片文件大小
        uploadedSize += file.getSize();
        redisTemplate.opsForValue().set(uploadedSizeKey, String.valueOf(uploadedSize));
        //判断上传过程是否结束，如果所有分片全部上传完毕，清空redis中的key和value
        String uploadedNoStr = redisTemplate.opsForValue().get(uploadedNoKey);
        Integer uploadedNo = Integer.valueOf(uploadedNoStr);
        String resultPath = "";
        //uploadedNo是一个对象，用equals比较
        if(uploadedNo.equals(totalSliceNo)){
            resultPath = redisTemplate.opsForValue().get(pathKey);
            //redis中有方法可以将列表中对应的类全部删除，所以这里用一个列表记录所有的key
            List<String> keyList = Arrays.asList(uploadedNoKey, pathKey, uploadedSizeKey);
            redisTemplate.delete(keyList);
        }
        //将路径返回前端，有两种方法，第一种是第一次分片后返回前端一个路径，前端做缓存，后续上传中无需操作，全部文件上传后，前端直接将缓存的这个路径返回给用户
        //第一种是删除redis信息前提取并保存pathKey对应的value，最后返回这个保存的变量，如这里
        return resultPath;
    }

    //文件分片方法（实践中是前端做）
    public void convertFileToSlices(MultipartFile multipartFile) throws IOException {
        String fileName = multipartFile.getOriginalFilename();
        String fileType = this.getFileType(multipartFile);
        //将multipartFile转换成java的file类型，方便后续操作
        File file = this.multipartFileToFile(multipartFile);
        long fileLength = file.length();
        int count = 1;
        //i指的是具体的分片位置
        for(int i = 0; i < fileLength; i += SLICE_SIZE){
            //支持随机访问，即跳到文件任意地方读取数据,第二个参数是指读的权限，"w"为写的权限，"rw"为读写
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            //定位到开始读取的位置
            randomAccessFile.seek(i);
            //从这个分片读取数据，每次读取分片大小（这里是2m）
            byte[] bytes = new byte[SLICE_SIZE];
            //最后一个分片大小大概率小于其他分片大小，这里需要额外获取一下读取的数组长度
            int len = randomAccessFile.read(bytes);
            //路径自己创设
            String path = "/Users/xiwei/Desktop/tempfile/" + count + "." + fileType;
            //新建一个file
            File slice = new File(path);
            //生成文件输出流
            FileOutputStream fos = new FileOutputStream(slice);
            fos.write(bytes, 0, len);
            //一定要关闭相关的流
            fos.close();
            randomAccessFile.close();
            count++;
        }
        //删除之前生成的临时文件
        file.delete();
    }


    //将multipartFile转换为java的File类型
    public File multipartFileToFile(MultipartFile multipartFile) throws IOException {
        String originalFileName = multipartFile.getOriginalFilename();
        //划分filename，取得名字和类型，返回类型是一个list
        String[] fileName = originalFileName.split("\\.");
        //用生成临时文件的方法储存文件内容，参数名是文件名和文件类型
        //生成的时候文件类型前不会自动加点，所以这里要加点
        File file = File.createTempFile(fileName[0], "." + fileName[1]);
        //转换文件类型
        multipartFile.transferTo(file);
        return file;
    }
    //删除文件
    public void deleteFile(String filePath){
        fastFileStorageClient.deleteFile(filePath);
    }

    @Value("${fdfs.http.storage-addr}")
    private String httpFdfsStorageAddr;
    public void viewVideoOnlineBySlices(HttpServletRequest request,
                                        HttpServletResponse response,
                                        String path) throws Exception {
        //使用fastDFS提供的获取文件信息的接口
        FileInfo fileInfo = fastFileStorageClient.queryFileInfo(DEFAULT_GROUP, path);
        long totalFileSize = fileInfo.getFileSize();
        //实际访问的文件路径，后面就是这里的url，前面是固定的ip地址和组名，使用配置文件中添加的参数，使用@Value引用配置文件中的变量
        String url = httpFdfsStorageAddr + path;
        //获取请求头，发到文件服务器,这个方法可以获取所有请求头的名称，返回的是枚举类型
        Enumeration<String> headerNames = request.getHeaderNames();
        //把henderNames的值添加到header中去
        Map<String, Object> headers = new HashMap<>();
        //遍历headerNames,把请求头信息统一放在headers中
        while(headerNames.hasMoreElements()) {//判断当前的枚举类型中是否还有更多数据
            //获取下一个header是什么
            String header = headerNames.nextElement();
            headers.put(header, request.getHeader(header));
        }
        //获取range信息,range的格式是bytes=起始位置-结束位置，需要进行处理
        String rangeStr = request.getHeader("Range");
        //处理range
        String[] range;
        //如果range是空的，需要进行初始赋值
        if(StringUtil.isNullOrEmpty((rangeStr))){
            rangeStr = "bytes=0-" + (totalFileSize - 1);
        }
        //这里相当于分成了bytes、起始位置、结束位置三部分,形成的数组会由一个空元素，一个起始位置，一个结束位置构成
        range = rangeStr.split("bytes=|-");
        long begin = 0;
        //只有开始位置没有结束位置的情况
        if(range.length >= 2) {
            begin = Long.parseLong(range[1]);
        }
        //没有结束位置时赋值
        long end = totalFileSize - 1;
        //有结束位置时
        if(range.length >= 3) {
            end = Long.parseLong(range[2]);
        }
        //取得range的长度
        long len = (end - begin) + 1;
        //进行response头的添加,response headers里面需要一个content-range字段，格式是bytes 起始位置-结束位置/总size，告诉前端返回字段在什么范围
        String contentRange = "bytes " + begin + "-" + end + "/" + totalFileSize;
        //设置Response header
        response.setHeader("Content-Range", contentRange);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Type", "video/mp4");
        //注意contentlength是用专门的方法，参数要求整数类型
        response.setContentLength((int)len);
        //固定的响应状态码是206，不是200，所以这里要设置，用httpresponse提供的常量
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        //完成响应参数设置，需要请求方法
        HttpUtil.get(url, headers, response);
    }
}
