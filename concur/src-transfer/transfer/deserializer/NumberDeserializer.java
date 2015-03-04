package transfer.deserializer;

import transfer.Inputable;
import transfer.def.Config;
import transfer.def.Types;
import transfer.exception.IllegalTypeException;
import transfer.utils.BitUtils;
import transfer.utils.IntegerMap;
import transfer.utils.TypeUtils;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Number解析器
 * Created by Jake on 2015/2/24.
 */
public class NumberDeserializer implements Deserializer {


    @Override
    public <T> T deserialze(Inputable inputable, Type type, byte flag, IntegerMap referenceMap) {

        byte typeFlag = Config.getType(flag);

        if (typeFlag != Types.NUMBER) {
            throw new IllegalTypeException(typeFlag, Types.NUMBER, type);
        }

        byte extraFlag = Config.getExtra(flag);

        Number number = null;
        switch (extraFlag) {
            case Config.INT321:
                number = BitUtils.getInt1(inputable);
                if (type == int.class || type == Integer.class) {
                    return (T) number;
                }
                break;
            case Config.INT322:
                number = BitUtils.getInt2(inputable);
                if (type == int.class || type == Integer.class) {
                    return (T) number;
                }
                break;
            case Config.INT323:
                number = BitUtils.getInt3(inputable);
                if (type == int.class || type == Integer.class) {
                    return (T) number;
                }
                break;
            case Config.INT324:
                number = BitUtils.getInt(inputable);
                if (type == int.class || type == Integer.class) {
                    return (T) number;
                }
                break;
            case Config.INT641:
                number = BitUtils.getLong1(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.INT642:
                number = BitUtils.getLong2(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.INT643:
                number = BitUtils.getLong3(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.INT644:
                number = BitUtils.getLong4(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.INT645:
                number = BitUtils.getLong5(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.INT646:
                number = BitUtils.getLong6(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.INT647:
                number = BitUtils.getLong7(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.INT648:
                number = BitUtils.getLong(inputable);
                if (type == long.class || type == Long.class) {
                    return (T) number;
                }
                break;
            case Config.FLOAT:
                number = BitUtils.getFloat(inputable);
                if (type == float.class || type == Float.class) {
                    return (T) number;
                }
                break;
            case Config.DOUBLE:
                number = BitUtils.getDouble(inputable);
                if (type == double.class || type == Double.class) {
                    return (T) number;
                }
                break;
        }

        if (type == null || type == Number.class) {
            return (T) number;
        }

        if (type == short.class || type == Short.class) {
            return (T) Short.valueOf(number.shortValue());
        } else if (type == int.class || type == Integer.class) {
            return (T) Integer.valueOf(number.intValue());
        } else if (type == long.class || type == Long.class) {
            return (T) Long.valueOf(number.longValue());
        } else if (type == float.class || type == Float.class) {
            return (T) Float.valueOf(number.floatValue());
        } else if (type == double.class || type == Double.class) {
            return (T) Double.valueOf(number.doubleValue());
        } else if (type == byte.class || type == Byte.class) {
            return (T) TypeUtils.castToByte(number);
        } else if (type == boolean.class || type == Boolean.class) {
            return (T) Boolean.valueOf(number.intValue() == 1);
        } else if (type == AtomicInteger.class) {
            return (T) new AtomicInteger(number.intValue());
        } else if (type == AtomicLong.class) {
            return (T) new AtomicLong(number.longValue());
        }

        return (T) number;
    }


    private static NumberDeserializer instance = new NumberDeserializer();

    public static NumberDeserializer getInstance() {
        return instance;
    }

}
