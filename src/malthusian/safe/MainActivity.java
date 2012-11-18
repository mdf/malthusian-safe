package malthusian.safe;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.VideoView;

public class MainActivity extends Activity implements SurfaceHolder.Callback 
{
	private static Logger logger = Logger.getLogger(MainActivity.class);

	SensorManager sensorManager;
	Sensor lightSensor;

	VideoView videoView;
    MediaRecorder recorder;
    SurfaceHolder holder;
    Camera camera;

	PowerManager.WakeLock wakeLock;  
		   
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); 
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
        wakeLock.acquire();
        		      
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(lightSensorEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
       
        Resources res = getResources();
        triggerLight = Float.parseFloat(res.getString(R.string.light_threshold));

        setContentView(R.layout.blankview);
	}
	
	boolean isRunning = false;
	boolean waitingForDark = true;
	float triggerLight = 2.0f;
	
	public void lightChange(float light)
	{
		logger.error("light level " + light);
		
		if(waitingForDark)
		{
			if(light<triggerLight)
			{
				waitingForDark = false;
			}
			else
			{
				return;
			}
		}		
		else
		{
			if(light>triggerLight)
			{
				if(isRunning)
				{
					return;
				}
				
				isRunning = true;
				
				go();
			}
			else
			{
				if(isRunning)
				{
					isRunning = false;
					waitingForDark = true;
					stop();
				}
			}
		}
	}

	
	public void startRecording()
	{
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
	    int cameraId = 0;
	    
	    for(int i = 0; i < Camera.getNumberOfCameras(); i++)
	    {
	    	Camera.getCameraInfo(i, cameraInfo);
	    	
	    	if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
	    	{
	    		cameraId = i;
	    		break;
	    	}
	    }

        camera = Camera.open(cameraId);
        camera.setDisplayOrientation(90);
        camera.unlock();
        
        recorder = new MediaRecorder();
        recorder.setCamera(camera);
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        
        CamcorderProfile cameraProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        recorder.setProfile(cameraProfile);

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date date = new Date();
		File path = Environment.getExternalStorageDirectory(); 
        recorder.setOutputFile(path + "/malthusian/reactions/" + df.format(date) + ".mp4");
        
        SurfaceView cameraView = (SurfaceView) findViewById(R.id.camerapreview);
        cameraView.setZOrderMediaOverlay(true);
        
        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);		
	}
	
	
	public void stopRecording()
	{
		if(recorder != null)
		{
			try
			{
				recorder.stop();
				recorder.release();
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		
		recorder = null;
	}

	public void surfaceCreated(SurfaceHolder holder)
	{
		recorder.setPreviewDisplay(holder.getSurface());

		try
		{
			recorder.prepare();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		recorder.start();
	}

	public void surfaceDestroyed(SurfaceHolder holder)
	{
		stopRecording();
		camera.stopPreview();
		camera.release();
	}
	
	public void startVideoPlayback()
	{
		File path = Environment.getExternalStorageDirectory(); 

		videoView = (VideoView) findViewById(R.id.surface_view);
		videoView.setVideoPath(path + "/malthusian/video/video.mp4");
		videoView.start();

		videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
		{
			@Override
			public void onCompletion(MediaPlayer mp)
			{
				stop();
			}
		});		
	}
		
	public void stopVideoPlayback()
	{
		videoView.stopPlayback();
	}
	
	public void stop()
	{
		logger.debug("stopping");
		stopVideoPlayback();
		stopRecording();
		setContentView(R.layout.blankview);
	}
	
	public void go()
	{
		logger.debug("running");
		setContentView(R.layout.videoview);
		startVideoPlayback();
		startRecording();
	}


	SensorEventListener lightSensorEventListener = new SensorEventListener()
	{
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy)
		{

		}

		@Override
		public void onSensorChanged(SensorEvent event)
		{
			if(event.sensor.getType()==Sensor.TYPE_LIGHT)
			{
				lightChange(event.values[0]);
			}
		}
	};
	
    
    @Override
    public void onPause()
    {
    	super.onPause();
    	wakeLock.release();
    }
    
	@Override
	protected void onDestroy()
	{
		super.onDestroy();

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(lightSensorEventListener);
	}

	@Override  
	protected void onResume()
	{  
		super.onResume();  
		wakeLock.acquire();  
	}

}
