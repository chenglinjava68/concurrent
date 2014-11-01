package dbcache.service.impl;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;

import dbcache.conf.CacheConfig;
import dbcache.conf.CacheType;
import dbcache.conf.PersistType;
import dbcache.model.CacheObject;
import dbcache.model.IEntity;
import dbcache.model.UpdateStatus;
import dbcache.model.WeakCacheEntity;
import dbcache.model.WeakCacheObject;
import dbcache.proxy.asm.EntityAsmFactory;
import dbcache.proxy.asm.IndexMethodAspect;
import dbcache.service.Cache;
import dbcache.service.ConfigFactory;
import dbcache.service.DbCacheMBean;
import dbcache.service.DbCacheService;
import dbcache.service.DbIndexService;
import dbcache.service.DbPersistService;
import dbcache.utils.AsmUtils;
import dbcache.utils.ThreadUtils;

/**
 * DbCached缓存模块配置服务实现
 * @author Jake
 * @date 2014年9月14日下午8:57:54
 */
@Component
public class ConfigFactoryImpl implements ConfigFactory, DbCacheMBean {

	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(ConfigFactoryImpl.class);


	private static final String entityClassProperty = "clazz";

	private static final String cacheProperty = "cache";

	private static final String proxyCacheConfigProperty = "cacheConfig";

	private static final String indexServiceProperty = "indexService";

	private static final String dbPersistServiceProperty = "dbPersistService";

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private IndexMethodAspect methodAspect;

	/**
	 * 即时持久化服务
	 */
	@Autowired
	@Qualifier("inTimeDbPersistService")
	private DbPersistService intimeDbPersistService;

	/**
	 * 延迟持久化服务
	 */
	@Autowired
	@Qualifier("delayDbPersistService")
	private DbPersistService delayDbPersistService;


	/**
	 * DbCacheService实例映射
	 */
	@SuppressWarnings("rawtypes")
	private ConcurrentMap<Class<? extends IEntity>, DbCacheService> dbCacheServiceBeanMap = new ConcurrentHashMap<Class<? extends IEntity>, DbCacheService>();

	/**
	 * 配置映射
	 */
	private ConcurrentMap<Class<?>, CacheConfig> cacheConfigMap = new ConcurrentHashMap<Class<?>, CacheConfig>();

	/**
	 * 持久化服务
	 */
	private ConcurrentMap<PersistType, DbPersistService> persistServiceMap = new ConcurrentHashMap<PersistType, DbPersistService>();


	@SuppressWarnings({ "rawtypes" })
	@Override
	public DbCacheService getDbCacheServiceBean(Class<? extends IEntity> clz) {

		DbCacheService service = this.dbCacheServiceBeanMap.get(clz);
		if(service != null) {
			return service;
		}

		//创建对应实体的CacheService
		service = this.createCacheService(clz);
		dbCacheServiceBeanMap.putIfAbsent(clz, service);
		service = dbCacheServiceBeanMap.get(clz);

		return service;
	}


