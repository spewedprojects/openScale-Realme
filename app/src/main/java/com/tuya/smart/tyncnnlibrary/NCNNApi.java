package com.tuya.smart.tyncnnlibrary;

public class NCNNApi {
    public static native void destroy();
    public static native float[] forward(float[] fArr);
    public static native void init();

    static {
        System.loadLibrary("ncnn_api");
    }
}
