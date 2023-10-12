package com.flower.spirit.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.flower.spirit.common.AjaxEntity;
import com.flower.spirit.config.Global;
import com.flower.spirit.dao.TikTokConfigDao;
import com.flower.spirit.entity.TikTokConfigEntity;

@Service
public class TikTokConfigService {
	
	
	@Autowired
	private TikTokConfigDao tikTokConfigDao;

	public List<TikTokConfigEntity> findAll(TikTokConfigEntity tikTokConfigEntity) {
		return tikTokConfigDao.findAll();
	}
	
	public TikTokConfigEntity getData() {
		List<TikTokConfigEntity> findAll = tikTokConfigDao.findAll();
		if(findAll.size() ==0) {
			TikTokConfigEntity tikTokConfigEntity = new TikTokConfigEntity();
			TikTokConfigEntity save = tikTokConfigDao.save(tikTokConfigEntity);
			return save;
		}
		return findAll.get(0);
	}

	public AjaxEntity updateTikTokConfig(TikTokConfigEntity tikTokConfigEntity) {
		tikTokConfigDao.save(tikTokConfigEntity);
		if(null != tikTokConfigEntity.getCookies() && !"".equals(tikTokConfigEntity.getCookies())) {
			Global.tiktokCookie = tikTokConfigEntity.getCookies();
		}
		if(null != tikTokConfigEntity.getAnalysisserver() && !"".equals(tikTokConfigEntity.getAnalysisserver())) {
			Global.analysiSserver = tikTokConfigEntity.getAnalysisserver();
		}
		return new AjaxEntity(Global.ajax_success, "操作成功", tikTokConfigEntity);
	}

}
