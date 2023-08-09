package com.flower.spirit.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.flower.spirit.config.Global;
public class BiliUtil {
	
	
	/**
	 * 
	 * 方法需要代码优化  有时间再说
	 * @throws Exception 
	 * 
	 */
	public static  Map<String, String> findVideoStreamingNoData(Map<String, String> videoDataInfo,String url,String token,String filepath,String quality) throws Exception {
		String api = buildInterfaceAddress(videoDataInfo.get("aid"), videoDataInfo.get("cid"), token,quality);
		String httpGetBili = HttpUtil.httpGetBili(api, "UTF-8", token);
		JSONObject parseObject = JSONObject.parseObject(httpGetBili);
//		System.out.println(parseObject);
		String video = parseObject.getJSONObject("data").getJSONArray("durl").getJSONObject(0).getString("url");
		String filename = StringUtil.getFileName(videoDataInfo.get("title"), videoDataInfo.get("cid"));
		if(Global.downtype.equals("http")) {
			HttpUtil.downBiliFromUrl(video, filename+".mp4", filepath);
		}
		if(Global.downtype.equals("a2")) {
			Aria2Util.sendMessage(Global.a2_link,  Aria2Util.createBiliparameter(video, Global.down_path+"/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM"), filename+".mp4", Global.a2_token));
		}
		videoDataInfo.put("video", filepath+"/"+filename+".mp4");
		videoDataInfo.put("videoname", filename+".mp4");
		return videoDataInfo;
	}
	
	/**
	 * 方法需要代码优化  有时间再说
	 * @throws Exception 
	 * 
	 */
	public static List<Map<String, String>> findVideoStreaming(String url,String token,String filepath) throws Exception {
	
		List<Map<String, String>> videoDataInfo = BiliUtil.getVideoDataInfo(url);
		List<Map<String, String>> res = new ArrayList<Map<String,String>>();
		for(int i =0;i<videoDataInfo.size();i++) {
			Map<String, String> map = videoDataInfo.get(i);
			String quality = map.get("quality");
			String api = buildInterfaceAddress(map.get("aid"), map.get("cid"), token,quality);
			String httpGetBili = HttpUtil.httpGetBili(api, "UTF-8", token);
			JSONObject parseObject = JSONObject.parseObject(httpGetBili);
			System.out.println(parseObject);
			String filename = StringUtil.getFileName(map.get("title"), map.get("cid"));
			if(Integer.valueOf(Global.bilibitstream) >=120 && quality.equals("1")) {
				//执行DASH格式合并  默认取第一个  最大清晰度
				String video = parseObject.getJSONObject("data").getJSONObject("dash").getJSONArray("video").getJSONObject(0).getString("base_url");
				String audio = parseObject.getJSONObject("data").getJSONObject("dash").getJSONArray("audio").getJSONObject(0).getString("base_url");
				//创建临时目录用于合并生成
				if(Global.downtype.equals("http")) {
					//http  需要创建临时目录
					String newpath =filepath+"/"+map.get("cid");
					FileUtils.createDirectory(newpath);
					HttpUtil.downBiliFromUrl(video, filename+"-video.m4s", newpath);
					HttpUtil.downBiliFromUrl(audio, filename+"-audio.m4s", newpath);
					
				}
				if(Global.downtype.equals("a2")) {
					//a2 不需要 目录有a2托管  此处路径应该可以优化
					String a2path= Global.down_path+"/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM")+"/"+map.get("cid");
					Aria2Util.sendMessage(Global.a2_link,  Aria2Util.createBiliparameter(video,a2path , filename+"-video.m4s", Global.a2_token));
					Aria2Util.sendMessage(Global.a2_link,  Aria2Util.createBiliparameter(audio,a2path , filename+"-audio.m4s", Global.a2_token));
				}
				map.put("video", filepath+"/"+filename+".mp4");
				map.put("videoname", filename+".mp4");
				res.add(map);
				return res;
			}
			//普通mp4
			String video = parseObject.getJSONObject("data").getJSONArray("durl").getJSONObject(0).getString("url");
			if(Global.downtype.equals("http")) {
				HttpUtil.downBiliFromUrl(video, filename+".mp4", filepath);
			}
			if(Global.downtype.equals("a2")) {
				Aria2Util.sendMessage(Global.a2_link,  Aria2Util.createBiliparameter(video, Global.down_path+"/"+DateUtils.getDate("yyyy")+"/"+DateUtils.getDate("MM"), filename+".mp4", Global.a2_token));
			}
			map.put("video", filepath+"/"+filename+".mp4");
			map.put("videoname", filename+".mp4");
			res.add(map);
		}
		
		
		
		return res;
	}
	
	
	/**
	 * 方法需要代码优化  有时间再说
	 * @param url
	 * @return
	 */
	public static List<Map<String, String>> getVideoDataInfo(String url) {
		List<Map<String, String>> res = new ArrayList<Map<String,String>>();
		String parseEntry = BiliUtil.parseEntry(url);
		String api ="";
		if(parseEntry.contains("BV")) {
			api ="https://api.bilibili.com/x/web-interface/view?bvid="+parseEntry.substring(2, parseEntry.length());
		}
		if(parseEntry.contains("av")) {
			api="https://api.bilibili.com/x/web-interface/view?aid="+parseEntry.substring(2, parseEntry.length());
		}
		String serchPersion = HttpUtil.getSerchPersion(api, "UTF-8");
		JSONObject videoData = JSONObject.parseObject(serchPersion);
		System.out.println(serchPersion);
		if(videoData.getString("code").equals("0")) {		
			//优化多集问题 从page 里取
			
			String bvid = videoData.getJSONObject("data").getString("bvid");
			String aid = videoData.getJSONObject("data").getString("aid");
//			String cid = videoData.getJSONObject("data").getString("cid");
//			String title = videoData.getJSONObject("data").getString("title");
//			String pic = videoData.getJSONObject("data").getString("pic");
			String desc = videoData.getJSONObject("data").getString("desc");
			JSONObject dimension = videoData.getJSONObject("data").getJSONObject("dimension");
			Integer width = dimension.getInteger("width");
			Integer height = dimension.getInteger("height");
	
			JSONArray jsonArray = videoData.getJSONObject("data").getJSONArray("pages");
	
			for(int i = 0;i<jsonArray.size();i++) {
				Map<String, String> data = new HashMap<String, String>(); 
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				String cid = jsonObject.getString("cid");
				String title = jsonObject.getString("part");
				String pic = jsonObject.getString("first_frame");
				data.put("aid", aid);
				data.put("bvid", bvid);
				data.put("desc", desc);
				if(width>1920 ||height >1920) {
					data.put("quality", "1");
				}else {
					data.put("quality", "0");
				}
				if(null == pic) {
					pic = videoData.getJSONObject("data").getString("pic");
				}
				data.put("cid", cid);
				data.put("title", title);
				data.put("pic", pic);
				res.add(data);
			}
			return res;
		}else {
			return null;
		}
	}
	
	
	public static String parseEntry(String url) {
		if(url.contains("/video/av") || url.contains("/video/BV") ) {
			return BiliUtil.findUrlAidOrBid(url);
		}else {
			Document document = null;
			try {
				document = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36").get();
				 String baseUri = document.baseUri();
				 return BiliUtil.findUrlAidOrBid(baseUri);
			} catch (IOException e1) {
				
			}
		}
		return "";
	}
	
	
	public static String findUrlAidOrBid(String url) {
		String replace ="";
		if(url.contains("http")) {
			replace = url.replaceAll("http://", "").replaceAll("https://", "").replace("www.bilibili.com/video/", "");
			int indexOf = replace.indexOf("/");
			String id = replace.substring(0, indexOf);
			return id;
		}else {
			replace = url.replaceAll("/video/", "");
			return replace;
		}
	}
	
	public static String buildInterfaceAddress(String aid,String cid,String token,String quality) {
		String bilibitstream = Global.bilibitstream;
		if(quality.equals("0")) {
			bilibitstream ="112"; //画质降级
		}
		String api ="https://api.bilibili.com/x/player/playurl?avid="+aid+"&cid="+cid;
		if(null != token && !token.equals("")) {
			if(!bilibitstream.equals("64")) {
				//vip
				if(Integer.valueOf(bilibitstream) >120) {
					api =api+"&qn=0";
				}else {
					api =api+"&qn="+bilibitstream;
				}
				
			}else {
				api =api+"&qn=80";
			}
			
		}else {
			api =api+"&qn=64";
		}
		api =api+"&fnver=0";  //固定 0
		switch (bilibitstream) {
		case "120":
			api =api+"&fourk=1&fnval="+Integer.toString(16|128);   //4k 传128
			break;
		case "125":
			api =api+"&fourk=1&fnval="+Integer.toString(16|64);   //hdr 传64
			break;
		case "126":
			api =api+"&fourk=1&fnval="+Integer.toString(16|512);   //杜比视界 传128
			break;
		case "127":
			api =api+"&fourk=1&fnval="+Integer.toString(16|1024);   //8k 传128
			break;
		default:
			api =api+"&fourk=0&fnval=1";
			break;
		}
		System.out.println(api);
		return api;
	}

	public static void main(String[] args) throws Exception {
		//video/BV1mz4y1q7Pb
		///video/BV1qM4y1w716
		List<Map<String, String>> findVideoStreaming = BiliUtil.findVideoStreaming("/video/BV1mz4y1q7Pb","buvid3=0E48097F-7998-C304-EA26-A098353B564E24664infoc; b_nut=1686316524; buvid4=19EFF99F-6593-1F59-BC30-D78EE91531EC24664-023060921-bpBB5Rug64HYSZoY1HTn2w%3D%3D; _uuid=92C36E106-9BF10-47105-DD10A-C83929B37CC757865infoc; rpdid=|(u||)R|~lYY0J'uY)YYkllY|; i-wanna-go-back=-1; header_theme_version=CLOSE; DedeUserID=3493262113376423; DedeUserID__ckMd5=d6afd5e2e0cb60b3; b_ut=5; FEED_LIVE_VERSION=V8; nostalgia_conf=-1; LIVE_BUVID=AUTO9316868115562984; CURRENT_BLACKGAP=0; CURRENT_FNVAL=4048; buvid_fp_plain=undefined; fingerprint=fe5542d30670ff1dc868f3bcd89b0534; buvid_fp=fe5542d30670ff1dc868f3bcd89b0534; home_feed_column=5; SESSDATA=cf15d78d%2C1706946744%2C5e06a%2A812WyG5Tqqb1IsCB42co5czmGI5HoS9rQENjjkTFkTGZrXkefl6eewX1fF_uDMZaWd9unlnAAAFwA; bili_jct=068800b8a727ccecfa7230a91c3c44f8; sid=72894xqr; CURRENT_QUALITY=112; PVID=2; bili_ticket=eyJhbGciOiJFUzM4NCIsImtpZCI6ImVjMDIiLCJ0eXAiOiJKV1QifQ.eyJleHAiOjE2OTE3NDE5MTEsImlhdCI6MTY5MTQ4MjcxMSwicGx0IjotMX0.fnyP8J8jj_uQpZAAtHDovqp6JDXLXoL9JdqOi7nTog4X60UYpV44xVT6TqMSOO6ZZy56KwASTrsr3rhWeg2AhcbXkCUcnq2xi0oj4cZqesGqS2QXzRJ-J4cZTO_GUg3a; bili_ticket_expires=1691741911; bp_video_offset_3493262113376423=827618465414119457; b_lsid=105EFBC32_189D82FE2E9; browser_resolution=1920-331","D:\\flower\\uploadFile");
		System.out.println(findVideoStreaming);
	}
}
