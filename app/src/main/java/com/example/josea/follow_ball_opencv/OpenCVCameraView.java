package com.example.josea.follow_ball_opencv;

/**
 * Created by josea on 2/23/2018.
 */


import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;

public class OpenCVCameraView extends JavaCameraView {
    private static final String TAG = "OpenCvCameraView";

    private boolean disabled;
    int width, height;

    public OpenCVCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean isAutofocusDisabled() {
        synchronized(this) {
            return disabled;
        }
    }

    public void disableAutoFocus() {
        synchronized(this) {
            if (mCamera != null && !disabled) {
                Camera.Parameters params = mCamera.getParameters();
                if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_INFINITY))
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                //params.setExposureCompensation(0);
                mCamera.setParameters(params);
                Camera.Size size = params.getPreviewSize();
                width = size.width;
                height = size.height;
                disabled = true;
            }
        }
    }



    public String zoom(int step) {
        Camera.Parameters parameters = mCamera.getParameters();
        if (!parameters.isZoomSupported())
            return "100%";
        int zoom = parameters.getZoom() + step;
        if (zoom > parameters.getMaxZoom())
            zoom = parameters.getMaxZoom();
        else if (zoom < 0)
            zoom = 0;
        parameters.setZoom(zoom);
        mCamera.setParameters(parameters);
        return parameters.getZoomRatios().get(zoom) + "%";
    }

    public int getMaxZoom() {
        Camera.Parameters parameters = mCamera.getParameters();
        if (!parameters.isZoomSupported())
            return 0;
        return parameters.getMaxZoom();
    }

    public int getMaxExposure() {
        Camera.Parameters parameters = mCamera.getParameters();
        return parameters.getMaxExposureCompensation();
    }

    public int getMinExposure() {
        Camera.Parameters parameters = mCamera.getParameters();
        return parameters.getMinExposureCompensation();
    }

    public String adjustExposure(int amount) {
        Camera.Parameters parameters = mCamera.getParameters();
        int current = parameters.getExposureCompensation();
        int minExposure = parameters.getMinExposureCompensation();
        int maxExposure = parameters.getMaxExposureCompensation();
        current += amount;
        if (current < minExposure)
            current = minExposure;
        else if (current > maxExposure)
            current = maxExposure;
        parameters.setExposureCompensation(current);
        mCamera.setParameters(parameters);
        double exposure = parameters.getExposureCompensationStep() * parameters.getExposureCompensation();
        exposure = (int)(exposure * 100) / 100d; //limit to two decimal places
        return "" + exposure;
    }

    public void setExposure(int amount){
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setExposureCompensation(amount);
        mCamera.setParameters(parameters);
    }

    public void setManualExposure(boolean value){
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setAutoExposureLock(value);
        mCamera.setParameters(parameters);
    }

    public int getCurrentZoom() {
        return mCamera.getParameters().getZoom();
    }

    public int getCurrentExposure() {
        return mCamera.getParameters().getExposureCompensation();
    }
}