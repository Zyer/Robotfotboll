package com.chalmers.objectrecognition;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.example.objectrecognition.R;
import com.example.objectrecognition.R.id;

public class FilteringActivity extends Activity implements CvCameraViewListener2 {

	private static final String TAG = "OCVSample::Activity";
	private final String BALL_SETTINGS = "ball_settings";
	private final String GOAL_SETTINGS = "goal_settings";
	private CameraBridgeViewBase mOpenCvCameraView;

	/*
	 * Variables used for the color filtering and 
	 * the save functions.
	 */
	private Mat mRgba;
	private Mat mHsv;

	private double[] minVal = { 0, 0, 0 };
	private double[] maxVal = { 255, 255, 255 };

	private Scalar HsvMin = new Scalar(0, 50, 50);
	private Scalar HsvMax = new Scalar(5, 255, 255);

	private SeekBar[] sb = new SeekBar[6];
	
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
				// mOpenCvCameraView.setOnTouchListener(ObjectRecognition.this);
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};

	public FilteringActivity() {
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
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.filter_layout);

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera);
		mOpenCvCameraView.setMaxFrameSize(320, 240);

		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);

		/*
		 * Initiates the seekbar listeners
		 */
		for (int i = 0; i < 6; i++) {
			sb[i] = (SeekBar) findViewById(R.id.seekBar1 + i);
			sb[i].setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {

				}

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress,
						boolean fromUser) {
					TextView text;
					switch (seekBar.getId()) {

					case R.id.seekBar1:
						text = (TextView) findViewById(id.textView0);
						text.setText(Integer.toString(progress) + " Hue");
						minVal[0] = progress;
						break;

					case R.id.seekBar2:
						text = (TextView) findViewById(id.textView1);
						text.setText(Integer.toString(progress) + " Sat");
						minVal[1] = progress;
						break;

					case R.id.seekBar3:
						text = (TextView) findViewById(id.textView2);
						text.setText(Integer.toString(progress) + " Val");
						minVal[2] = progress;
						break;

					case R.id.seekBar4:
						text = (TextView) findViewById(id.textView3);
						text.setText(Integer.toString(progress) + " Hue");
						maxVal[0] = progress;
						break;

					case R.id.seekBar5:
						text = (TextView) findViewById(id.textView4);
						text.setText(Integer.toString(progress) + " Sat");
						maxVal[1] = progress;
						break;

					case R.id.seekBar6:
						text = (TextView) findViewById(id.textView5);
						text.setText(Integer.toString(progress) + " Val");
						maxVal[2] = progress;
						break;
					}
				}
			});
		}
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
		inflater.inflate(R.menu.hsv_menu_activity, menu);

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
		FileOutputStream fos = null;

		switch (item.getItemId()) {
		case R.id.save_ball:
			try {
				fos = openFileOutput(BALL_SETTINGS, Context.MODE_PRIVATE);
				fos.write((byte) ((int) minVal[0]));
				fos.write((byte) ((int) minVal[1]));
				fos.write((byte) ((int) minVal[2]));
				fos.write((byte) ((int) maxVal[0]));
				fos.write((byte) ((int) maxVal[1]));
				fos.write((byte) ((int) maxVal[2]));

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;

		case R.id.save_goal:
			try {
				fos = openFileOutput(GOAL_SETTINGS, Context.MODE_PRIVATE);
				fos.write((byte) ((int) minVal[0]));
				fos.write((byte) ((int) minVal[1]));
				fos.write((byte) ((int) minVal[2]));
				fos.write((byte) ((int) maxVal[0]));
				fos.write((byte) ((int) maxVal[1]));
				fos.write((byte) ((int) maxVal[2]));

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2#onCameraViewStarted(int, int)
	 * 
	 * Initiates RBB image
	 */
	@Override
	public void onCameraViewStarted(int width, int height) {
		mRgba = new Mat(height, width, CvType.CV_8UC4);

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

	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 * 
	 * Disables camera view when the application is closed.
	 */
	public void onDestroy() {
		super.onDestroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();

	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 * 
	 * Enables camera view
	 */
	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
	}

	public Scalar getHsvMin() {
		HsvMin.set(minVal);
		return HsvMin;
	}

	public Scalar getHsvMax() {
		HsvMax.set(maxVal);
		return HsvMax;
	}

	/*
	 * (non-Javadoc)
	 * @see org.opencv.android.CameraBridgeViewBase
	 * .CvCameraViewListener2#onCameraFrame(org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame)
	 * 
	 * Converts from RGB to HSV then converts to binary image that is shown on the screen.
	 */
	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS,
				new Size(4, 4));
		mRgba = inputFrame.rgba();
		mHsv = new Mat(mRgba.size(), CvType.CV_8UC3);
		Imgproc.cvtColor(mRgba, mHsv, Imgproc.COLOR_BGR2HSV_FULL, 3);
		Core.inRange(mHsv, getHsvMin(), getHsvMax(), mHsv);
		Imgproc.erode(mHsv, mHsv, element);
		Imgproc.dilate(mHsv, mHsv, element);

		return mHsv;
	}

}
