package transfer.serializer;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dbcache.support.asm.util.AsmUtils;
import transfer.Outputable;
import transfer.compile.AsmSerializerContext;
import transfer.core.ClassInfo;
import transfer.core.FieldInfo;
import transfer.def.TransferConfig;
import transfer.def.Types;
import transfer.exception.CompileError;
import transfer.utils.BitUtils;
import transfer.utils.IdentityHashMap;
import transfer.utils.TypeUtils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Asm代理对象编码器
 * Created by Jake on 2015/2/23.
 */
public class ObjectAsmProxySerializer implements Serializer, Opcodes {


    @Override
    public void serialze(Outputable outputable, Object object, IdentityHashMap referenceMap) {

        if (object == null) {
            NULL_SERIALIZER.serialze(outputable, object, referenceMap);
            return;
        }

        Class<?> clazz = object.getClass().getSuperclass();

        ClassInfo classInfo = TransferConfig.getOrCreateClassInfo(clazz);

        outputable.putByte(Types.OBJECT);

        BitUtils.putInt(outputable, classInfo.getClassId());

        for (FieldInfo fieldInfo : classInfo.getFieldInfos()) {

            Serializer fieldSerializer = TransferConfig.getSerializer(TypeUtils.getRawClass(fieldInfo.getType()));

            Object fieldValue = fieldInfo.getFieldGetter().get(object);

            fieldSerializer.serialze(outputable, fieldValue, referenceMap);

        }

    }
    

    @Override
    public void compile(Type type, MethodVisitor mv, AsmSerializerContext context) {
    	
    	mv.visitCode();
        mv.visitVarInsn(ALOAD, 2);
        Label l1 = new Label();
        mv.visitJumpInsn(IFNONNULL, l1);

        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ICONST_1);
        mv.visitMethodInsn(INVOKEINTERFACE, "transfer/Outputable", "putByte", "(B)V", true);

        mv.visitInsn(RETURN);
        mv.visitLabel(l1);

        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

        mv.visitVarInsn(ALOAD, 1);
        mv.visitIntInsn(BIPUSH, (int) Types.OBJECT);
        mv.visitMethodInsn(INVOKEINTERFACE, "transfer/Outputable", "putByte", "(B)V", true);


        Class<?> clazz = TypeUtils.getRawClass(type);

        ClassInfo classInfo = TransferConfig.getOrCreateClassInfo(clazz.getSuperclass());

        mv.visitVarInsn(ALOAD, 1);
        mv.visitIntInsn(BIPUSH, classInfo.getClassId());
        mv.visitMethodInsn(INVOKESTATIC, "transfer/utils/BitUtils", "putInt2", "(Ltransfer/Outputable;I)V", false);


        for (FieldInfo fieldInfo : classInfo.getFieldInfos()) {

            Serializer fieldSerializer = TransferConfig.getSerializer(TypeUtils.getRawClass(fieldInfo.getType()));

            String serializerClassName = fieldSerializer.getClass().getName();

         //   mv.visitMethodInsn(INVOKESTATIC, AsmUtils.toAsmCls(serializerClassName), "getInstance", "()" + "L" + AsmUtils.toAsmCls(serializerClassName) + ";", false);

            mv.visitVarInsn(ALOAD, 0);
            
            mv.visitVarInsn(ALOAD, 1);

            PropertyDescriptor propertyDescriptor = null;
            try {
                propertyDescriptor = new PropertyDescriptor(fieldInfo.getFieldName(), clazz);
            } catch (IntrospectionException e) {
                e.printStackTrace();
                throw new CompileError(e);
            }

            //获取get方法
            final Method getMethod = propertyDescriptor.getReadMethod();
            final org.objectweb.asm.Type rt = org.objectweb.asm.Type.getReturnType(getMethod);
            final org.objectweb.asm.Type mt = org.objectweb.asm.Type.getType(getMethod);

            //获取this.target
            mv.visitVarInsn(ALOAD, 2);

            mv.visitMethodInsn(INVOKEVIRTUAL,
                    AsmUtils.toAsmCls(clazz.getName()), getMethod.getName(),
                    mt.toString());

            // 处理返回值类型 到 Object类型
            AsmUtils.withBoxingType(mv, rt);

            mv.visitVarInsn(ALOAD, 3);

            // 执行属性预编译
            MethodVisitor methodVisitor = context.invokeNextSerialize(fieldInfo.getFieldName(), mv);

            fieldSerializer.compile(fieldInfo.getType(), methodVisitor, context);

        }

        
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 4);
        mv.visitEnd();
    	
    }


    private static ObjectAsmProxySerializer instance = new ObjectAsmProxySerializer();

    public static ObjectAsmProxySerializer getInstance() {
        return instance;
    }
}