package org.ros.android.view.visualization;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
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
	public final static int GESTURES_CONTROL = 0x0001;
	public final static int BUTTONS_CONTROL = 0x0002;

	VisualizationView visualizationView;
//	ImageView joystickThumb;
//	ImageView joystickBoarder;
//	int joystickBoarderCenterX;
//	int joystickBoarderCenterY;

	//Keep a view to steal gestures/touch events from the visualization view, when needed.
	View splashView;
	Slider slider;
	SimpleJoystick joystick;

	public PointCloudView(Context context) {
		super(context);
	}

	public PointCloudView(Context context, AttributeSet attrs) {
		super(context, attrs);
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
		visualizationView.onCreate(Lists.<Layer>newArrayList(
				new CogniPointCloud2DLayer(context, topic, frame)));



		slider = (Slider) findViewById(R.id.slider);
		joystick = (SimpleJoystick) findViewById(R.id.simple_joystick);
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
	 * Sets the control mode for the pointcloud - either GESTURES_CONTROL, or BUTTONS_CONTROL
	 *
	 * @param gesturesControl
	 */
	public void setControlMode(int gesturesControl) {
		switch (gesturesControl) {
			case GESTURES_CONTROL:
				setGesturesControl();
				break;
			case BUTTONS_CONTROL:
				setButtonsControl();
				break;
		}
	}

	/**
	 * sets the control mode to buttons.
	 */
	private void setButtonsControl() {
		post(new Runnable() {
			@Override
			public void run() {
				//set the splash view to "enabled", so it will steal the touch events from the visualization view.
				splashView.setEnabled(true);

				slider.setVisibility(VISIBLE);
				slider.setFocusable(true);
				joystick.setVisibility(VISIBLE);
				joystick.setFocusable(true);
			}
		});
	}

	/**
	 * sets the control mode to gestures.
	 */
	private void setGesturesControl() {
		post(new Runnable() {
			@Override
			public void run() {
				//set the splash view to "disabled", so it won't steal the touch events from the visualization view.
				splashView.setEnabled(false);

				slider.setVisibility(INVISIBLE);
				slider.setFocusable(false);
				joystick.setVisibility(INVISIBLE);
				joystick.setFocusable(false);
			}
		});
	}
}
