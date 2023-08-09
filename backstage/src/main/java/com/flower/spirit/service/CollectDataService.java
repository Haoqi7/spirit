package com.flower.spirit.service;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.flower.spirit.common.AjaxEntity;
import com.flower.spirit.config.Global;
import com.flower.spirit.dao.CollectdDataDao;
import com.flower.spirit.dao.VideoDataDao;
import com.flower.spirit.entity.CollectDataDetailEntity;
import com.flower.spirit.entity.CollectDataEntity;
import com.flower.spirit.entity.VideoDataEntity;
import com.flower.spirit.utils.Aria2Util;
import com.flower.spirit.utils.BiliUtil;
import com.flower.spirit.utils.DataUtil;
import com.flower.spirit.utils.DateUtils;
import com.flower.spirit.utils.DouUtil;
import com.flower.spirit.utils.HttpUtil;
import com.flower.spirit.utils.StringUtil;

@Service
public class CollectDataService {
	
	
	private ExecutorService exec = Executors.newFixedThreadPool(1);
	
	@Autowired
	private CollectdDataDao collectdDataDao;
	
	@Autowired
	private CollectDataDetailService collectDataDetailService;
	
	@Autowired
	private VideoDataService videoDataService;
	
	@Autowired
	private VideoDataDao videoDataDao;
	
	private Logger logger = LoggerFactory.getLogger(CollectDataService.class);
	
	
    /**
     * 文件储存真实路径
     */
    @Value("${file.save.path}")
    private String uploadRealPath;
    
    /**
     * 映射路径
     */
    @Value("${file.save}")
    private String savefile;
    
    
    public void findMonitor(){
    	List<CollectDataEntity> list = collectdDataDao.findByMonitoring("Y");
    	if(list.size() == 0) {
    		logger.info("未设置监控收藏夹");
    		return;
    	}
    	for(CollectDataEntity data:list) {
    		//开始执行
    		this.submitCollectData(data);
    	}
		
    }
	
	
	@SuppressWarnings("serial")
	public AjaxEntity findPage(CollectDataEntity res) {
		PageRequest of= PageRequest.of(res.getPageNo(), res.getPageSize());
		Specification<CollectDataEntity> specification = new Specification<CollectDataEntity>() {

			@Override
			public Predicate toPredicate(Root<CollectDataEntity> root, CriteriaQuery<?> query,
					CriteriaBuilder criteriaBuilder) {
				Predicate predicate=criteriaBuilder.conjunction();
				CollectDataEntity seachDate = (CollectDataEntity) res;
				if(seachDate != null && StringUtil.isString(seachDate.getTaskid())) {
					predicate.getExpressions().add(criteriaBuilder.like(root.get("taskid"), "%"+seachDate.getTaskid()+"%"));
				}
				if(seachDate != null && StringUtil.isString(seachDate.getPlatform())) {
					predicate.getExpressions().add(criteriaBuilder.like(root.get("platform"), "%"+seachDate.getPlatform()+"%"));
				}

				query.orderBy(criteriaBuilder.desc(root.get("id")));
				return predicate;
			}};
			
			Page<CollectDataEntity> findAll = collectdDataDao.findAll(specification,of);
			return new AjaxEntity(Global.ajax_success, "数据获取成功", findAll);
	}

	public AjaxEntity deleteCollectData(CollectDataEntity collectDataEntity) {
		collectdDataDao.deleteById(collectDataEntity.getId());
		return new AjaxEntity(Global.ajax_success, "操作成功", null);
	}