	@SuppressWarnings("rawtypes")
	private DbCacheService createCacheService(Class<? extends IEntity> clz) {

		DbCacheService service = null;

		try {
			//获取缓存配置
			CacheConfig cacheConfig = this.getCacheConfig(clz);

			//创建新的bean
			service = applicationContext.getAutowireCapableBeanFactory().createBean(DbCacheServiceImpl.class);

			//设置实体类
			Field clazzField = DbCacheServiceImpl.class.getDeclaredField(entityClassProperty);
			inject(service, clazzField, clz);


			//初始化代理类
			Class<?> proxyClazz = EntityAsmFactory.getEntityEnhancedClass(clz, methodAspect);
			cacheConfig.setProxyClazz(proxyClazz);

			Field cacheConfigField = DbCacheServiceImpl.class.getDeclaredField(proxyCacheConfigProperty);
			inject(service, cacheConfigField, cacheConfig);


			//初始化缓存实例
			Field cacheField = DbCacheServiceImpl.class.getDeclaredField(cacheProperty);
			Class<?> cacheClass = cacheConfig.getCacheType().getCacheClass();
			Cache cache = (Cache) applicationContext.getAutowireCapableBeanFactory().createBean(cacheClass);
			int concurrencyLevel = cacheConfig.getConcurrencyLevel() == 0? Runtime.getRuntime().availableProcessors() : cacheConfig.getConcurrencyLevel();
			cache.init(cacheConfig.getEntitySize(), concurrencyLevel);
			inject(service, cacheField, cache);


			//设置持久化PersistType方式的dbPersistService
			Field dbPersistServiceField = DbCacheServiceImpl.class.getDeclaredField(dbPersistServiceProperty);
			PersistType persistType = cacheConfig.getPersistType();
			DbPersistService dbPersistService = persistServiceMap.get(persistType);
			if(dbPersistService == null) {
				dbPersistService = (DbPersistService) applicationContext.getBean(persistType.getDbPersistServiceClass());
				persistServiceMap.putIfAbsent(persistType, dbPersistService);
				dbPersistService = persistServiceMap.get(persistType);
				dbPersistService.init();
			}
			inject(service, dbPersistServiceField, dbPersistService);


			//修改IndexService的cache
			Field indexServiceField = DbCacheServiceImpl.class.getDeclaredField(indexServiceProperty);
			ReflectionUtils.makeAccessible(indexServiceField);
			Class<?> indexServiceClass = indexServiceField.get(service).getClass();
			DbIndexService indexService = (DbIndexService) applicationContext.getAutowireCapableBeanFactory().createBean(indexServiceClass);
			inject(service, indexServiceField, indexService);

			// 索引服务缓存设置
			Cache indexCache = (Cache) applicationContext.getAutowireCapableBeanFactory().createBean(cacheConfig.getIndexCacheClass());
			// 初始化索引缓存
			indexCache.init(cacheConfig.getIndexSize() > 0 ? cacheConfig.getIndexSize() : cacheConfig.getEntitySize(), concurrencyLevel);

			Field cacheField1 = indexService.getClass().getDeclaredField(cacheProperty);
			ReflectionUtils.makeAccessible(cacheField1);
			inject(indexService, cacheField1, indexCache);

			//修改IndexService的cacheConfig
			Field cacheConfigField1 = indexService.getClass().getDeclaredField(proxyCacheConfigProperty);
			inject(indexService, cacheConfigField1, cacheConfig);


		} catch(Exception e) {
			e.printStackTrace();
		}

		return service;
	}


