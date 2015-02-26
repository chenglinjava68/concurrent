package transfer.serializer;

import transfer.Outputable;
import transfer.utils.IdentityHashMap;

/**
 * 类型编码接口
 * Created by Jake on 2015/2/23.
 */
public interface Serializer {


    /**
     * 编码方法
     * @param outputable 输出接口
     * @param object 目标对象
     * @param referenceMap 引用表
     */
    void serialze(Outputable outputable, Object object, IdentityHashMap referenceMap);


}
