/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.view.visualization.layer;

import android.content.Context;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.support.v4.view.GestureDetectorCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

import com.google.common.base.Preconditions;

import org.jboss.netty.buffer.ChannelBuffer;
import org.ros.android.view.visualization.RotateGestureDetector;
import org.ros.android.view.visualization.Vertices;
import org.ros.android.view.visualization.VisualizationView;
import org.ros.android.view.visualization.gl_utils.ModelMatrix;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;
import org.ros.rosjava_geometry.Vector3;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;

import sensor_msgs.PointCloud2;
import sensor_msgs.PointField;

/**
 * A {@link SubscriberLayer} that visualizes
 * sensor_msgs/PointCloud2 messages in 2D.
 * <p/>
 * Also, handles gestures.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public class CogniPointCloud2DLayer extends SubscriberLayer<PointCloud2> implements TfLayer {
	private GraphName frame;

	private static final int BACKGRUND_COLOR = 0x377dfaFF;
	private static final float POINT_SIZE = 10.f;

	private final static float MAX_INTENSITY = 3700f;
	private final static float MIN_INTENSITY = 0f;

	//keep a mutex for reading/writing to buffers.
	private final Object mutex;

	private GraphName frameToStick;
	private FloatBuffer vertexFrontBuffer;
	private FloatBuffer colorsFrontBuffer;
	private FloatBuffer vertexBackBuffer;
	private FloatBuffer colorsBackBuffer;

	private Vector3 pointCloudCenterOfGravity; //For rotation around the object.

	private ModelMatrix cameraModel;
	private ModelMatrix pcdModel;
	private PointCloudController pcdController;
	private GesturesController gesturesController;

	private static final float MAX_SPEED_PER_FRAME = 0.05f;

	private ArrayList<PcdDrawListener> drawListeners;
	private interface PcdDrawListener {
		public void onPcdDraw();
	}
	/**
	 * A class used to control movements according to touch events.
	 */
	public class GesturesController {
		private final static float scaleMovementFactor = 1f; //translation between scaling to movement in z axis

		//The following are not final because they depend on screen dpi.
		private float translateGestureFactor = 0.1f; //translation between translate gesture to rotating
		private float translateMultiGestureFactor = 0.002f; //translation between translate gesture to rotating

		private GestureDetectorCompat translateGestureDetector;
		private GestureDetectorCompat translateMultiGestureDetector;
		private RotateGestureDetector rotateGestureDetector;
		private ScaleGestureDetector zoomGestureDetector;

		//Count max pointers for each gesture, so multi-touch will remain so until the end of gesture.
		private int currentGestureMaxPointers = 0;

		public GesturesController(final View view) {
			//set factors according to screen density, to keep behaviours on different devices.
			final DisplayMetrics displayMetrics = new DisplayMetrics();
			WindowManager windowManager = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
			windowManager.getDefaultDisplay().getMetrics(displayMetrics);
			float density = displayMetrics.density;
			translateGestureFactor *= (density / 3f);

			//Add gesture listeners
			view.post(new Runnable() {
				@Override
				public void run() {
					translateGestureDetector =
							new GestureDetectorCompat(view.getContext(), new GestureDetector.SimpleOnGestureListener() {
								@Override
								public boolean onDown(MotionEvent e) {
									// This must return true in order for onScroll() to trigger.
									return true;
								}

								@Override
								public boolean onScroll(MotionEvent event1, MotionEvent event2, final float distanceX, final float distanceY) {
									GesturesController.this.onGestureTranslate(-distanceX, distanceY);
									return true;
								}

								@Override
								public boolean onDoubleTap(final MotionEvent e) {
									GesturesController.this.onGestureDoubleTap(e.getX(), e.getY());
									return true;
								}
							});

					translateMultiGestureDetector =
							new GestureDetectorCompat(view.getContext(), new GestureDetector.SimpleOnGestureListener() {
								@Override
								public boolean onDown(MotionEvent e) {
									// This must return true in order for onScroll() to trigger.
									return true;
								}

								@Override
								public boolean onScroll(MotionEvent event1, MotionEvent event2, final float distanceX, final float distanceY) {
									GesturesController.this.onMultiGestureTranslate(-distanceX, distanceY);
									return true;
								}

								@Override
								public boolean onDoubleTap(final MotionEvent e) {
									GesturesController.this.onGestureDoubleTap(e.getX(), e.getY());
									return true;
								}
							});

					rotateGestureDetector =
							new RotateGestureDetector(new RotateGestureDetector.OnRotateGestureListener() {
								@Override
								public boolean onRotate(MotionEvent event1, MotionEvent event2, final double deltaAngle) {
									final float focusX = (event1.getX(0) + event1.getX(1)) / 2;
									final float focusY = (event1.getY(0) + event1.getY(1)) / 2;
									GesturesController.this.onGestureRotate(focusX, focusY, (float) deltaAngle);
									return true;
								}
							});

					zoomGestureDetector =
							new ScaleGestureDetector(view.getContext(),
									new ScaleGestureDetector.SimpleOnScaleGestureListener() {
										@Override
										public boolean onScale(ScaleGestureDetector detector) {
											if (!detector.isInProgress()) {
												return false;
											}
											final float focusX = detector.getFocusX();
											final float focusY = detector.getFocusY();
											final float factor = detector.getScaleFactor();
											GesturesController.this.onGestureZoom(focusX, focusY, factor);
											return true;
										}
									});

				}
			});
		}

		public boolean onTouchEvent(View view, MotionEvent event) {
			if (translateGestureDetector == null || rotateGestureDetector == null || zoomGestureDetector == null || translateMultiGestureDetector == null) {
				return false;
			}
			if (currentGestureMaxPointers < event.getPointerCount())
				currentGestureMaxPointers = event.getPointerCount();

			if (isEndOfGesture(event)) {
				//The last finger was lifted - the gesture was finished.
				currentGestureMaxPointers = 0;
			}

			if (event.getPointerCount() == 1 && currentGestureMaxPointers <= 1) {
				final boolean translateGestureHandled = translateGestureDetector.onTouchEvent(event);
				return translateGestureHandled;
			} else { //multi touch
				final boolean rotateGestureHandled = rotateGestureDetector.onTouchEvent(event);
				final boolean zoomGestureHandled = zoomGestureDetector.onTouchEvent(event);
				final boolean translateMultiGestureHandled = translateMultiGestureDetector.onTouchEvent(event);
				return translateMultiGestureHandled || rotateGestureHandled || zoomGestureHandled;
			}
		}


		private boolean isEndOfGesture(MotionEvent event) {
			return event.getPointerCount() == 1 && event.getAction() == MotionEvent.ACTION_UP;
		}

		private void onGestureDoubleTap(float x, float y) {
			pcdController.resetView();
		}

		private void onGestureZoom(float focusX, float focusY, float factor) {
			//Move on z axis of the camera.
			float movement = -(scaleMovementFactor * (1 - factor));
			pcdController.translateCameraOnAxes(0, 0, movement);
		}

		private void onMultiGestureTranslate(float x, float y) {
			x *= translateMultiGestureFactor;
			y *= -translateMultiGestureFactor; //opengl is bottom-up axis, and screen is top-down axis

			pcdController.translateCameraOnAxes(x, y, 0);
		}

		private void onGestureTranslate(float x, float y) {
			x *= translateGestureFactor;
			y *= translateGestureFactor;

			//x movement is rotation on y axis, and vice versa
//			cameraController.rotateOnCameraX(y);
//			cameraController.rotateOnCameraY(x);

//			pcdController.rotateOnCameraX(y);
			pcdController.rotatePcdInPlaceOnCameraAxisX(y, cameraModel);
			pcdController.rotateOnCameraY(x);
		}


		private void onGestureRotate(float focusX, float focusY, float deltaAngle) {
			deltaAngle = (float) Math.toDegrees(deltaAngle);
			pcdController.rotateOnCameraZ(deltaAngle);
		}
	}

	/**
	 * A class used to control movements of a pointcloud layer, such as camera and pcd translations.
	 */
	public class PointCloudController {
		Vector3 origin;
		float maxSpeed;
		Vector3 cameraSpeed;

		/**
		 * Creates a new controller. The step size is used when moving with steps.
		 */
		public PointCloudController(float maxSpeed) {
			this.maxSpeed = maxSpeed;
			origin = new Vector3(0,0,0);
			cameraSpeed = new Vector3(0,0,0);

			//add speed factor on each drawing of frame.
			addDrawListener(new PcdDrawListener() {
				@Override
				public void onPcdDraw() {
					cameraModel.translate(cameraSpeed);
				}
			});
		}

		/**
		 * Sets the origin for the world.
		 */
		public void setOrigin(float x,float y,float z){
			setOrigin(new Vector3(x,y,z));
		}

		/**
		 * Sets the origin for the world.
		 */
		public void setOrigin(Vector3 origin){
			this.origin=origin;
		}

		/**
		 * Moves the camera on it's axes.
		 */
		public void translateCameraOnAxes(Vector3 delta) {
			cameraModel.translate(delta);
		}

		/**
		 * Moves the camera on it's axes.
		 */
		public void translateCameraOnAxes(float dx, float dy, float dz) {
			translateCameraOnAxes(new Vector3(dx, dy, dz));
		}

		/**
		 * Moves the camera on it's axes on each frame, by fraction of maximum speed.
		 */
		public void setCameraSpeed(float xSteps, float ySteps, float zSteps) {
			setCameraSpeed(new Vector3(xSteps, ySteps, zSteps));
		}

		/**
		 * Moves the camera on it's axes on each frame, by fraction of maximum speed.
		 */
		public void setCameraSpeed(Vector3 steps) {
			float dx = (float)steps.getX() * maxSpeed;
			float dy = (float)steps.getY() * maxSpeed;
			float dz = (float)steps.getZ() * maxSpeed;

			//x axis is inverted.
			//TODO: CHECK WHY IS IT SO.
			cameraSpeed = new Vector3(-dx, dy, dz);
		}


		/**
		 * Rotates the camera on its x axis.
		 * @param delta: the angle in degrees.
		 */
		public void rotateOnCameraX(float delta) {
			cameraModel.rotateX(delta);
		}

		/**
		 * Rotates the camera on its y axis.
		 * @param delta: the angle in degrees.
		 */
		public void rotateOnCameraY(float delta) {
			cameraModel.rotateY(delta);
		}

		/**
		 * Rotates the camera on its z axis.
		 * @param delta: the angle in degrees.
		 */
		public void rotateOnCameraZ(float delta) {
			cameraModel.rotateZ(delta);
		}

		/**
		 * Rotates The Pcd on the camera's
		 * @param delta: Angle in degrees
		 * @param model: The model to rotate around.
		 */
		public void rotatePcdInPlaceOnCameraAxisX(float delta, ModelMatrix model){
			model.rotateOnModelX(delta, model);
		}
		/**
		 * Moves the camera and PCD to the origin.
		 */
		public void resetView() {
			cameraModel.setIdentity();
			pcdModel.setIdentity();
			pcdModel.translate(origin);
		}
	}

	public CogniPointCloud2DLayer(Context context, String topicName, String frameToStick) {
		this(context, GraphName.of(topicName), GraphName.of(frameToStick));
	}

	public CogniPointCloud2DLayer(Context context, GraphName topicName, GraphName frameToStick) {
		super(topicName, PointCloud2._TYPE);
		this.frameToStick = frameToStick;
		mutex = new Object();

		drawListeners = new ArrayList<PcdDrawListener>();

		cameraModel = new ModelMatrix();
//		cameraModel.rotateZ(90);
		cameraModel.translate(0,0,-10);

		pcdModel = new ModelMatrix();
		pcdController = new PointCloudController(MAX_SPEED_PER_FRAME);

		pointCloudCenterOfGravity = new Vector3(0, 0, 0);

	}

	@Override
	public void onSurfaceChanged(VisualizationView view, GL10 gl, int width, int height) {
		//set the new projection
		gl.glViewport(0, 0, width, height);
		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();
		GLU.gluPerspective(gl, 45.0f, (float) width / (float) height, 0.1f, 100.0f);
		gl.glMatrixMode(GL10.GL_MODELVIEW);
	}

	/**
	 * Adds a listener to the listeners list.
	 *
	 * @return true if the listener was added.
	 */
	public boolean addDrawListener(PcdDrawListener listener) {
		if (listener == null) return false;

		return !drawListeners.contains(listener) && drawListeners.add(listener);
	}

	/**
	 * Removes a listener.
	 *
	 * @return true if the listener was removed.
	 */
	public boolean removeDrawListener(PcdDrawListener listener) {
		if (listener == null) return false;

		return drawListeners.contains(listener) && drawListeners.remove(listener);
	}

	int counter =0;
	@Override
	public void draw(VisualizationView view, GL10 gl) {

		if (null != vertexFrontBuffer) {
			synchronized (mutex) {
				notifyDrawListeners();

				counter++;
				ModelMatrix cam = new ModelMatrix( setCamera(gl) );
				ModelMatrix inv_cam = cam.getInvertedMat();

				ModelMatrix rot = new ModelMatrix();
//				rot.rotate(2 * counter, 1, 0, 0);

				ModelMatrix mv = cam.mult(pcdModel);
				ModelMatrix inv_mv = mv.getInvertedMat();

				ModelMatrix translate_in_cam = mv.calTranslation();

				ModelMatrix trt =
						   translate_in_cam.getInvertedMat()
				    .mult(rot)
					.mult(translate_in_cam)
				;

				ModelMatrix trt_pcd = inv_mv.mult(trt).mult(mv);

				ModelMatrix pcdModel_updated =
						   cam
					.mult(pcdModel)
					.mult( trt_pcd )

				;

				//draw the pcd.
				Vertices.drawPointsWithColors(gl, vertexFrontBuffer, colorsFrontBuffer, POINT_SIZE, pcdModel_updated.getMat() );
			}
		}
	}

	private void notifyDrawListeners() {
		if(drawListeners == null) return;

		for(PcdDrawListener listener: drawListeners){
			listener.onPcdDraw();
		}
	}

	/**
	 * Sets the camera: moves according to the camera position, and set the lookAt point according to it's Z axis.
	 */
	private float[] setCamera(GL10 gl) {
		gl.glMatrixMode(GL10.GL_MODELVIEW);     //Select The Modelview Matrix
		gl.glLoadIdentity();
		Vector3 lookAtPoint = cameraModel.getAxisZNormalized();/*Z axis of the "lookAt" is the place we should look*/
		//add the movement of the camera, to get relative point and not absolute.
		lookAtPoint = lookAtPoint.add(cameraModel.getPosition());
//		GLU.gluLookAt(gl, cameraModel.getX(), cameraModel.getY(), cameraModel.getZ(), /* look from camera XYZ */
//				(float) lookAtPoint.getX(), (float) lookAtPoint.getY(), (float) lookAtPoint.getZ(),
//				(float) cameraModel.getAxisYNormalized().getX(), (float) cameraModel.getAxisYNormalized().getY(), (float) cameraModel.getAxisYNormalized().getZ()); /* positive Y up vector */

		float[] scratch = new float[16];
			Matrix.setLookAtM(scratch, 0, cameraModel.getX(), cameraModel.getY(), cameraModel.getZ(), /* look from camera XYZ */
					(float) lookAtPoint.getX(), (float) lookAtPoint.getY(), (float) lookAtPoint.getZ(),
					(float) cameraModel.getAxisYNormalized().getX(), (float) cameraModel.getAxisYNormalized().getY(), (float) cameraModel.getAxisYNormalized().getZ());
		return scratch;

//		GLU.gluLookAt(gl, 0, 0, 10, /* look from camera XYZ */
//				(float) 0, 0, 0,
//				(float) -1, (float) 0, (float) 0); /* positive Y up vector */

	}

	@Override
	public boolean onTouchEvent(VisualizationView view, MotionEvent event) {
		return gesturesController.onTouchEvent(view, event) || super.onTouchEvent(view, event);
	}

	@Override
	public void onStart(VisualizationView view, ConnectedNode connectedNode) {
		super.onStart(view, connectedNode);
		Subscriber<PointCloud2> subscriber = getSubscriber();
		subscriber.addMessageListener(new MessageListener<PointCloud2>() {
			@Override
			public void onNewMessage(PointCloud2 pointCloud) {
				//Keep the PCD's frame for any case.
				frame = GraphName.of(pointCloud.getHeader().getFrameId());
				updateVertexBuffer(pointCloud);
				pcdController.setOrigin(pointCloudCenterOfGravity);
			}
		});


		//Now that the Visualization view is created, we can set the gesture controller.
		gesturesController = new GesturesController(view);
	}


	private void updateVertexBuffer(final PointCloud2 pointCloud) {
		// We expect an unordered, XYZ point cloud of 32-bit floats (i.e. the result of
		// pcl::toROSMsg()).
		Preconditions.checkArgument(pointCloud.getHeight() == 1);
		Preconditions.checkArgument(pointCloud.getFields().get(0).getDatatype() == PointField.FLOAT32);
		Preconditions.checkArgument(pointCloud.getFields().get(1).getDatatype() == PointField.FLOAT32);
		Preconditions.checkArgument(pointCloud.getFields().get(2).getDatatype() == PointField.FLOAT32);

		final int numOfPoints = (pointCloud.getRowStep() / pointCloud.getPointStep());
		final int vertexSize = numOfPoints * 3 /* x, y, z*/;
		if (vertexBackBuffer == null || vertexBackBuffer.capacity() < vertexSize) {
			vertexBackBuffer = Vertices.allocateBuffer(vertexSize);
		}
		vertexBackBuffer.clear();

		final int colorSize = numOfPoints * 4 /* r,g,b,a */;
		if (colorsBackBuffer == null || colorsBackBuffer.capacity() < colorSize) {
			colorsBackBuffer = Vertices.allocateBuffer(colorSize);
		}

		Vector3 centerOfGravity = new Vector3(0, 0, 0);
		final ChannelBuffer buffer = pointCloud.getData();
		long startTime = System.currentTimeMillis();
		Log.i("PointCloud", "Starting PointCloud processing...");

		while (buffer.readable()) {
			int pointBegin = buffer.readerIndex();

			float x = buffer.readFloat();
			vertexBackBuffer.put(x);

			float y = buffer.readFloat();
			vertexBackBuffer.put(y);

			float z = buffer.readFloat();
			vertexBackBuffer.put(z);

			//add the relative part of this point to the center of gravity
			centerOfGravity = centerOfGravity.add(new Vector3(x/numOfPoints, y/numOfPoints, z/numOfPoints));


			//discard index
			buffer.readFloat();

			// intensities
			float intensity = buffer.readFloat();
			intensity = (intensity - MIN_INTENSITY) / (MAX_INTENSITY - MIN_INTENSITY);
			intensity = Math.min(Math.max(intensity, 0f), 1f);
			colorsBackBuffer.put(intensity); //r
			colorsBackBuffer.put(intensity); //g
			colorsBackBuffer.put(intensity); //b
			colorsBackBuffer.put(1f); //a

			//discard leftovers
			int totalRead = buffer.readerIndex() - pointBegin;
			buffer.readBytes(pointCloud.getPointStep() - totalRead);
		}
		long endTime   = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		Log.i("Cogni", "Time PointCloud processing took: " + totalTime + "ms");

		vertexBackBuffer.position(0);
		colorsBackBuffer.position(0);

		this.pointCloudCenterOfGravity = centerOfGravity;

		synchronized (mutex) {
			FloatBuffer tmpVertice = vertexFrontBuffer;
			vertexFrontBuffer = vertexBackBuffer;
			vertexBackBuffer = tmpVertice;

			FloatBuffer tmpColors = colorsFrontBuffer;
			colorsFrontBuffer = colorsBackBuffer;
			colorsBackBuffer = tmpColors;
		}
	}

	/**
	 * @return The camera controller for this view.
	 */
	public PointCloudController getCameraController() {
		return pcdController;
	}

	@Override
	public GraphName getFrame() {
		return frameToStick;
	}
}
