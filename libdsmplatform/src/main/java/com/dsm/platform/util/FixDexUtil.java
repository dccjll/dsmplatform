package com.dsm.platform.util;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import com.dsm.platform.util.log.LogUtil;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashSet;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by SJL on 2016/11/6.
 */
class FixDexUtil {

    private static final String tag = FixDexUtil.class.getSimpleName();
    private static final String DEX_DIR = "odex";
    public static String DEX_DIR_SD = Environment.getExternalStorageDirectory() + "/secondlock/fd/";

    private static final HashSet<File> loadedDex = new HashSet<>();

    static {
        loadedDex.clear();
    }

    /**
     * 加载指定前缀的dex分包
     * @param context
     * @param prefix
     */
    public static void loadFixDex(Context context, String prefix) {
        // 获取到系统的odex 目录
        File fileDir = context.getDir(DEX_DIR, Context.MODE_PRIVATE);
        File[] listFiles = fileDir.listFiles();

        for (File file : listFiles) {
            if (file.getName().endsWith(".dex")&&(!TextUtils.isEmpty(prefix))&&file.getName().startsWith(prefix)) {
                // 存储该目录下的.dex文件（补丁）
                loadedDex.add(file);
            }
        }

        doDexInject(context, fileDir);
    }

    public static void loadFixDex(Context context) {
        // 获取到系统的odex 目录
        File fileDir = context.getDir(DEX_DIR, Context.MODE_PRIVATE);
        File[] listFiles = fileDir.listFiles();

        for (File file : listFiles) {
            if (file.getName().endsWith(".dex")) {
                // 存储该目录下的.dex文件（补丁）
                loadedDex.add(file);
            }
        }

        doDexInject(context, fileDir);
    }

    private static void doDexInject(Context context, File fileDir) {
        // .dex 的加载需要一个临时目录
        String optimizeDir = fileDir.getAbsolutePath() + File.separator + "opt_dex";
        File fopt = new File(optimizeDir);
        if (!fopt.exists())
            fopt.mkdirs();
        // 根据.dex 文件创建对应的DexClassLoader 类
        for (File file : loadedDex) {
            DexClassLoader classLoader = new DexClassLoader(file.getAbsolutePath(), fopt.getAbsolutePath(), null, context.getClassLoader());
            //注入
            inject(classLoader, context);
        }
    }

    private static void inject(DexClassLoader classLoader, Context context) {
        // 获取到系统的DexClassLoader 类
        PathClassLoader pathLoader = (PathClassLoader) context.getClassLoader();
        try {
            // 分别获取到补丁的dexElements和系统的dexElements
            Object dexElements = combineArray(getDexElements(getPathList(classLoader)),
                    getDexElements(getPathList(pathLoader)));
            // 获取到系统的pathList 对象
            Object pathList = getPathList(pathLoader);
            // 设置系统的dexElements 的值
            setField(pathList, pathList.getClass(), "dexElements", dexElements);

            LogUtil.i(tag,"inject"+getPathList(pathLoader).toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 通过反射设置字段值
     */
    private static void setField(Object obj, Class<?> cl, String field, Object value)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {

        Field localField = cl.getDeclaredField(field);
        localField.setAccessible(true);
        localField.set(obj, value);
    }

    /**
     * 通过反射获取 BaseDexClassLoader中的PathList对象
     */
    private static Object getPathList(Object baseDexClassLoader)
            throws IllegalArgumentException, NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        return getField(baseDexClassLoader, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
    }

    /**
     * 通过反射获取指定字段的值
     */
    private static Object getField(Object obj, Class<?> cl, String field)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field localField = cl.getDeclaredField(field);
        localField.setAccessible(true);
        return localField.get(obj);
    }

    /**
     * 通过反射获取DexPathList中dexElements
     */
    private static Object getDexElements(Object paramObject)
            throws IllegalArgumentException, NoSuchFieldException, IllegalAccessException {
        return getField(paramObject, paramObject.getClass(), "dexElements");
    }

    /**
     * 合并两个数组
     *
     * @param arrayLhs
     * @param arrayRhs
     * @return
     */
    private static Object combineArray(Object arrayLhs, Object arrayRhs) {
        Class<?> localClass = arrayLhs.getClass().getComponentType();
        int i = Array.getLength(arrayLhs);
        int j = i + Array.getLength(arrayRhs);
        Object result = Array.newInstance(localClass, j);
        for (int k = 0; k < j; ++k) {
            if (k < i) {
                Array.set(result, k, Array.get(arrayLhs, k));
            } else {
                Array.set(result, k, Array.get(arrayRhs, k - i));
            }
        }
        return result;
    }
}
