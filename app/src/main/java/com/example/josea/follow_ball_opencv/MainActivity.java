package com.example.josea.follow_ball_opencv;

import android.graphics.Camera;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;


import com.kunzisoft.androidclearchroma.ChromaDialog;
import com.kunzisoft.androidclearchroma.IndicatorMode;
import com.kunzisoft.androidclearchroma.colormode.ColorMode;
import com.kunzisoft.androidclearchroma.listener.OnColorChangedListener;
import com.kunzisoft.androidclearchroma.listener.OnColorSelectedListener;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2,OnColorSelectedListener, OnColorChangedListener {
    OpenCVCameraView javaCameraView;

    //
    double[] circle_mean={0,0,0};
    //
    Mat mRgba, imgGray,imgHSV, imgCanny, mask, res, kernel,circles,hsv;
    //camera image size
    int height=320;
    int width=480;
    //center area it's the double of what's give in center_percentage
    double center_percentage=0.07;
    int lateral_width=(int)((width-(width*center_percentage*2))/2);
    int mid=(int)(width/2);
    //color mask
    boolean colorUp=false;
    double[] lower_color= { 90, 128,  60};
    double[] upper_color= {150, 255, 220};
    Scalar lower_blue=new Scalar(lower_color);
    Scalar upper_blue=new Scalar(upper_color);

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
    //speed of wheels
    int distance2center=0;
    static int v_right;
    static int v_left;
    int v_center=512;
    //toggle button
    int view_toggle=0;
    //manual exposure
    boolean man_exposure=false;
    //flash
    boolean flash=false;
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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (savedInstanceState != null) {
            view_toggle=savedInstanceState.getInt("view");
            lower_color= savedInstanceState.getDoubleArray("lc");
            upper_color=savedInstanceState.getDoubleArray("uc");
            lower_blue.set(lower_color);
            upper_blue.set(upper_color);
        }
        setContentView(R.layout.activity_main);

        javaCameraView=(OpenCVCameraView)findViewById(R.id.javaCameraView);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.setMaxFrameSize(width,height);

        ImageView toggle=findViewById(R.id.imageViewToggle);
        toggle.setOnClickListener(view -> {
            if(view_toggle>=2)
                view_toggle=0;
            else
                view_toggle++;
        });
        //button to update the lower and upper colors of the mask
        ImageView menuUpImage= findViewById(R.id.imageViewUp);
        menuUpImage.setOnClickListener(view -> {
            colorUp=true;
            hsv_color_picker_up();
        });
        ImageView menuDownImage= findViewById(R.id.imageViewDown);
        menuDownImage.setOnClickListener(view -> {
            colorUp=false;
            hsv_color_picker_down();
        });
        //button lock the exposure
        ImageView lock= findViewById(R.id.imageViewLock);
        lock.setOnClickListener(view -> {
            man_exposure = !man_exposure;
            javaCameraView.setManualExposure(man_exposure);
        });
        //button turn on/off the flash
        ImageView flashView= findViewById(R.id.imageViewFlash);
        flashView.setOnClickListener(view -> {
            flash = !flash;
            javaCameraView.setLedFlash(flash);
        });
        //Example of a call to a native method
        //TextView tv = (TextView) findViewById(R.id.sample_text);
        //tv.setText(stringFromJNI());
    }
    @Override
    protected  void onPause(){
        super.onPause();
        if(javaCameraView!=null)
            javaCameraView.disableView();
            v_right=0;
            v_left=0;
            new SendPostRequest().execute();
    }
     @Override
     protected void onDestroy(){
        super.onDestroy();
        if(javaCameraView!=null)
             javaCameraView.disableView();
             v_right=0;
             v_left=0;
             new SendPostRequest().execute();
     }
     @Override
     protected  void onResume(){
         super.onResume();
         if(OpenCVLoader.initDebug()){
             Log.i(TAG,"opencv loaded");
             mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
             v_right=0;
             v_left=0;
             new SendPostRequest().execute();
         }else{
             Log.i(TAG,"Opencv not loaded");
             OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION,this,mLoaderCallBack);
         }
     }
    @Override
    protected void onSaveInstanceState(Bundle outState) {

        // Save our own state now
        outState.putDoubleArray("lc",lower_color);
        outState.putDoubleArray("uc",upper_color);
        outState.putInt("view",view_toggle);
        super.onSaveInstanceState(outState);
    }


     private void hsv_color_picker_up(){
         float[] hsv_color={
                 (float)upper_color[0]*2,
                 (float)upper_color[1]/255,
                 (float)upper_color[2]/255};

         new ChromaDialog.Builder()
                 .initialColor(Color.HSVToColor(hsv_color))
                 .colorMode(ColorMode.HSV) // RGB, ARGB, HVS, CMYK, CMYK255, HSL
                 .indicatorMode(IndicatorMode.DECIMAL) //HEX or DECIMAL; Note that (HSV || HSL || CMYK) && IndicatorMode.HEX is a bad idea
                 .create()
                 .show(getSupportFragmentManager(), "ChromaDialog");
     }

     private void hsv_color_picker_down(){
        float[] hsv_color={
                (float)lower_color[0]*2,
                (float)lower_color[1]/255,
                (float)lower_color[2]/255};
        new ChromaDialog.Builder()
                .initialColor(Color.HSVToColor(hsv_color))
                .colorMode(ColorMode.HSV) // RGB, ARGB, HVS, CMYK, CMYK255, HSL
                .indicatorMode(IndicatorMode.DECIMAL) //HEX or DECIMAL; Note that (HSV || HSL || CMYK) && IndicatorMode.HEX is a bad idea
                .create()
                .show(getSupportFragmentManager(), "ChromaDialog");
    }

     private void setLower_color(int color){
         float[] hsv = new float[3];
         Color.colorToHSV(color, hsv);
         lower_color[0]=hsv[0]/2;
         lower_color[1]=hsv[1]*255;
         lower_color[2]=hsv[2]*255;
         lower_blue.set(lower_color);
     }

     private void setUpper_color(int color){
         float[] hsv = new float[3];
         Color.colorToHSV(color, hsv);
         upper_color[0]=hsv[0]/2;
         upper_color[1]=hsv[1]*255;
         upper_color[2]=hsv[2]*255;
         upper_blue.set(upper_color);
     }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */

    @Override
    public void onCameraViewStarted(int width, int height) {
        //set camera properties manually
        //javaCameraView.setManualExposure(true);
        //javaCameraView.setExposure(0);
        //initialize matrix frames
        mRgba=new Mat(height,width, CvType.CV_8UC4);
        imgHSV=new Mat(height,width, CvType.CV_8UC4);
        mask=new Mat(height,width,CvType.CV_8SC1);
        kernel=new Mat(25,25, CvType.CV_8UC1,Scalar.all(1));
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
        Size size= new Size(25,25);
        Imgproc.GaussianBlur(mask,mask,size,0);
        Imgproc.threshold(mask,mask,0,255,Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);
        //closing mask
        //Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_CLOSE,kernel);
        //merge frame with mask
        Core.bitwise_and(mRgba,mRgba,res,mask);
        //blur just saturation layer
        Core.extractChannel(res,hsv,1);
        Imgproc.medianBlur(hsv,res,25);
        //get circles
        Imgproc.HoughCircles(res,circles,Imgproc.HOUGH_GRADIENT,1,30,180,20,0,0);
        //draw the circles
        circle_mean[0]=0;circle_mean[1]=0;circle_mean[2]=0;
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
            Imgproc.circle(mRgba,center_c, ((int)circle_mean[2]),new Scalar(0,255,0),1);
            Imgproc.circle(mRgba,center_c, 2,new Scalar(0,100,255),2);
            //calculate if it's left, center or right
            double x_circle=circle_mean[0];
            if(cant_frames < 1){
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
                    //max=right;
                    i_label=2;
                }
                //pixels to distance
                distance2target= (int) (296.94*(Math.pow(circle_mean[2]*100/width,-1.13)));
                cant_frames=0;
                left=0;center=0;right=0;
            }
            speed();
            new SendPostRequest().execute();
        }
        Imgproc.putText(mRgba,"Direccion: "+labels[i_label],new Point(20,20),Core.FONT_HERSHEY_DUPLEX,0.35,new Scalar(0,255,255),1);
        Imgproc.putText(mRgba,"Distancia: "+distance2target,new Point(20,60),Core.FONT_HERSHEY_DUPLEX,0.35,new Scalar(0,255,255),1);

        if(view_toggle==0)
            return mRgba;
        else if(view_toggle==1)
            return mask;
        else
            return res;
    }

    private void speed(){
        v_center=(int)(6.24*distance2target+137.6);
        v_center=v_center>512?512:v_center;
        v_center=v_center<200?0:v_center;
        switch (labels[i_label]){
            case ("centro"):{
                v_right=v_center;
                v_left=v_center;
                break;
            }
            case("izquierda"):{
                distance2center=(int)(mid-circle_mean[0]);
                int pwm=distance2center*v_center/lateral_width;
                v_right=v_center+pwm;
                v_left=v_center-pwm;
                break;
            }
            case("derecha"):{
                distance2center=(int)(circle_mean[0]-mid);
                int pwm=distance2center*v_center/lateral_width;
                v_right=v_center-pwm;
                v_left=v_center+pwm;
                break;
            }
            default:{
                v_right=512;
                v_left=512;
                break;
            }
        }
    }

    @Override
    public void onColorChanged(int color) {
        if(colorUp){
            setUpper_color(color);
            //Toast.makeText(getApplicationContext(),"Maximo"+upper_color[0]+","+upper_color[1]+","+upper_color[2],Toast.LENGTH_SHORT).show();
        }else{
            //Toast.makeText(getApplicationContext(),"Minimo"+lower_color[0]+","+lower_color[1]+","+lower_color[2],Toast.LENGTH_SHORT).show();
            setLower_color(color);
        }
    }

    @Override
    public void onPositiveButtonClick(int color) {
        if(colorUp){
            setUpper_color(color);
            Toast.makeText(getApplicationContext(),"Set Maximo"+upper_color[0]+","+upper_color[1]+","+upper_color[2],Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(getApplicationContext(),"Set Minimo"+lower_color[0]+","+lower_color[1]+","+lower_color[2],Toast.LENGTH_SHORT).show();
            setLower_color(color);
        }
    }

    @Override
    public void onNegativeButtonClick(int color) {

    }

    public static class SendPostRequest extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                
                URL url = new URL("http://192.168.137.200/body?v_right=" + v_right+"&v_left="+v_left);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null)
                    result.append(inputLine).append("\n");

                in.close();
                connection.disconnect();
                return result.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
