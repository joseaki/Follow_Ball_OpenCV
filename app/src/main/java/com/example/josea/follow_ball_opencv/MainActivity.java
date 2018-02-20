package com.example.josea.follow_ball_opencv;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{
    JavaCameraView javaCameraView;
    Mat mRgba, imgGray,imgHSV, imgCanny, mask, res, kernel,circles,hsv;
    int height=240;
    int width=320;
    double center_percentage=0.07;
    int mid=(int)(width/2);
    //blue mask
    Scalar lower_blue=new Scalar(90, 100, 80);
    Scalar upper_blue=new Scalar(150, 255, 250);
    //calculate the borders of the frame
    double center_left=mid-(width*center_percentage);
    double center_right=mid+(width*center_percentage);
    double left_left=0;
    double left_right=center_left;
    double right_left=center_right;
    double right_right=width;
    //init cant_frames
    int cant_frames=0;
    String[] labels={"izquierda","centro","derecha"};
    int left,center,right=0;
    int i_label,distance2target=0;

    BaseLoaderCallback mLoaderCallBack=new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case BaseLoaderCallback.SUCCESS:{
                    javaCameraView.enableView();
                    break;
                }
                default:{
                    super.onManagerConnected(status);
                    break;
                }
            }
            super.onManagerConnected(status);
        }
    };
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    public static String TAG = "MainActivity";
    static{
        if(OpenCVLoader.initDebug()){
            Log.i(TAG,"opencv loaded");
        }else{
            Log.i(TAG,"Opencv not loaded");
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        javaCameraView=(JavaCameraView)findViewById(R.id.javaCameraView);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.setMaxFrameSize(width,height);
        // Example of a call to a native method
        //TextView tv = (TextView) findViewById(R.id.sample_text);
        //tv.setText(stringFromJNI());
    }
    @Override
    protected  void onPause(){
        super.onPause();
        if(javaCameraView!=null)
            javaCameraView.disableView();
    }
     @Override
     protected void onDestroy(){
        super.onDestroy();
        if(javaCameraView!=null)
             javaCameraView.disableView();
     }
     @Override
     protected  void onResume(){
         super.onResume();
         if(OpenCVLoader.initDebug()){
             Log.i(TAG,"opencv loaded");
             mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
         }else{
             Log.i(TAG,"Opencv not loaded");
             OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION,this,mLoaderCallBack);
         }
     }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba=new Mat(height,width, CvType.CV_8UC4);
        imgHSV=new Mat(height,width, CvType.CV_8UC4);
        mask=new Mat(height,width,CvType.CV_8SC1);
        kernel=new Mat(35,35, CvType.CV_8UC1,Scalar.all(1));
        res=new Mat(height,width, CvType.CV_8UC4);
        imgGray=new Mat(height,width, CvType.CV_8UC1);
        imgCanny=new Mat(height,width, CvType.CV_8UC1);
        circles=new Mat();
        hsv=new Mat(height,width, CvType.CV_8UC1);
        Log.i(TAG,"inicio");


    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba.release();
        mRgba=inputFrame.rgba().clone();
        Imgproc.cvtColor(mRgba,imgHSV,Imgproc.COLOR_RGB2HSV);
        //make mask
        Core.inRange(imgHSV,lower_blue,upper_blue,mask);

        //mask smoth
        Size size= new Size(35,35);
        Imgproc.GaussianBlur(mask,mask,size,0);
        Imgproc.threshold(mask,mask,0,255,Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);
        //closing mask
        Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_CLOSE,kernel);
        //merge frame with mask
        Core.bitwise_and(mRgba,mRgba,res,mask);
        //blur just gray image

        Core.extractChannel(res,hsv,1);

        Imgproc.medianBlur(hsv,res,5);
        //get circles
        Imgproc.HoughCircles(res,circles,Imgproc.HOUGH_GRADIENT,1,30,50,20,0,0);
        //draw the circles
        double[] circle_mean={0,0,0};
        if(circles!=null){
            for (int x = 0; x < circles.cols(); x++){
                double vCircle[] = circles.get(0,x);
                circle_mean[0]+=vCircle[0];
                circle_mean[1]+=vCircle[1];
                circle_mean[2]+=vCircle[2];
            }
            circle_mean[0]=circle_mean[0]/circles.cols();
            circle_mean[1]=circle_mean[1]/circles.cols();
            circle_mean[2]=circle_mean[2]/circles.cols();
            Point center_c=new Point(circle_mean[0],circle_mean[1]);
            Imgproc.circle(mRgba,center_c, ((int)circle_mean[2]),new Scalar(0,255,0),3);
            Imgproc.circle(mRgba,center_c, 2,new Scalar(0,100,255),3);
            //calculate if it's left, center, right
            double x_circle=circle_mean[0];
            if(cant_frames<5){
                cant_frames+=1;
                if(x_circle>left_left && x_circle<left_right)
                    left+=1;
                else if(x_circle>center_left && x_circle<center_right)
                    center+=1;
                else if(x_circle>right_left && x_circle<right_right)
                    right+=1;
            }else{
                int max=left;
                i_label=0;
                if(center>max){
                    max=center;
                    i_label=1;
                }
                if(right>max){
                    max=right;
                    i_label=2;
                }
                //pixels to distance
                distance2target= (int) (1107.3*(Math.pow(circle_mean[2],-1.13)));
                cant_frames=0;
                left=0;center=0;right=0;
            }
        }
        Imgproc.putText(mRgba,"Direccion: "+labels[i_label],new Point(20,20),Core.FONT_HERSHEY_DUPLEX,0.35,new Scalar(0,255,255),1);
        Imgproc.putText(mRgba,"Distancia: "+distance2target,new Point(20,60),Core.FONT_HERSHEY_DUPLEX,0.35,new Scalar(0,255,255),1);
        //
        //
        //
        //Imgproc.cvtColor(mRgba,imgGray,Imgproc.COLOR_RGB2GRAY);
        //Imgproc.Canny(imgGray,imgCanny,50,150);
        /*
        imgHSV.release();
        mask.release();
        res.release();
        kernel.release();
        circles.release();
        System.gc();
        */

        return mRgba;
    }
}
