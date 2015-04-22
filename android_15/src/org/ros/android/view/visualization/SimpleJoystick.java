package org.ros.android.view.visualization;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.ros.android.android_15.R;

import java.util.ArrayList;

/**
 * Created by omri on 20/04/15.
 * A simple joystick with visualization.
 */
public class SimpleJoystick extends RelativeLayout {
	private ImageView boarder;
	private ImageView thumb;

	private int boarderCenterX;
	private int boarderCenterY;

	private ArrayList<SimpleJoystickListener> listeners;

	public interface SimpleJoystickListener {
		/**
		 * The function to be called upon update of joystick values.
		 *
		 * @param x value between -1 and 1.
		 * @param y value between -1 and 1.
		 */
		public void onNewPosition(float x, float y);
	}

	/**
	 * Adds a listener to the listeners list.
	 *
	 * @return true if the listener was added.
	 */
	public boolean addListener(SimpleJoystickListener l) {
		if (l == null) return false;

		return !listeners.contains(l) && listeners.add(l);
	}

	/**
	 * Removes a listener.
	 *
	 * @return true if the listener was removed.
	 */
	public boolean removeListener(SimpleJoystickListener l) {
		if (l == null) return false;

		return listeners.contains(l) && listeners.remove(l);
	}

	private void notifyListeners(float outputX, float outputY) {
		for (SimpleJoystickListener l : listeners) {
			l.onNewPosition(outputX, outputY);
		}
	}

	public SimpleJoystick(Context context) {
		super(context);
		init(context);
	}

	public SimpleJoystick(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public SimpleJoystick(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	/**
	 * Initiates all components.
	 */
	protected void init(Context context) {
		LayoutInflater.from(context).inflate(R.layout.simple_joystick, this, true);

		listeners = new ArrayList<SimpleJoystickListener>();

		thumb = (ImageView) findViewById(R.id.joystick_pan);
		boarder = (ImageView) findViewById(R.id.joystick_boarder);


		thumb = (ImageView) findViewById(R.id.joystick_pan);
		boarder = (ImageView) findViewById(R.id.joystick_boarder);
		//use a listener to always hold the latest size of the boarder.
		boarder.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				//now we can retrieve the width and height
				int height = boarder.getHeight();
				int width = height;
				boarderCenterX = Math.round(boarder.getX() + boarder.getWidth() - width / 2);
				boarderCenterY = Math.round(boarder.getY() + boarder.getHeight() - height / 2);

				//center the position according to the boarder
				thumb.setX(boarderCenterX - thumb.getWidth() / 2);
				thumb.setY(boarderCenterY - thumb.getHeight() / 2);
			}
		});

		thumb.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getAction();
				switch (action) {
					case MotionEvent.ACTION_DOWN:
					case MotionEvent.ACTION_MOVE:
						//move the joystick to the correct spot.
						//basically, the size of the boarder can change - that's why we're not using "final"
						float maxRadius = (boarder.getHeight() - thumb.getHeight()) / 2;

						float newX = thumb.getX() + event.getX();
						float newY = thumb.getY() + event.getY();

						float angle = (float) Math.atan2(newY - boarderCenterY, newX - boarderCenterX);
						float xFactor = (float) Math.cos(angle);
						float yFactor = (float) Math.sin(angle);


						float distanceRadius = (float) Math.hypot(newX - boarderCenterX, newY - boarderCenterY);
						distanceRadius = Math.min(distanceRadius, maxRadius);

						newX = boarderCenterX + (xFactor * distanceRadius);
						newY = boarderCenterY + (yFactor * distanceRadius);


						thumb.setX(newX - thumb.getWidth() / 2);
						thumb.setY(newY - thumb.getHeight() / 2);

						float outputX = xFactor * (distanceRadius / maxRadius);
						float outputY = yFactor * (distanceRadius / maxRadius);

						//invert y value (up here is less, but we want it to be greater)
						outputY *= -1;
						notifyListeners(outputX, outputY);

						break;
					case MotionEvent.ACTION_UP:
						thumb.setX(boarderCenterX - thumb.getWidth() / 2);
						thumb.setY(boarderCenterY - thumb.getHeight() / 2);
						notifyListeners(0, 0);
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

}