	/**
	 * 获取实体类的CacheConfig
	 * @param clz 实体类
	 * @return
	 */
	@Override
	public CacheConfig getCacheConfig(Class<?> clz) {
		CacheConfig cacheConfig = cacheConfigMap.get(clz);

		//初始化CacheConfig配置
		if(cacheConfig == null) {
			cacheConfig = CacheConfig.valueOf(clz);

			final Map<String, Field> indexes = new HashMap<String, Field>();
			ReflectionUtils.doWithFields(clz, new FieldCallback() {
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					if (field.isAnnotationPresent(org.hibernate.annotations.Index.class) ||
							field.isAnnotationPresent(dbcache.annotation.Index.class)) {
						String indexName = null;
						org.hibernate.annotations.Index indexAno = field.getAnnotation(org.hibernate.annotations.Index.class);
						if(indexAno != null) {
							indexName = indexAno.name();
						} else {
							dbcache.annotation.Index indexAno1 = field.getAnnotation(dbcache.annotation.Index.class);
							indexName = indexAno1.name();
						}

						indexes.put(indexName, field);
					}
				}
			});
			cacheConfig.setIndexes(indexes);

			cacheConfigMap.put(clz, cacheConfig);
		}
		return cacheConfig;
	}


	/**
	 * 注入属性
	 * @param bean bean
	 * @param field 属性
	 * @param val 值
	 */
	private void inject(Object bean, Field field, Object val) {
		ReflectionUtils.makeAccessible(field);
		try {
			field.set(bean, val);
		} catch (Exception e) {
			FormattingTuple message = MessageFormatter.format("属性[{}]注入失败", field);
			logger.debug(message.getMessage());
			throw new IllegalStateException(message.getMessage(), e);
		}
	}




	@SuppressWarnings("rawtypes")
	@Override
	public IEntity<?> createProxyEntity(IEntity<?> entity, Class<?> proxyClass, DbIndexService indexService) {
		CacheConfig cacheConfig = getCacheConfig(entity.getClass());
		// 判断是否启用索引服务
		if(cacheConfig == null || !cacheConfig.isEnableIndex()) {
			return entity;
		}
		return AsmUtils.getProxyEntity(proxyClass, entity, indexService);
	}


	@SuppressWarnings("unchecked")
	@Override
	public IEntity<?> wrapEntity(IEntity<?> entity, Class<?> entityClazz, Cache cache, Object key) {
		CacheConfig cacheConfig = getCacheConfig(entityClazz);
		// 判断是否开启弱引用
		if(cacheConfig == null || cacheConfig.getCacheType() != CacheType.WEEKMAP) {
			return entity;
		}
		return WeakCacheEntity.valueOf(entity, cache.getReferencequeue(), key);
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public CacheObject<?> createCacheObject(IEntity<?> entity,
			Class<?> entityClazz, DbIndexService<?> indexService, Object key, Cache cache, UpdateStatus updateStatus) {
		CacheConfig cacheConfig = getCacheConfig(entityClazz);
		// 判断是否开启弱引用
		if(cacheConfig == null || cacheConfig.getCacheType() != CacheType.WEEKMAP) {
			return new CacheObject(entity, entity.getId(), entity.getClass(),
					this.createProxyEntity(entity,
							cacheConfig.getProxyClazz(), indexService), updateStatus);
		}
		return new WeakCacheObject((WeakCacheEntity) this.wrapEntity(entity, entityClazz, cache, key),
				entity.getId(), entityClazz, (WeakCacheEntity) this.wrapEntity(this.createProxyEntity(entity,
						cacheConfig.getProxyClazz(),
						indexService), entityClazz, cache, key), key, updateStatus);
	}


	@Override
	@SuppressWarnings("rawtypes")
	public void registerDbCacheServiceBean(Class<? extends IEntity> clz,
			DbCacheService dbCacheService) {
		dbCacheServiceBeanMap.put(clz, dbCacheService);
	}


	//-----------------------JMX接口---------------------


	/**
	 * 注册JMX服务
	 * @throws MalformedObjectNameException
	 * @throws NotCompliantMBeanException
	 * @throws MBeanRegistrationException
	 * @throws InstanceAlreadyExistsException
	 */
	@PostConstruct
	public void init() throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
		// Get the Platform MBean Server
		final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

	    // Construct the ObjectName for the MBean we will register
	    final ObjectName name = new ObjectName("dbcache.service:type=DbCacheMBean");

	    final StandardMBean mbean = new StandardMBean(this, DbCacheMBean.class);

	    // Register the Hello World MBean
	    mbs.registerMBean(mbean, name);
	}


	@Override
	@SuppressWarnings("rawtypes")
	public Map<String, String> getDbCacheServiceBeanInfo() {
		Map<String, String> infoMap = new HashMap<String, String>();
		for(Entry<Class<? extends IEntity>, DbCacheService> entry : dbCacheServiceBeanMap.entrySet()) {
			infoMap.put(entry.getKey().getName(), entry.getValue().toString());
		}
		return infoMap;
	}


	@Override
	public Map<String, String> getCacheConfigInfo() {
		Map<String, String> infoMap = new HashMap<String, String>();
		for(Entry<Class<?>, CacheConfig> entry : cacheConfigMap.entrySet()) {
			infoMap.put(entry.getKey().getName(), entry.getValue().toString());
		}
		return infoMap;
	}


	@Override
	public Map<String, Object> getDbPersistInfo() {
		Map<String, Object> infoMap = new HashMap<String, Object>();
		infoMap.put("intimeDbPersistService", ThreadUtils.dumpThreadPool(
				"intimeDbPersistServiceTheadPool",
				this.intimeDbPersistService.getThreadPool()));
		infoMap.put("delayDbPersistService", ThreadUtils.dumpThreadPool(
				"delayDbPersistServiceTheadPool",
				this.delayDbPersistService.getThreadPool()));
		return infoMap;
	}



}