	/**
	 * 提交任务 
	 * @param collectDataEntity
	 * @return
	 */
	public AjaxEntity submitCollectData(CollectDataEntity collectDataEntity) {
		if(null != collectDataEntity.getPlatform() && collectDataEntity.getPlatform().equals("哔哩") ) {
			//必须授权ck
			if(Global.bilicookies.equals("")) {
				logger.info("必须填写bili ck,本次执行失败");
				return new AjaxEntity(Global.ajax_uri_error, "必须填写bili ck", null);
			}
			String api = "https://api.bilibili.com/x/v3/fav/resource/ids?media_id="+collectDataEntity.getOriginaladdress()+"&platform=web";
			String httpGetBili = HttpUtil.httpGetBili(api, "UTF-8", Global.bilicookies);
			JSONArray jsonArray = JSONObject.parseObject(httpGetBili).getJSONArray("data");
			if(jsonArray.size() >0) {
				//进线程前创建collectDataEntity
				collectDataEntity.setTaskstatus("已提交待处理");
				collectDataEntity.setCreatetime(DateUtils.formatDateTime(new Date()));
				collectDataEntity.setCount(String.valueOf(jsonArray.size()));
				CollectDataEntity save = collectdDataDao.save(collectDataEntity);
				//提交线程
				exec.execute(() -> {
					try {
						this.createBiliData(save, jsonArray);
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
				return new AjaxEntity(Global.ajax_success, "已提交线程处理,如填错但线程已开启请重启容器解决", null);
			}
			return new AjaxEntity(Global.ajax_uri_error, "数据为空 请检查收藏ID", null);
			
			
		}
		if(null != collectDataEntity.getPlatform() && collectDataEntity.getPlatform().equals("抖音") ) {
			if(Global.tiktokCookie.equals("")) {
				return new AjaxEntity(Global.ajax_uri_error, "此功能必须填写ck", null);
			}
			if(collectDataEntity.getOriginaladdress().contains("post") || collectDataEntity.getOriginaladdress().contains("like")) {
				try {
					//进线程前创建collectDataEntity
					collectDataEntity.setTaskstatus("已提交待处理");
					collectDataEntity.setCreatetime(DateUtils.formatDateTime(new Date()));
					collectDataEntity.setCount("0");
					CollectDataEntity save = collectdDataDao.save(collectDataEntity);
					//提交线程
					exec.execute(() -> {
						try {
							this.createDyData(save);
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
					return new AjaxEntity(Global.ajax_success, "已提交线程处理,如填错但线程已开启请重启容器解决", null);
					
				} catch (Exception e) {
					logger.error("异常"+e.getMessage());
				}
				
			}else {
				return new AjaxEntity(Global.ajax_uri_error, "请按页面要求填写地址", null);
			}
			
		}
		return null;
	}
	
	
	/**
	 * 方法需要代码优化  有时间再说
	 * @param entity
	 * @param json
	 * @throws Exception
	 */
	public void createBiliData(CollectDataEntity entity,JSONArray json) throws Exception {
		//线程开始  变更状态
		String videofile = uploadRealPath+"video/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM"); //真实地址
		entity.setTaskstatus("已开始处理");
		collectdDataDao.save(entity);
		for(int i = 0;i<json.size();i++) {
			JSONObject data = json.getJSONObject(i);
			String avid = data.getString("id");
			String bvid = data.getString("bvid");
			List<Map<String, String>> videoDataInfo = BiliUtil.getVideoDataInfo("/video/"+bvid);
//			Map<String, String> videoDataInfo = BiliUtil.getVideoDataInfo("/video/"+bvid);
			
			for(int y =0;y<videoDataInfo.size();y++) {
				Map<String, String> map = videoDataInfo.get(y);
				String status ="";
				if(map !=  null) {
					String filename =StringUtil.getFileName(map.get("title"), map.get("cid"));
					String cid = map.get("cid");
					List<VideoDataEntity> findByVideoid = videoDataService.findByVideoid(cid);
					if(findByVideoid.size() == 0) {
						Map<String, String> findVideoStreaming =  BiliUtil.findVideoStreamingNoData(map,"/video/"+bvid,Global.bilicookies,videofile,map.get("quality"));
						String coverunaddr =  savefile+"cover/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM"); //映射
						String videounaddr =  savefile+"video/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/"+findVideoStreaming.get("videoname");//映射
						 //封面down
						HttpUtil.downBiliFromUrl(findVideoStreaming.get("pic"), filename+".jpg", uploadRealPath+"cover/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM"));
						//封面down
						VideoDataEntity videoDataEntity = new VideoDataEntity(findVideoStreaming.get("cid"),findVideoStreaming.get("title"), findVideoStreaming.get("desc"), "哔哩", coverunaddr+"/"+filename+".jpg", findVideoStreaming.get("video"),videounaddr,bvid);
					    videoDataDao.save(videoDataEntity);
					    logger.info("收藏"+(i+1)+"下载流程结束");
					}
					 //新建明细
					status =findByVideoid.size() == 0?"已完成":"已完成(未下载已存在)";
				}else {
					status ="视频异常下载失败";
				}
			   
			    CollectDataDetailEntity collectDataDetailEntity = new CollectDataDetailEntity();
			    collectDataDetailEntity.setDataid(entity.getId());
			    collectDataDetailEntity.setVideoname(map.get("title"));
			    collectDataDetailEntity.setVideoid(map == null ?bvid:map.get("cid"));
			    collectDataDetailEntity.setOriginaladdress(bvid);
			    collectDataDetailEntity.setStatus(status);
			    collectDataDetailEntity.setCreatetime(DateUtils.formatDateTime(new Date()));
			    collectDataDetailService.save(collectDataDetailEntity);
			    //修改主体
			    String carriedout = entity.getCarriedout() == null ?"1":String.valueOf(Integer.parseInt(entity.getCarriedout())+1);
			    entity.setCarriedout(carriedout);
			    collectdDataDao.save(entity);
			    Thread.sleep(2500);
				
			}
			
		
		}
		entity.setTaskstatus("处理完成");
		entity.setEndtime(DateUtils.formatDateTime(new Date()));
		collectdDataDao.save(entity);
		System.gc();
		
	}
	
	
	public void createDyData(CollectDataEntity entity) throws Exception {

		logger.info("任务开始"+entity.getOriginaladdress());
		JSONArray allDYData = this.getAllDYData(entity);
		
		entity.setCount(String.valueOf(allDYData.size()));
		entity.setTaskstatus("已开始处理");
		collectdDataDao.save(entity);
		
		for(int i = 0;i<allDYData.size();i++) {
//			System.out.println(allDYData.get(i));
			logger.info(entity.getOriginaladdress()+"任务中第"+i+"个");
			String status ="";
			JSONObject aweme_detail = allDYData.getJSONObject(i);	
			String aweme_type = aweme_detail.getString("aweme_type");
			String awemeId = aweme_detail.getString("aweme_id");
			try {
				if(aweme_type.equals("68")) {
					status ="图集不支持下载";
			 		Thread.sleep(2500);
				    CollectDataDetailEntity collectDataDetailEntity = new CollectDataDetailEntity();
				    collectDataDetailEntity.setDataid(entity.getId());
				    collectDataDetailEntity.setVideoid(awemeId);
				    collectDataDetailEntity.setOriginaladdress(awemeId);
				    collectDataDetailEntity.setStatus(status);
				    collectDataDetailEntity.setCreatetime(DateUtils.formatDateTime(new Date()));
				    collectDataDetailService.save(collectDataDetailEntity);
				    //修改主体
				    String carriedout = entity.getCarriedout() == null ?"1":String.valueOf(Integer.parseInt(entity.getCarriedout())+1);
				    entity.setCarriedout(carriedout);
				    collectdDataDao.save(entity);
					continue;
				}
			} catch (Exception e) {
				//异常了 没有type
				status ="视频异常";
		 		Thread.sleep(2500);
			    CollectDataDetailEntity collectDataDetailEntity = new CollectDataDetailEntity();
			    collectDataDetailEntity.setDataid(entity.getId());
			    collectDataDetailEntity.setVideoid(awemeId);
			    collectDataDetailEntity.setOriginaladdress(awemeId);
			    collectDataDetailEntity.setStatus(status);
			    collectDataDetailEntity.setCreatetime(DateUtils.formatDateTime(new Date()));
			    collectDataDetailService.save(collectDataDetailEntity);
			    //修改主体
			    String carriedout = entity.getCarriedout() == null ?"1":String.valueOf(Integer.parseInt(entity.getCarriedout())+1);
			    entity.setCarriedout(carriedout);
			    collectdDataDao.save(entity);
				continue;
				
			}
			String coveruri = "";
			JSONArray cover = aweme_detail.getJSONObject("video").getJSONObject("cover").getJSONArray("url_list");
			if(cover.size() >=2) {
				coveruri = cover.getString(2);
			}else {
				coveruri = cover.getString(0);
			}
			JSONArray jsonArray = aweme_detail.getJSONObject("video").getJSONObject("play_addr").getJSONArray("url_list");
			String videoplay = "";
			if(jsonArray.size() >=2) {
				videoplay = jsonArray.getString(2);
			}else {
				videoplay = jsonArray.getString(0);
			}
			String desc = aweme_detail.getString("desc");
			

			List<VideoDataEntity> findByVideoid = videoDataService.findByVideoid(awemeId);
			if(findByVideoid.size()==0) {
				 // 复制代码 懒得优化 后期再说
				 String filename = StringUtil.getFileName(desc, awemeId);
			     String videofile = Global.down_path+"/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/"+filename+".mp4";
		         String videounrealaddr = savefile+"video/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/"+filename+".mp4";
		         String coverunaddr =  savefile+"cover/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/"+filename+".jpg";
		        
		         logger.info("已使用批量下载,下载器类型为:"+Global.downtype);
		         if(Global.downtype.equals("a2")) {
			      	   Aria2Util.sendMessage(Global.a2_link,  Aria2Util.createDouparameter(videoplay, Global.down_path+"/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM"), filename+".mp4", Global.a2_token,Global.tiktokCookie));
			      	   videofile = "/app/resources/video/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/"+filename+".mp4";
		         }
		         if(Global.downtype.equals("http")) {
		        	//内置下载器
		        	HttpUtil.downDouFromUrl(videoplay, filename+".mp4","/app/resources/video/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM"),Global.tiktokCookie);
		        	videofile = "/app/resources/video/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/"+filename+".mp4";
		         }
		         HttpUtil.downLoadFromUrl(coveruri, filename+".jpg", uploadRealPath+"cover/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/");
		         VideoDataEntity videoDataEntity = new VideoDataEntity(awemeId,desc, desc, "抖音", coverunaddr, videofile,videounrealaddr,entity.getOriginaladdress());
		         videoDataDao.save(videoDataEntity);
		 		logger.info("下载流程结束");
			}
			if(status.equals("")) {
				status =findByVideoid.size() == 0?"已完成":"已完成(未下载已存在)";
			}
	 		Thread.sleep(2500);
		    CollectDataDetailEntity collectDataDetailEntity = new CollectDataDetailEntity();
		    collectDataDetailEntity.setVideoname(desc);
		    collectDataDetailEntity.setDataid(entity.getId());
		    collectDataDetailEntity.setVideoid(awemeId);
		    collectDataDetailEntity.setOriginaladdress(awemeId);
		    collectDataDetailEntity.setStatus(status);
		    collectDataDetailEntity.setCreatetime(DateUtils.formatDateTime(new Date()));
		    collectDataDetailService.save(collectDataDetailEntity);
		    //修改主体
		    String carriedout = entity.getCarriedout() == null ?"1":String.valueOf(Integer.parseInt(entity.getCarriedout())+1);
		    entity.setCarriedout(carriedout);
		    collectdDataDao.save(entity);
	 		
		}
		entity.setTaskstatus("处理完成");
		entity.setEndtime(DateUtils.formatDateTime(new Date()));
		collectdDataDao.save(entity);
		System.gc();
		logger.info("任务结束"+entity.getOriginaladdress());
	}
	

	public JSONArray getAllDYData(CollectDataEntity entity) throws IOException, InterruptedException {
		String api ="";
		String sign = "aid=6383&sec_user_id=#uid#&count=35&max_cursor=#max_cursor#&cookie_enabled=true&platform=PC&downlink=10";
		if(entity.getOriginaladdress().contains("post")) {
			api = "https://www.douyin.com/aweme/v1/web/aweme/post/?";
		}
		if(entity.getOriginaladdress().contains("like")) {
			api = "https://www.douyin.com/aweme/v1/web/aweme/favorite/?";
		}
		String sec_user_id = entity.getOriginaladdress().replaceAll("post", "").replaceAll("like", "");
		String singnew = sign.replaceAll("#uid#", sec_user_id);
		api =api+singnew;
		JSONArray dyNextData = this.getDYNextData(api, new JSONArray(),"0",singnew);
		return dyNextData;
		
	}
	
	public JSONArray  getDYNextData(String api,JSONArray data,String max_cursor,String sign) throws IOException, InterruptedException {
//		System.out.println(sign);
		JSONObject json =  new JSONObject();
		String newsign = sign.replaceAll("#max_cursor#", max_cursor);
//		System.out.println(newsign);
		json.put("str", newsign);
		json.put("ua", "");
		String apiaddt = api.replaceAll("#max_cursor#", max_cursor);
		JSONObject token = HttpUtil.doPostNew(Global.analysiSserver+"/spirit-token-update", json);
		logger.info("使用的:"+Global.analysiSserver+"服务器");
		String xbogus = token.getJSONObject("data").getString("xbogus");
		apiaddt = apiaddt+"&X-Bogus="+xbogus;
//		System.out.println(apiaddt);
		String httpget = DouUtil.httpget(apiaddt, Global.tiktokCookie);
//		System.out.println(httpget);
		JSONObject parseObject = JSONObject.parseObject(httpget);
		JSONArray jsonArray = parseObject.getJSONArray("aweme_list");
		max_cursor = parseObject.getString("max_cursor");
		if(!max_cursor.equals("0")) {
			//需要递归
			data.addAll(jsonArray);
			Thread.sleep(2500);
			return this.getDYNextData(api, data, max_cursor,sign);
		}else {
			data.addAll(jsonArray);
			return data;
		}
	}

}
