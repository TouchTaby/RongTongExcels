package utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * ： on 2018-10-23.
 * ：630646654@qq.com
 */
public class LogUtil {

    //这里DEBUG的作用是，可以在程序完成后设置DEBUG的值为false，程序以后就不会在显示以前的打印信息
    public static boolean DEBUG = true;

    static String className;//文件名
    static String methodName;//方法名
    static int lineNumber;//行数

    //   static String threadName  = Thread.currentThread().getName();//线程名字，能判断主线程还是子线程

    private static void getMethodNames(StackTraceElement[] sElements) {
        className = sElements[2].getFileName();
        methodName = sElements[2].getMethodName();
        lineNumber = sElements[2].getLineNumber();
    }

    /**
     * 获取当前文件的基本信息
     */
    private static StringBuffer getFileInfo() {
        getMethodNames(new Throwable().getStackTrace());

        //获取获取包含文件名、方法名、行数的对象数据
        StringBuffer buffer = new StringBuffer();
        buffer.append(methodName);
        buffer.append("(").append(className).append(":").append(lineNumber).append(")");
        return buffer;
    }

    //各种Log打印
    public static void e(Object head,Object o) {
        if (DEBUG) {
            StringBuffer buffer = getFileInfo();
            Log.e("TAG", buffer +"--"+ head.toString()+"：------ " + o.toString());
        }
    }


    public static void e(int i) {
        if (DEBUG) {
            StringBuffer buffer = getFileInfo();
            Log.e("TAG", buffer + "打印：------      " + i);
        }
    }

    public static void e(float i) {
        if (DEBUG) {
            StringBuffer buffer = getFileInfo();
            Log.e("TAG", buffer + "打印：------      " + i);
        }
    }

    public static void e(boolean b) {
        if (DEBUG) {
            StringBuffer buffer = getFileInfo();
            Log.e("TAG", buffer + "打印：------      " + b);
        }
    }

    //各种土司
    public static void ts(Context context, Object object) {
        if (DEBUG) {
            Toast.makeText(context, object + "", Toast.LENGTH_SHORT).show();
        }
    }

    public static void tsl(Context context, Object object) {
        if (DEBUG)
            Toast.makeText(context, object + "", Toast.LENGTH_LONG).show();
    }


}
