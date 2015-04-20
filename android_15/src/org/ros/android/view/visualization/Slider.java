package org.ros.android.view.visualization;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
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
 * A simple, visual slider
 */
public class Slider extends RelativeLayout {


	ImageView sliderThumb;
	ImageView sliderBoarder;

	ArrayList<SliderListener> listeners;

	public interface SliderListener {
		/**
		 * The function to be called upon update of joystick values.
		 *
		 * @param value value between -1 and 1.
		 */
		public void onNewPosition(float value);
	}

	/**
	 * Adds a listener to the listeners list.
	 *
	 * @return true if the listener was added.
	 */
	public boolean addListener(SliderListener l) {
		if (l == null) return false;

		return !listeners.contains(l) && listeners.add(l);
	}

	/**
	 * Removes a listener.
	 *
	 * @return true if the listener was removed.
	 */
	public boolean removeListener(SliderListener l) {
		if (l == null) return false;

		return listeners.contains(l) && listeners.remove(l);
	}

	private void notifyListeners(float value) {
		for (SliderListener l : listeners) {
			l.onNewPosition(value);
		}
	}

	public Slider(Context context) {
		super(context);
		init(context);
	}

	public Slider(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public Slider(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	/**
	 * Initiates all components.
	 */
	protected void init(Context context) {
		LayoutInflater.from(context).inflate(R.layout.slider, this, true);

		listeners = new ArrayList<SliderListener>();

		sliderThumb = (ImageView) findViewById(R.id.slider_thumb);
		sliderBoarder = (ImageView) findViewById(R.id.slider_boarder);

		//set the boarder to match the size of the thumb
		sliderThumb.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				//now we can retrieve the width and height
				int width = sliderThumb.getWidth();

				sliderBoarder.getLayoutParams().width = width;
				sliderThumb.getViewTreeObserver().removeGlobalOnLayoutListener(this);
			}
		});

		sliderThumb.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getAction();

				final float HALF_HEIGHT = sliderThumb.getHeight() / 2;
				final float BOARDER_TOP = sliderBoarder.getY(); //y value of max slider position
				final float BOARDER_BOTTOM = sliderBoarder.getY() + sliderBoarder.getHeight() - sliderThumb.getHeight();
				final float BOARDER_CENTER = sliderBoarder.getY() + sliderBoarder.getHeight() / 2 - HALF_HEIGHT;

				switch (action) {
					case MotionEvent.ACTION_DOWN:
					case MotionEvent.ACTION_MOVE:
						float yPos = sliderThumb.getY() + event.getY() - HALF_HEIGHT;
						yPos = Math.max(Math.min(yPos, BOARDER_BOTTOM), BOARDER_TOP);
						sliderThumb.setY(yPos);

						float relativePos = (-2) * ((yPos - BOARDER_TOP) / (BOARDER_BOTTOM - BOARDER_TOP) - 0.5f);
						notifyListeners(relativePos);

						break;
					case MotionEvent.ACTION_UP:
						sliderThumb.setY(BOARDER_CENTER);
						notifyListeners(0);
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
