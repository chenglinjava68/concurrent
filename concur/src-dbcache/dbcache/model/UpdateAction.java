package dbcache.model;

import java.io.Serializable;

/**
 * 更新实体的行为
 * @author jake
 * @date 2014-7-31-下午9:56:42
 */
public class UpdateAction<T extends IEntity<PK>, PK extends Comparable<PK> & Serializable> {
	
	/**
	 * 实体
	 */
	private CacheObject<T> cacheObject;
	
	/**
	 * 实体更新类型
	 */
	private UpdateType updateType;
	
	/**
	 * 当前更改的版本号
	 */
	private long editVersion;
	
	/**
	 * 当前入库的版本号
	 */
	private long dbVersion;
	
	/**
	 * 创建时间
	 */
	private long createTime;
	
	/**
	 * 保持实体引用直到任务被处理,防止任务处理前被回收
	 */
	private T entity;
	
	
	/**
	 * 构造方法
	 * @param cacheObject 实体
	 * @param updateType 更新类型
	 * @param editVersion 更改的版本号
	 */
	public UpdateAction(CacheObject<T> cacheObject, UpdateType updateType, long editVersion, long dbVersion) {
		this.cacheObject = cacheObject;
		this.updateType = updateType;
		this.editVersion = editVersion;
		this.dbVersion = dbVersion;
		this.createTime = System.currentTimeMillis();
		this.entity = cacheObject.getEntity();
	}
	
	/**
	 * 获取实例
	 * @param cacheObject 实体
	 * @param updateType 更新类型
	 * @param editVersion 更改的版本号
	 * @param dbVersion 入库的版本号
	 * @return
	 */
	public static <T extends IEntity<PK>, PK extends Comparable<PK> & Serializable> UpdateAction<T, PK> valueOf(CacheObject<T> cacheObject, UpdateType updateType) {
		//更新修改版本号
		long editVersion = cacheObject.increseEditVersion();
		long dbVersion = cacheObject.getDbVersion();
		return new UpdateAction<T, PK>(cacheObject, updateType, editVersion, dbVersion);
	}
	
	
	public CacheObject<T> getCacheObject() {
		return cacheObject;
	}
	
	public UpdateType getUpdateType() {
		return updateType;
	}
	
	public long getEditVersion() {
		return editVersion;
	}

	public long getDbVersion() {
		return dbVersion;
	}

	public void setDbVersion(long dbVersion) {
		this.dbVersion = dbVersion;
	}

	public long getCreateTime() {
		return createTime;
	}

	public Object getEntity() {
		return entity;
	}

}
