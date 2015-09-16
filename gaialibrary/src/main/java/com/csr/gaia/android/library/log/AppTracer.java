package com.csr.gaia.android.library.log;

import java.util.Enumeration;
import java.util.Stack;

/**
 * 设计目标：
 * 1. 当进入某个方法中时，将该方法名放入堆栈中；
 * 2. 当退出某个方法时，将该方法从堆栈中移除；
 * 3. 按照自底向上的顺序，打印输出堆栈中的所有元素；
 * Created by linky on 15-6-30.
 */
public class AppTracer {

    public static final String TAG = "AppTracer";
    private static Stack<String> mTracer = new Stack<>();

    private static boolean isNeeded = false;

    // 不能被继承，只能用来调试之用；
    private AppTracer(){}

    public static void put(String methodName) {
        if(isNeeded)
            mTracer.add(methodName);
    }

    public static void pop() {
        if(!isNeeded) return;

        if(!mTracer.isEmpty()) {
            mTracer.pop();
        } else {
            throw new IllegalArgumentException("The stack is empty");
        }
    }

    public static String getTrace() {
        if(!isNeeded) return "";

        if(!mTracer.isEmpty()) {
            Enumeration<String> elements = mTracer.elements();
            StringBuilder sb = new StringBuilder();
            while (elements.hasMoreElements()) {
                String ele = elements.nextElement();
                sb.append(ele + "->");
            }
            return sb.toString();
        }
        return "";
    }

}
