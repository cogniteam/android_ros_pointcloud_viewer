package org.ros.android.view.visualization;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import com.google.common.collect.Lists;

import org.ros.android.android_15.R;
import org.ros.android.view.visualization.layer.CogniPointCloud2DLayer;
import org.ros.android.view.visualization.layer.Layer;
import org.ros.namespace.GraphName;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;

/**
 * A view for PointClouds.
 */
public class PointCloudView extends RelativeLayout {
	public final static int NO_CONTROL = 0x0000;
	public final static int GESTURES_CONTROL_ONLY = 0x0001;
	public final static int BUTTONS_CONTROL_ONLY = 0x0002;
	public final static int BUTTONS_AND_GESTURES = 0x0003;

	VisualizationView visualizationView;
	CogniPointCloud2DLayer cogniPointCloud2DLayer;

	//Keep a view to steal gestures/touch events from the visualization view, when needed.
	View splashView;
	Slider slider;
	SimpleJoystick joystick;

	public PointCloudView(Context context) {
		super(context);
	}

	public PointCloudView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setGesturesControl(true);
		setButtonsControl(true);
	}

	/**
	 * Initiates all components.
	 * To be called from the onCreate method of the parent.
	 */
	public void onCreate(Context context, String topic, String frame) {
		onCreate(context, GraphName.of(topic), GraphName.of(frame));
	}

	/**
	 * Initiates all components.
	 * To be called from the onCreate method of the parent.
	 */
	public void onCreate(Context context, GraphName topic, GraphName frame) {
		LayoutInflater.from(context).inflate(R.layout.point_cloud_view, this, true);

		splashView = findViewById(R.id.splash_view);
		//set a touch listener to "steal" gestures from underneath views.
		splashView.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
			}
		});

		visualizationView = (VisualizationView) findViewById(R.id.pcd_visualization_view);
		visualizationView.getCamera().setFrame(frame);
		cogniPointCloud2DLayer = new CogniPointCloud2DLayer(context, topic, frame);
		visualizationView.onCreate(Lists.<Layer>newArrayList(
				cogniPointCloud2DLayer));


		slider = (Slider) findViewById(R.id.slider);
		joystick = (SimpleJoystick) findViewById(R.id.simple_joystick);

		final CogniPointCloud2DLayer.PointCloudController camControl = cogniPointCloud2DLayer.getCameraController();
		//set the controllers for the layer
		slider.addListener(new Slider.SliderListener() {
			@Override
			public void onNewPosition(float value) {
				//move the camera forward when the slider value is "up"
				camControl.setCameraSpeed(0, 0, value);
			}
		});

		joystick.addListener(new SimpleJoystick.SimpleJoystickListener() {
			@Override
			public void onNewPosition(float x, float y) {
				//move the camera left/right and up/down.
				camControl.setCameraSpeed(x, y, 0);
			}
		});
	}

	/**
	 * Must be called in {@link org.ros.android.RosActivity#init(org.ros.node.NodeMainExecutor)}
	 *
	 * @param nodeMainExecutor
	 */
	public void init(NodeMainExecutor nodeMainExecutor) {
		visualizationView.init(nodeMainExecutor);
	}

	/**
	 * @return The NodeMain of the PCD view, which is the visualization view.
	 */
	public NodeMain getNodeMain() {
		return visualizationView;
	}

	/**
	 * Sets the control mode for the pointcloud - either GESTURES_CONTROL_ONLY, or BUTTONS_CONTROL_ONLY
	 *
	 * @param gesturesControl
	 */
	public void setControlMode(int gesturesControl) {
		switch (gesturesControl) {
			case NO_CONTROL:
				setGesturesControl(false);
				setButtonsControl(false);
				break;
			case BUTTONS_CONTROL_ONLY:
				setGesturesControl(false);
				setButtonsControl(true);
				break;
			case GESTURES_CONTROL_ONLY:
				setGesturesControl(true);
				setButtonsControl(false);
				break;
			case BUTTONS_AND_GESTURES:
				setGesturesControl(true);
				setButtonsControl(true);
				break;
		}
	}

	/**
	 * Sets the control mode to buttons.
	 */
	private void setButtonsControl(final boolean set) {
		post(new Runnable() {
			@Override
			public void run() {
				if (set) {
					slider.setVisibility(VISIBLE);
					slider.setFocusable(true);
					joystick.setVisibility(VISIBLE);
					joystick.setFocusable(true);
				} else {
					slider.setVisibility(INVISIBLE);
					slider.setFocusable(false);
					joystick.setVisibility(INVISIBLE);
					joystick.setFocusable(false);
				}
			}
		});
	}

	/**
	 * Sets the control mode to gestures.
	 */
	private void setGesturesControl(final boolean set) {
		post(new Runnable() {
			@Override
			public void run() {
				if (set) {
					//set the splash view to "disabled", so it won't steal the touch events from the visualization view.
					splashView.setEnabled(false);
				} else {
					//set the splash view to "enabled", so it will steal the touch events from the visualization view.
					splashView.setEnabled(true);
				}
			}
		});
	}
}
