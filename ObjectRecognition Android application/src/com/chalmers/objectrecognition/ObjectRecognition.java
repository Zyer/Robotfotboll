package com.chalmers.objectrecognition;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.example.objectrecognition.R;

public class ObjectRecognition extends Activity implements
		CvCameraViewListener2 {

	/*
	 * The following variable is for object recognition.
	 */
	private static final String TAG = "ObjectRecognition::Activity";
	private final String BALL_SETTINGS = "ball_settings";
	private final String GOAL_SETTINGS = "goal_settings";
	private CameraBridgeViewBase mOpenCvCameraView;
	
	/*
	 * This variable is for determining the required area size 
	 * of the goal to allow shooting. This is set in pixels.
	 */
	private static final int GOAL_SHOOT_THRESHOLD = 7500;
	
	/*
	 * Communication bytes used to determining the position
	 * of the object.
	 */
	private static final byte BIT_LOCATE_OBJECT = 0x03;
	private static final byte BIT_OBJECT_CENTER = 0x04;
	private static final byte BIT_OBJECT_CENTER_LEFT = 0x05;
	private static final byte BIT_OBJECT_CENTER_RIGHT = 0x06;
	private static final byte BIT_OBJECT_RIGHT = 0x08;
	private static final byte BIT_OBJECT_LEFT = 0x07;
	private static final byte BIT_SHOOT_BALL = 0x0A;
	private static final byte OBJECT_BALL_GRAB = 0x09;
	private static final byte BIT_REVERSE = 0x0B;
	
	//Communication bytes received from the Arduino
	private static final byte OBJECT_BALL = 0x01;
	private static final byte OBJECT_GOAL = 0x02;
	
	/*
	 * Speed bytes.
	 */
	@SuppressWarnings("unused")
	private static final byte SPEED_FULL = 0x01;
	private static final byte SPEED_HALF = 0x02;
	private static final byte SPEED_QUARTER = 0x04;
	@SuppressWarnings("unused")
	private static final byte SPEED_EIGHT = 0x08;
	@SuppressWarnings("unused")
	private static final byte SPEED_SIXTEENTH = 0x16;
	
	/*
	 * Used to track current object. 
	 */
	private byte current_object = 0x7f;

	//Set to false if automatic start is not desired.
	//If false, requires to press "start" in menu.
	private boolean start = true;

	/*
	 * Variables used for the color filtering and 
	 * the load functions.
	 */
	private Mat mRgba;
	private Mat mHsv;
	
	private Point cameraCenter;
	private Point contour_center;
	private int centerLeftY;
	private int centerRightY;
	private int leftY;
	private int rightY;
	
	private double[] minVal = { 0, 0, 0 };
	private double[] maxVal = { 255, 255, 255 };

	private Scalar HsvMin = new Scalar(0, 50, 50);
	private Scalar HsvMax = new Scalar(5, 255, 255);
	private Scalar contour_color = new Scalar(255,255,255,0);

	private List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
	private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();
	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		/*
		 * This method initialize the OpenCV manager and enables the camera
		 */
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");
				mOpenCvCameraView.enableView();
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};
	
	/*
	 * The following variable is for communication with the Arduino over USB
	 */
	private PendingIntent mPermissionIntent;
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	private Boolean mPermissionRequestPending;
	
	private UsbManager mUsbManager;
	private UsbAccessory mAccessory;
	private ParcelFileDescriptor mFileDescriptor;
	private FileInputStream mInputStream;
	private FileOutputStream mOutputStream;
	
	private static final byte COMMAND_TEXT = 0xf;
	private static final byte TARGET_DEFAULT = 0xf;
	private byte[] send_Value = {0x0,0x0};
	
	/*
	 * Establishes the USB connection
	 */
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = (UsbAccessory) intent
							.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, "Permission denied for Accessory:"
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = (UsbAccessory) intent
						.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};
	
	/*
	 * This runnable send and receives data.
	 */
	Runnable commRunnable = new Runnable() {

		@Override
		public void run() {
			int ret = 0;
			byte[] buffer = new byte[3];
			while (ret >= 0) {
				try {
					ret = mInputStream.read(buffer);
				} catch (IOException E) {
					break;
				}
				switch (buffer[0]) {
				case COMMAND_TEXT:
					if(start){
						if ((buffer[2] == OBJECT_BALL) && (current_object != OBJECT_BALL)) {
							loadObject(BALL_SETTINGS);
							current_object = OBJECT_BALL;
						}
						else if ((buffer[2] == OBJECT_GOAL) && (current_object != OBJECT_GOAL)) {
							loadObject(GOAL_SETTINGS);
							current_object = OBJECT_GOAL;
						}
						
						sendByte(COMMAND_TEXT, TARGET_DEFAULT,send_Value);
					}
					break;

				default:
					Log.d(TAG, "Unknown Message: " + buffer[0]);
					break;
				}
			}
		}
	};
	
	public ObjectRecognition() {
		Log.i(TAG, "Instantiated new " + this.getClass());

	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 * 
	 * This method is executed when the this activity starts.
	 * Initiating the camera view, USB and inflating XML
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		
		//USB-CALLS
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		//END USB-CALLS
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_object_recognition);

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera);
		mOpenCvCameraView.setMaxFrameSize(320, 240);

		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);

	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 * 
	 * Inflates the options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_menu, menu);

		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 * 
	 * Executed upon clicking a menu item. Contains logic for menu item.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
		switch (item.getItemId()) {

		case R.id.change_view:
			Intent intent = new Intent(ObjectRecognition.this,
					FilteringActivity.class);
			startActivity(intent);
			break;

		case R.id.load_ball:
			loadObject(BALL_SETTINGS);
			break;

		case R.id.load_goal:
			loadObject(GOAL_SETTINGS);
			break;
			
		case R.id.start_search:
			start = true;
			break;
		}
		return false;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2#onCameraViewStarted(int, int)
	 * 
	 * Initializes segmentation variables upon camera view start.
	 */
	@Override
	public void onCameraViewStarted(int width, int height) {
		mRgba = new Mat(height, width, CvType.CV_8UC4);
		leftY = (height/4)-25;
		centerLeftY = (height/2)-20;
		centerRightY = (height/2)+20;
		rightY = (height/4)*3+25;
		
		cameraCenter = new Point((width/2)+40,height/2);
		
	}

	/*
	 * (non-Javadoc)
	 * @see org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2#onCameraViewStopped()
	 * 
	 * Clears the RGB-view upon camera shutdown.
	 */
	@Override
	public void onCameraViewStopped() {
		mRgba.release();
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 * 
	 * Disables camera view and USB-connection when the application is paused.
	 */
	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		closeAccessory();

	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 * 
	 * Closes USB connection and disables camera view when the application is
	 * closed.
	 */
	public void onDestroy() {
		super.onDestroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		unregisterReceiver(mUsbReceiver);

	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 * 
	 * Enables camera view and USB-connection upon resuming the application.
	 */
	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
		
		
		if ((mInputStream != null) && (mOutputStream != null))
			return;
		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.opencv.android.CameraBridgeViewBase
	 * .CvCameraViewListener2#onCameraFrame(org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame)
	 * 
	 * Called when receiving a frame from the camera view. This method deals with color
	 * conversion, filtering as well as detecting the position of the filtered object. 
	 */
	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(4, 4));
		mRgba = inputFrame.rgba();
		mHsv = new Mat(mRgba.size(), CvType.CV_8UC3);
		double isCenterInContour = -1;

		/*
		 * Converts the RGB view into HSV.
		 */
		Imgproc.cvtColor(mRgba, mHsv, Imgproc.COLOR_BGR2HSV_FULL, 3);

		/*
		 * Filters the image and converts into binary representation.
		 */
		Core.inRange(mHsv, getHsvMin(), getHsvMax(), mHsv);
		
		/*
		 * Makes the binary image clearer.
		 */
		Imgproc.erode(mHsv, mHsv, element);
		Imgproc.dilate(mHsv, mHsv, element);
		
		MatOfPoint contour = null;
		MatOfPoint temp;
		
		/*
		 * Clears the contour lists.
		 */
		contours.clear();
		mContours.clear();
		/*
		 * Finds all contours in the foreground of the binary image.
		 */
		Imgproc.findContours(mHsv, contours, new Mat(), Imgproc.RETR_LIST,
				Imgproc.CHAIN_APPROX_NONE);
		
		/*
		 * Finds the biggest contour of the ones found in the previous step.
		 */
		Iterator<MatOfPoint> it = contours.iterator();
		while(it.hasNext()){
			temp = it.next();
			if (contour == null)
				contour = temp;
			else if (Imgproc.contourArea(temp) > Imgproc.contourArea(contour))
				contour = temp;
		}
		
		
		/*
		 * If a contour exists, color the outline of the contour and mark the center.
		 */
		if (contour != null) {
			mContours.add(contour);
			/*
			 * Tests if the center line of the camera view is inside the contour.
			 */
			isCenterInContour = Imgproc.pointPolygonTest(new MatOfPoint2f(contour.toArray()), cameraCenter, false);
			Imgproc.drawContours(mRgba, mContours, -1, contour_color, 5);
			List<Moments> mu = new ArrayList<Moments>(mContours.size());
		    for (int i = 0; i < mContours.size(); i++) {
		        mu.add(i, Imgproc.moments(mContours.get(i), false));
		        Moments p = mu.get(i);
		        int x = (int) (p.get_m10() / p.get_m00());
		        int y = (int) (p.get_m01() / p.get_m00());
		        
		        contour_center = new Point(x, y);
		        Core.circle(mRgba, contour_center, 4, new Scalar(255,49,0,255));
		        
		    }
		}
		/*
		 * Given the correct conditions, send the correct bytes via USB-connection
		 * to the Arduino.
		 */
	    if (!mContours.isEmpty()) {
	    	if(current_object == OBJECT_GOAL && Imgproc.contourArea(contour) >= GOAL_SHOOT_THRESHOLD && isCenterInContour == 1){
	    		send_Value[0] = BIT_SHOOT_BALL;
	    	}
	    	else{
			    if((contour_center.y < centerRightY) && (contour_center.y > centerLeftY)){
					if(contour_center.x > 50)
						send_Value[0] = BIT_OBJECT_CENTER;
					else
						send_Value[0] = OBJECT_BALL_GRAB;
					send_Value[1] = SPEED_HALF;
					
			    }else {
			    	if(contour_center.y > centerRightY) {
			    		if (contour_center.y < rightY) {
			    			if (contour_center.x > 35)
			    				send_Value[0] = BIT_OBJECT_CENTER_RIGHT;
			    			else
			    				send_Value[0] = BIT_REVERSE;
			    				
			    			send_Value[1] = SPEED_QUARTER;
			    		}
			    		else {
			    			send_Value[0] = BIT_OBJECT_RIGHT;
			    			send_Value[1] = SPEED_HALF;
			    		}
			    	}
			    	else{
			    		if (contour_center.y > leftY) {
			    			if (contour_center.x > 35)
			    				send_Value[0] = BIT_OBJECT_CENTER_LEFT;
			    			else
			    				send_Value[0] = BIT_REVERSE;
			    			send_Value[1] = SPEED_QUARTER;
			    		}
			    		else {
			    			send_Value[0] = BIT_OBJECT_LEFT;
			    			send_Value[1] = SPEED_HALF;
			    		}
			    	}
			    }
	    	}
	    } else {
			send_Value[0] = BIT_LOCATE_OBJECT;
			send_Value[1] = SPEED_HALF;
	    }
	    	
	    Log.d(TAG, "Sending "+send_Value[0]);
		return mRgba;
	}
	
	
	/*
	 * Used to get the minimum limits of the HSV-filter
	 */
	private Scalar getHsvMin() {
		HsvMin.set(minVal);
		return HsvMin;
	}

	/*
	 * Used to get the maximum limits of the HSV-filter
	 */
	private Scalar getHsvMax() {
		HsvMax.set(maxVal);
		return HsvMax;
	}
	
	/*
	 * Used to initiate input and output streams as well as
	 * opening the USB accessory.
	 */
	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			Thread thread = new Thread(null, commRunnable, TAG);
			thread.start();
			Log.d(TAG, "Accessory Opened");
		} else {
			Log.d(TAG, "Accessory Open failed");
		}
	}

	/*
	 * Closes the USB accessory.
	 */
	private void closeAccessory() {
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException E) {

		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
	}

	/*
	 * Method used to send bytes via the USB-connection.
	 */
	public void sendByte(byte command, byte target, byte[] value) {
		byte[] buffer = new byte[4];
		buffer[0] = command;
		buffer[1] = target;
		buffer[2] = value[0];
		buffer[3] = value[1];
		if (mOutputStream != null) {
			try {
				mOutputStream.write(buffer);
			} catch (Exception e) {
				Log.d(TAG, "Write Failed", e);
			}
		}
	}
	
	/*
	 * Loads the minimum and maximum values of the HSV-filter
	 * of the given object.
	 */
	public void loadObject(String file){
		FileInputStream fis = null;
		try {
			fis = openFileInput(file);
			for (int i = 0; i < 6; i++) {
				if (i < 3) {
					minVal[i] = fis.read();
				} else {
					maxVal[i % 3] = fis.read();
				}
			}

			fis.close();
		} catch (Exception e) {
			Log.d(TAG, "Read Failed", e);
		}
	}
	
}
