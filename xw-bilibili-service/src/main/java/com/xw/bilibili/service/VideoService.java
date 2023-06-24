package com.xw.bilibili.service;


import com.xw.bilibili.dao.VideoDao;
import com.xw.bilibili.domain.*;
import com.xw.bilibili.domain.exception.ConditionException;
import com.xw.bilibili.service.util.FastDFSUtil;
import com.xw.bilibili.service.util.IpUtil;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.UncenteredCosineSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.model.DataModel;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoService {
    @Autowired
    private VideoDao videoDao;

    @Autowired
    private FastDFSUtil fastDFSUtil;

    @Autowired
    private UserCoinService userCoinService;

    @Autowired
    private UserService userService;

    //因为这里要向两个数据库添加数据，所以要加transactional，及时进行数据回滚，防止一个添加成功一个添加失败
    @Transactional
    public void addVideos(Video video) {
        Date now = new Date();
        video.setCreateTime(new Date());
        videoDao.addVideos(video);
        Long videoId = video.getId();
        List<VideoTag> tagList = video.getVideoTagList();
        tagList.forEach(item -> {
            item.setCreateTime(now);
            item.setVideoId(videoId);
        });
        videoDao.batchAddVideTags(tagList);

    }

    public PageResult<Video> pageListVideos(Integer size, Integer no, String area) {
        if (size == null || no == null) {
            throw new ConditionException("Parameter Exception.");
        }
        //新设map用于存放传入的参数，一个start一个limit
        Map<String, Object> params = new HashMap<>();
        //start是从哪条数据开始查询，一页size条数据，每一页就是从前面页面已有数据开始
        params.put("start", (no - 1) * size);
        //每一页的数量是limit
        params.put("limit", size);
        //分区作为可能的筛选条件，所以也传进来
        params.put("area", area);
        //pageresult需要传入一个list，所以新建一个list存储查询结果
        List<Video> list = new ArrayList<>();
        Integer total = videoDao.pageCountVideos(params);
        //如果查询结果大于0，需要给list重新赋值
        if (total > 0) {
            list = videoDao.pageListVideos(params);
        }
        return new PageResult<>(total, list);
    }

    public void viewVideoOnlineBySlices(HttpServletRequest request,
                                        HttpServletResponse response,
                                        String url) throws Exception {
        fastDFSUtil.viewVideoOnlineBySlices(request, response, url);

    }

    public void addVideoLike(Long videoId, Long userId) {
        Video video = videoDao.getVideoById(videoId);
        if(video == null) {
            throw new ConditionException("Invalid video.");
        }
        //判断当前视频是否已被登陆用户点赞过，点赞过的话就没必要进行后续操作
        VideoLike videoLike = videoDao.getVideoLikeByVideoIdAndUserId(videoId, userId);
        if(videoLike != null) {
            throw new ConditionException("Already liked.");
        }
        videoLike = new VideoLike();
        videoLike.setVideoId(videoId);
        videoLike.setUserId(userId);
        videoLike.setCreateTime(new Date());
        videoDao.addVideoLike(videoLike);
    }

    public void deleteVideoLike(Long videoId, Long userId) {
        videoDao.deleteVideoLike(videoId, userId);
    }

    public Map<String, Object> getVideoLikes(Long videoId, Long userId) {
        Long count = videoDao.getVideoLikes(videoId);
        //userId如果传了null进来，数据库查询不到数据，就不会获取到内容，就不做已点赞的展示，不会影响count的结果
        VideoLike videoLike = videoDao.getVideoLikeByVideoIdAndUserId(videoId, userId);
        //判断当前用户是否已经对该视频点赞，已点赞的话前端页面也做一个已点赞的展示
        boolean like = videoLike != null;
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("like", like);
        return result;
    }

    @Transactional
    public void addVideoCollection(VideoCollection videoCollection, Long userId) {
        //如api中所描述的，这里需要获取出参数自行进行判断
        Long videoId = videoCollection.getVideoId();
        Long groupId = videoCollection.getGroupId();
        if(videoId == null || groupId == null) {
            throw new ConditionException("Parameter exception.");
        }
        Video video = videoDao.getVideoById(videoId);
        if(video == null) {
            throw new ConditionException("Invalid video.");
        }
        //先删除后添加，相当于更新的操作，可以既囊括添加，也囊括更新功能
        videoDao.deleteVideoCollection(videoId, userId);
        videoCollection.setUserId(userId);
        videoCollection.setCreateTime(new Date());
        videoDao.addVideoCollection(videoCollection);
    }

    public void deleteVideoCollection(Long videoId, Long userId) {
        videoDao.deleteVideoCollection(videoId, userId);
    }

    public Map<String, Object> getVideoCollections(Long videoId, Long userId) {
        Long count = videoDao.getVideoCollections(videoId);
        VideoCollection videoCollection = videoDao.getVideoCollectionByVideoIdAndUserId(videoId, userId);
        boolean collected = videoCollection != null;
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("collected", collected);
        return result;
    }

    //Transactional说明这个方法对多个表进行了操作
    @Transactional
    public void addVideoCoins(VideoCoin videoCoin, Long userId) {
        Long videoId = videoCoin.getVideoId();
        Integer amount = videoCoin.getAmount();
        if(videoId == null) {
            throw new ConditionException("Parameter exception.");
        }
        //看投币视频是否存在
        Video video = videoDao.getVideoById(videoId);
        if(video == null) {
            throw new ConditionException("Invalid video.");
        }
        //看当前用户是否有足够的币
        Integer userCoinsAmount = userCoinService.getUserCoinsAmount(userId);
        //如果未查询到值，说明没有币，赋值0
        userCoinsAmount = userCoinsAmount == null ? 0 : userCoinsAmount;
        if(amount > userCoinsAmount) {
            throw new ConditionException("No enough coins.");
        }
        //查询当前用户已经对该视频投了多少硬币,因为数据库表每个用户是一条数据，而不是每次投币是一次数据，所以需要每次投币需要更新用户对应的投币总数
        //而不是新增一条数据
        VideoCoin dbvideoCoin = videoDao.getVideoCoinByVideoIdAndUserId(videoId, userId);
        //如果本来没有投币，是新增视频投币操作
        if(dbvideoCoin == null){
            videoCoin.setUserId(userId);
            videoCoin.setCreateTime(new Date());
            videoDao.addVideoCoin(videoCoin);
        }else{
            //获取用户已经投币的总数量
            Integer dbAmount = dbvideoCoin.getAmount();
            dbAmount += amount;
            //已投过币就走更新操作
            videoCoin.setUserId(userId);
            videoCoin.setAmount(dbAmount);
            videoCoin.setUpdateTime(new Date());
            videoDao.updateVideoCoin(videoCoin);
        }
        //因为有投币行为，需要更新用户的硬币总数
        userCoinService.updateUserCoinAmount(userId, (userCoinsAmount - amount));
    }

    public Map<String, Object> getVideoCoins(Long videoId, Long userId) {
        //得到硬币数量，因为不同用户投币数量不同，需要求和，所以要注意这里的sql语句与其他不同
        Long count = videoDao.getVideoCoinsAmount(videoId);
        VideoCoin videoCoin = videoDao.getVideoCoinByVideoIdAndUserId(videoId, userId);
        boolean paid = videoCoin != null;
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("paid", paid);
        return result;
    }

    public void addVideoComment(VideoComment videoComment, Long userId) {
        Long videoId = videoComment.getVideoId();
        if(videoId == null){
            throw new ConditionException("Parameter exception.");
        }
        Video video = videoDao.getVideoById(videoId);
        if(video == null){
            throw new ConditionException("Invalid video.");
        }
        videoComment.setUserId(userId);
        videoComment.setCreateTime(new Date());
        videoDao.addVideoComment(videoComment);
    }

    //没看懂
    public PageResult<VideoComment> pageListVideoComments(Integer size, Integer no, Long videoId) {
        Video video = videoDao.getVideoById(videoId);
        if (video == null) {
            throw new ConditionException("Parameter exception.");
        }
        //分页参数设置
        Map<String, Object> params = new HashMap<>();
        params.put("start", (no - 1) * size);
        params.put("limit", size);
        params.put("videoId", videoId);
        //取得评论总数量
        Integer total = videoDao.pageCountVideoComments(params);
        List<VideoComment> list = new ArrayList<>();
        //如果评论总数大于0，就查询列表的值
        if(total > 0) {
            //这里先查询一级评论，所以xml里面搜索条件要写rootId is null
            list = videoDao.pageListVideoComments(params);
            //批量查询二级查询
            // 先拿出list中的所有一级评论的id字段
            List<Long> parentIdList = list.stream().map(VideoComment::getId).collect(Collectors.toList());
            List<VideoComment> childCommentList = videoDao.batchGetVideoCommentByRootIds(parentIdList);
            //批量查询用户信息
            Set<Long> userIdList = list.stream().map(VideoComment::getUserId).collect(Collectors.toSet());
            Set<Long> replyUserIdList = childCommentList.stream().map(VideoComment::getReplyUserId).collect(Collectors.toSet());
            //形成所有评论相关的userId的合集
            userIdList.addAll(replyUserIdList);
            List<UserInfo> userInfoList = userService.batchGetUserInfoByUserIds(userIdList);
            //封装成map类型，后续可以直接通过userId获取userInfo，无需再遍历userInfoList
            Map<Long, UserInfo> userInfoMap = userInfoList.stream().collect(Collectors.toMap(UserInfo::getUserId, userInfo -> userInfo));
            list.forEach(comment->{
                Long id = comment.getId();
                List<VideoComment> childList = new ArrayList<>();
                childCommentList.forEach(child->{
                    //如果一级评论的id等于child的rootId就进行赋值
                    if(id.equals(child.getRootId())){
                        child.setUserInfo(userInfoMap.get(child.getUserId()));
                        child.setReplyUserInfo(userInfoMap.get(child.getReplyUserId()));
                        childList.add(child);
                    }
                });
                comment.setChildList(childList);
                comment.setUserInfo(userInfoMap.get(comment.getUserId()));
            });
        }
        return new PageResult<>(total, list);
    }

    public Map<String, Object> getVideoDetails(Long videoId) {
        //查询取得video
        Video video = videoDao.getVideoDetails(videoId);
        //获取用户id用于查询用户信息
        Long userId = video.getUserId();
        //查询用户信息因为加载视频详情页的时候右上角会加载视频投稿人的信息
        User user = userService.getUserInfo(userId);
        UserInfo userInfo = user.getUserInfo();
        Map<String, Object> result = new HashMap<>();
        result.put("video", video);
        result.put("userInfo", userInfo);
        return result;



    }

    public void addVideoView(VideoView videoView, HttpServletRequest request) {
        Long userId = videoView.getUserId();
        Long videoId = videoView.getVideoId();
        //生成clientId
        String agent = request.getHeader("User-Agent");
        //把agent这个请求头中的字段解析生成UserAgent实体类，需要pom中引入eu依赖
        UserAgent userAgent = UserAgent.parseUserAgentString(agent);
        String clientId = String.valueOf(userAgent.getId());
        String ip = IpUtil.getIP(request);
        Map<String, Object> params = new HashMap<>();
        //区分游客和登陆模式,如果是登陆模式，添加userId，如果是游客模式，添加ip和clientId，用操作系统+浏览器+IP区分游客
        if(userId != null) {
            params.put("userId", userId);
        }else{
            params.put("ip", ip);
            params.put("clientId", clientId);
        }
        //看当天有没有这个视频的观看记录
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");
        params.put("today", sdf.format(now));
        params.put("videoId", videoId);
        //添加观看记录,一个用户一天只能查出一条记录
        VideoView dbVideoView = videoDao.getVideoView(params);
        //如果查询到了，说明当天已经添加了，所以是null时才添加，非null时直接返回
        if(dbVideoView == null){
            videoView.setIp(ip);
            videoView.setClientId(clientId);
            videoView.setCreateTime(new Date());
            videoDao.addVideoView(videoView);
        }

    }

    public Integer getVideoViewCounts(Long videoId) {
        return videoDao.getVideoViewCounts(videoId);
    }

    public List<Video> recommend(Long userId) throws TasteException {
        List<UserPreference> list = videoDao.getAllUserPreference();
        //Manhout提供的数据分析模型，输入偏好数据，可以存储在模型中，进行数据准备
        DataModel dataModel = this.createDataModel(list);
        //获取用户相似程度，mahout中有多种计算相似度，这里选择UncenteredCosineSimilarity，对于偏好评分的计算比较准确
        UserSimilarity similarity = new UncenteredCosineSimilarity(dataModel);
        System.out.println(similarity.userSimilarity(11, 12));
        //获取用户邻居,参数包括想要的邻居数量、相似度和数据模型，这样就可以获取跟用户最相近的两个用户邻居
        UserNeighborhood userNeighborhood = new NearestNUserNeighborhood(2, similarity, dataModel);
        long[] ar = userNeighborhood.getUserNeighborhood(userId);
        //构建推荐器,这里使用的是基于用户的推荐器
        Recommender recommender = new GenericUserBasedRecommender(dataModel, userNeighborhood, similarity);
        //推荐商品，参数是userId和推荐的商品数量
        List<RecommendedItem> recommendedItems = recommender.recommend(userId, 5);
        //因为我们用下面的方法传入datamodel的时候传入的是videoid，所以推荐出来videoid
        List<Long> itemIds = recommendedItems.stream().map(RecommendedItem::getItemID).collect(Collectors.toList());
        return videoDao.batchGetVideosByIds(itemIds);
    }

    private DataModel createDataModel(List<UserPreference> userPreferenceList) {
        FastByIDMap<PreferenceArray> fastByIdMap = new FastByIDMap<>();
        Map<Long, List<UserPreference>> map = userPreferenceList.stream().collect(Collectors.groupingBy(UserPreference::getUserId));
        Collection<List<UserPreference>> list = map.values();
        for(List<UserPreference> userPreferences : list){
            GenericPreference[] array = new GenericPreference[userPreferences.size()];
            for(int i = 0; i < userPreferences.size(); i++){
                UserPreference userPreference = userPreferences.get(i);
                GenericPreference item = new GenericPreference(userPreference.getUserId(), userPreference.getVideoId(), userPreference.getValue());
                array[i] = item;
            }
            fastByIdMap.put(array[0].getUserID(), new GenericUserPreferenceArray(Arrays.asList(array)));
        }
        return new GenericDataModel(fastByIdMap);
    }

}
