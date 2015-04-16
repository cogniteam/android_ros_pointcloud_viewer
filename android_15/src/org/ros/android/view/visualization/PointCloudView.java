package org.ros.android.view.visualization;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
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

	VisualizationView visualizationView;

	ImageView seekBarThumb;
	ImageView joystickThumb;
	ImageView seekBarBoarder;

	RelativeLayout joystickHolder;

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

		visualizationView = (VisualizationView) findViewById(R.id.pcd_visualization_view);
		visualizationView.getCamera().setFrame(frame);
		visualizationView.onCreate(Lists.<Layer>newArrayList(
				new CogniPointCloud2DLayer(context, topic, frame)));



		seekBarThumb = (ImageView) findViewById(R.id.seek_bar_thumb);
		seekBarBoarder = (ImageView) findViewById(R.id.seek_bar_boarder);
		joystickThumb = (ImageView) findViewById(R.id.joystick_pan);
		joystickHolder = (RelativeLayout) findViewById(R.id.joystick_holder);


		//set the boarder to match the size of the thumb
		seekBarThumb.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				//now we can retrieve the width and height
				int width = seekBarThumb.getWidth();

				seekBarBoarder.getLayoutParams().width = width;
				seekBarThumb.getViewTreeObserver().removeGlobalOnLayoutListener(this);
			}
		});

		seekBarThumb.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getAction();
				switch (action) {
					case MotionEvent.ACTION_DOWN:
//						seekBarThumb.setY(event.getY()/*+seekBarThumb.getY()*/);
						Log.i("SPAM" , "SPAM: DOWN: y = " + seekBarThumb.getY() + " , event y " +event.getY());
						seekBarThumb.setY(event.getY());
						break;
					case MotionEvent.ACTION_MOVE:
						Log.i("SPAM" , "SPAM: MOVE: y = " + seekBarThumb.getY() + " , event y " +event.getY());
						Log.i("SPAM" , "SPAM: Pivot: " + seekBarThumb.getPivotY());
						Log.i("SPAM" , "SPAM: minus: " + (event.getY()-seekBarThumb.getPivotY()));

						seekBarThumb.setY(event.getY()-seekBarThumb.getPivotY());
						break;
					case MotionEvent.ACTION_UP:
						Log.i("SPAM" , "SPAM: UP: y = " + seekBarThumb.getY() + " , event y " +event.getY());
						seekBarThumb.setY(0);
						break;
					case MotionEvent.ACTION_CANCEL:
						Log.i("SPAM" , "SPAM: CANCEL: y = " + seekBarThumb.getY() + " , event y " +event.getY());
						break;
					default:
						break;
				}
				return true;
			}
		});
		joystickThumb.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				Log.i("SPAM" , "SPAM: x = " + joystickThumb.getX() + " , event x " +event.getX());
				int action = event.getAction();
				switch (action) {
					case MotionEvent.ACTION_DOWN:
						joystickThumb.setX(event.getX());
						joystickThumb.setY(event.getY());
						break;
					case MotionEvent.ACTION_MOVE:
						break;
					case MotionEvent.ACTION_UP:
						break;
					case MotionEvent.ACTION_CANCEL:
						break;
					default:
						break;
				}
				return true;
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
}
