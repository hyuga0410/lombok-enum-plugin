package io.github.hyuga0410.lombok.enums.component;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EnumDescFactory
 *
 * @author pengqinglong
 * @since 2022/5/12
 */
public class EnumDescAugmentProvider extends PsiAugmentProvider {

    private static final Logger log = Logger.getInstance(EnumDescAugmentProvider.class.getName());
    private static final String ENUM_DESC = "io.github.hyuga0410.lombok.enums.annotations.EnumDesc";
    private static final String ENUM = "Enum";
    private static final String STRING_S = "String %s";
    private static final String DESC = "Desc";
    private static final String GET_S_S = "get%s%s";

    public EnumDescAugmentProvider() {
        log.debug("EnumDescAugmentProvider created");
    }

    /**
     * 允许将子元素添加到某些PSI元素的扩展，例如Java类的方法。
     * <p>
     * 类代码保持不变，但其方法访问器也包括从PsiAugmentProviders返回的结果。
     * <p>
     * 在代码模型的相同状态下，可以使用相同的参数多次调用增强器，从这些调用返回的PSI必须相等，并相应地实现Equals/hashCode()。
     *
     * @param psiElement 请求的增强成员的预期名称，如果要返回指定类的所有成员，则为空。
     *                   实现可以忽略此参数或将其用于优化。
     * @param psiClazz   Class<Psi>
     * @param nameHint   请求的增强成员的预期名称，如果要返回指定类的所有成员，则为空。
     *                   实现可以忽略此参数或将其用于优化。
     * @return List<Psi>
     */
    @Override
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement psiElement,
                                                                      @NotNull Class<Psi> psiClazz,
                                                                      @Nullable String nameHint) {
        final List<Psi> emptyResult = Collections.emptyList();
        // type不是Psi方法，或者psiElement不是Psi可扩展类，则返回
        if ((psiClazz != PsiMethod.class) || !(psiElement instanceof PsiExtensibleClass)) {
            return emptyResult;
        }

        final PsiClass psiClass = (PsiClass) psiElement;
        // psiElement Class是注解类型，或者是接口类型，则返回
        if (psiClass.isAnnotationType() || psiClass.isInterface()) {
            return emptyResult;
        }
        // @EnumDesc加在枚举类上无效，仅支持加在常规类或者常规类的枚举成员属性上
        if (psiClass.isEnum()) {
            return emptyResult;
        }
        // 获取psiElement所有字段
        PsiField[] fields = psiClass.getFields();
        if (fields.length == 0) {
            return emptyResult;
        }

        // 根据字段是否有@EnumDesc注解，分组，字段上有@EnumDesc的执行this.handleField()，类上有@EnumDesc的执行this.handleClass()
        Map<Boolean, List<PsiField>> fieldMap =
                Arrays.stream(fields)
                        .collect(Collectors.groupingBy(field -> field.getAnnotation(ENUM_DESC) != null));

        List<Psi> list = new ArrayList<>();

        // 字段上有@EnumDesc
        this.handleField(fieldMap.get(true), psiClass, list);
        // 类上有@EnumDesc
        this.handleClass(fieldMap.get(false), psiClass, list);

        return list;
    }

    /**
     * 处理类上有@EnumDesc的Field
     *
     * @param psiFields List<PsiField>
     * @param psiClass  PsiClass
     * @param result    List<Psi>
     */
    private <Psi extends PsiElement> void handleClass(List<PsiField> psiFields, PsiClass psiClass, List<Psi> result) {
        // 获取psiClass类上的@EnumDesc注解
        PsiAnnotation annotation = psiClass.getAnnotation(ENUM_DESC);
        if (annotation == null || psiFields == null || psiFields.size() == 0) {
            return;
        }
        // 提取枚举字段的fields
        List<PsiField> psiEnumFields = psiFields.stream().filter(this::isEnum).collect(Collectors.toList());

        List<JvmAnnotationAttribute> attributes = Objects.requireNonNull(annotation).getAttributes();

        if (attributes.size() == 0) {
            // 仅生成默认的desc方法
            psiEnumFields.forEach(psiField -> {
                String getMethodName = String.format(GET_S_S, upperCase(psiField.getName()), DESC);
                LightMethodBuilder method = createMethod(psiClass, psiField, getMethodName);
                if (Optional.ofNullable(method).isPresent()) {
                    result.add(cast(method));
                }
            });
            return;
        }

        JvmAnnotationAttributeValue attributeValue = attributes.get(0).getAttributeValue();

        // 支持动态拼接枚举扩展字段方法【常量】
        if (attributeValue instanceof JvmAnnotationConstantValue) {
            String value = (String) ((JvmAnnotationConstantValue) attributeValue).getConstantValue();
            if (value == null) {
                return;
            }
            psiEnumFields.forEach(psiField -> {
                String name = String.format(GET_S_S, upperCase(psiField.getName()), upperCase(value));
                LightMethodBuilder method = createMethod(psiClass, psiField, name);
                if (method != null) {
                    result.add(cast(method));
                }
            });
        } else if (attributeValue instanceof JvmAnnotationArrayValue) {
            // 支持动态拼接枚举扩展字段方法【数组入参】
            List<JvmAnnotationAttributeValue> values = ((JvmAnnotationArrayValue) attributeValue).getValues();
            for (JvmAnnotationAttributeValue tempValue : values) {
                if (tempValue instanceof JvmAnnotationConstantValue) {
                    String value = (String) ((JvmAnnotationConstantValue) tempValue).getConstantValue();
                    psiEnumFields.forEach(psiField -> {
                        String name = String.format(GET_S_S, upperCase(psiField.getName()), upperCase(Objects.requireNonNull(value)));
                        LightMethodBuilder method = createMethod(psiClass, psiField, name);
                        if (method != null) {
                            result.add(cast(method));
                        }
                    });
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <Psi> Psi cast(LightMethodBuilder method) {
        return (Psi) method;
    }

    /**
     * 处理字段上有@EnumDesc的Field
     *
     * @param psiFields List<PsiField>
     * @param psiClass  PsiClass
     * @param result    List<Psi>
     */
    private <Psi extends PsiElement> void handleField(List<PsiField> psiFields, PsiClass psiClass, List<Psi> result) {
        if (psiFields == null || psiFields.size() == 0) {
            return;
        }

        // 提取枚举字段的fields
        List<PsiField> psiEnumFields = psiFields.stream().filter(this::isEnum).collect(Collectors.toList());

        psiEnumFields.forEach(psiField -> {
            // 获取字段上的@EnumDesc
            PsiAnnotation annotation = psiField.getAnnotation(ENUM_DESC);
            // 获取字段名
            String psiFieldName = psiField.getName();
            // 获取@EnumDesc上的所有属性
            List<JvmAnnotationAttribute> attributes = Objects.requireNonNull(annotation).getAttributes();
            if (attributes.size() == 0) {
                String name = String.format(GET_S_S, upperCase(psiFieldName), DESC);
                LightMethodBuilder method = createMethod(psiClass, psiField, name);
                if (method != null) {
                    result.add(cast(method));
                }
                return;
            }

            // 支持动态拼接枚举扩展字段方法【常量】
            JvmAnnotationAttributeValue attributeValue = attributes.get(0).getAttributeValue();
            if (attributeValue instanceof JvmAnnotationConstantValue) {
                String value = (String) ((JvmAnnotationConstantValue) attributeValue).getConstantValue();
                if (value == null) {
                    return;
                }
                String name = String.format(GET_S_S, upperCase(psiFieldName), upperCase(value));
                LightMethodBuilder method = createMethod(psiClass, psiField, name);
                if (method != null) {
                    result.add(cast(method));
                }
                return;
            }

            // 支持动态拼接枚举扩展字段方法【数组入参】
            if (attributeValue instanceof JvmAnnotationArrayValue) {
                List<JvmAnnotationAttributeValue> values = ((JvmAnnotationArrayValue) attributeValue).getValues();
                for (JvmAnnotationAttributeValue tempValue : values) {
                    if (tempValue instanceof JvmAnnotationConstantValue) {
                        String value = (String) ((JvmAnnotationConstantValue) tempValue).getConstantValue();
                        String name = String.format(GET_S_S, upperCase(psiFieldName), upperCase(Objects.requireNonNull(value)));
                        LightMethodBuilder method = createMethod(psiClass, psiField, name);
                        if (method != null) {
                            result.add(cast(method));
                        }
                    }
                }
            }
        });
    }

    /**
     * 判断字段是否枚举字段
     *
     * @param psiField PsiField
     * @return boolean
     */
    private boolean isEnum(PsiField psiField) {
        PsiType psiType = psiField.getType();
        PsiType[] superTypes = psiType.getSuperTypes();
        if (superTypes.length == 0) {
            return false;
        }
        return superTypes[0].getPresentableText().startsWith(ENUM);
    }

    /**
     * 创建一个方法
     *
     * @param psiClass PsiClass
     * @param psiField PsiField
     * @param name     name
     * @return LightMethodBuilder
     */
    private LightMethodBuilder createMethod(PsiClass psiClass, PsiField psiField, String name) {
        PsiElement psiElement = psiClass.getContext();

        String context = Objects.requireNonNull(psiElement).getText();
        if (context.contains(String.format(STRING_S, name))) {
            return null;
        }
        LightMethodBuilder methodBuilder = new LightMethodBuilder(psiClass.getManager(), JavaLanguage.INSTANCE, name);
        methodBuilder.addModifier(PsiModifier.PUBLIC);
        methodBuilder.setContainingClass(psiClass);
        methodBuilder.setNavigationElement(psiField);
        methodBuilder.setMethodReturnType("java.lang.String");

        return methodBuilder;
    }

    /**
     * 字符串首字母大写
     */
    protected String upperCase(@Nullable String str) {
        if (str == null) {
            return null;
        }
        String suffix;
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        bytes[0] = (byte) (bytes[0] - 32);
        suffix = new String(bytes);
        return suffix;
    }

}