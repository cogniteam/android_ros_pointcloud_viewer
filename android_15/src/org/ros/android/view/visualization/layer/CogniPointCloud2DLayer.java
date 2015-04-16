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
import android.view.WindowManager;

import com.google.common.base.Preconditions;

import org.jboss.netty.buffer.ChannelBuffer;
import org.ros.android.view.visualization.RotateGestureDetector;
import org.ros.android.view.visualization.Vertices;
import org.ros.android.view.visualization.VisualizationView;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;
import org.ros.rosjava_geometry.Vector3;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import sensor_msgs.PointCloud2;
import sensor_msgs.PointField;

/**
 * A {@link SubscriberLayer} that visualizes
 * sensor_msgs/PointCloud2 messages in 2D.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public class CogniPointCloud2DLayer extends SubscriberLayer<PointCloud2> implements TfLayer {
	/**
	 * A class used for holding model data for opengl 3D objects.
	 */
	public class ModelMatrix {
		private float mModel[] = new float[16];

		public ModelMatrix() {
			Matrix.setIdentityM(mModel, 0);
		}

		public ModelMatrix clone(){
			ModelMatrix answer = new ModelMatrix();
			for(int i=0; i<mModel.length; i++){
				answer.mModel[i] = this.mModel[i];
			}
			return answer;
		}

		public void translate(float dx, float dy, float dz) {
			Matrix.translateM(this.mModel, 0, dx, dy, dz);
		}

		public void translate(Vector3 v) {
			Matrix.translateM(this.mModel, 0, (float) v.getX(), (float) v.getY(), (float) v.getZ());
		}

		//angle in degrees
		public void rotateX(float angle) {
			Matrix.rotateM(this.mModel, 0, angle, 1.0f, 0, 0);
		}

		//angle in degrees
		public void rotateY(float angle) {
			Matrix.rotateM(this.mModel, 0, angle, 0, 1.0f, 0);
		}

		//angle in degrees
		public void rotateZ(float angle) {
			Matrix.rotateM(this.mModel, 0, angle, 0, 0, 1.0f);
		}

		//Rotate the object around self's position. Angle in degrees
		public void rotateXAroundSelfPosition(float angle) {
			Vector3 position = getPosition();
			translate(position.scale(-1));
			rotateX(angle);
			translate(position);
		}

		//Rotate the object around self's position. Angle in degrees
		public void rotateYAroundSelfPosition(float angle) {
			Vector3 position = getPosition();
			translate(position.scale(-1));
			rotateY(angle);
			translate(position);
		}

		//Rotate the object around self's position. Angle in degrees
		public void rotateZAroundSelfPosition(float angle) {
			Vector3 position = getPosition();
			translate(position.scale(-1));
			rotateZ(angle);
			translate(position);
		}

		//Scales the object
		public void scale(float amount) {
			Matrix.scaleM(this.mModel, 0, amount, amount, amount);
		}

		/**
		 * @return The X position of this model.
		 */
		public float getX() {
			return mModel[12];
		}

		/**
		 * @return The Y position of this model.
		 */
		public float getY() {
			return mModel[13];
		}

		/**
		 * @return The Z position of this model.
		 */
		public float getZ() {
			return mModel[14];
		}

		/**
		 * @return The position of this model.
		 */
		public Vector3 getPosition() {
			return new Vector3(getX(), getY(), getZ());
		}

		/**
		 * @return A 3D point representing the X axis of this model.
		 */
		public Vector3 getAxisX() {
			return new Vector3(mModel[0], mModel[1], mModel[2]);
		}

		/**
		 * @return A 3D point representing the Y axis of this model.
		 */
		public Vector3 getAxisY() {
			return new Vector3(mModel[4], mModel[5], mModel[6]);
		}

		/**
		 * @return A 3D point representing the Z axis of this model.
		 */
		public Vector3 getAxisZ() {
			return new Vector3(mModel[8], mModel[9], mModel[10]);
		}

		/**
		 * @return A 3D point representing the X axis of this model.
		 * The axis is normalized.
		 */
		public Vector3 getAxisXNormalized() {
			return (new Vector3(mModel[0], mModel[1], mModel[2])).scale(1.f / getScaling());
		}

		/**
		 * @return A 3D point representing the Y axis of this model.
		 * The axis is normalized.
		 */
		public Vector3 getAxisYNormalized() {
			return (new Vector3(mModel[4], mModel[5], mModel[6])).scale(1.f / getScaling());
		}

		/**
		 * @return A 3D point representing the Z axis of this model.
		 * The axis is normalized.
		 */
		public Vector3 getAxisZNormalized() {
			return (new Vector3(mModel[8], mModel[9], mModel[10])).scale(1.f / getScaling());
		}

		public float getScaling() {
			return (float) Math.sqrt(Math.pow(mModel[0], 2) + Math.pow(mModel[4], 2) + Math.pow(mModel[8], 2));
		}

		public final float[] getMat() {
			return mModel;
		}
	}

	private static final int BACKGRUND_COLOR = 0x377dfaFF;
	private static final float POINT_SIZE = 10.f;

	private final Object mutex;

	private GraphName frame;
	//use frameToStick for drawing the pcd without moving it on robot's movement.
	private GraphName frameToStick;
	private FloatBuffer vertexFrontBuffer;
	private FloatBuffer colorsFrontBuffer;
	private FloatBuffer vertexBackBuffer;
	private FloatBuffer colorsBackBuffer;

	private GestureDetectorCompat translateGestureDetector;
	private GestureDetectorCompat translateMultiGestureDetector;
	private RotateGestureDetector rotateGestureDetector;
	private ScaleGestureDetector zoomGestureDetector;


	private ModelMatrix cameraModel;

	private final float CAMERA_Z = -5f;
	private float translateGestureFactor = 0.1f; //translation between translate gesture to rotating
	private float translateMultiGestureFactor = 0.002f; //translation between translate gesture to rotating
	private final float scaleMovementFactor = 1f; //translation between scaling to movement in z axis


	private final static float MAX_INTENSITY = 3700f;
	private final static float MIN_INTENSITY = 0f;

	public CogniPointCloud2DLayer(Context context, String topicName, String frameToStick) {
		this(context, GraphName.of(topicName), GraphName.of(frameToStick));
	}

	public CogniPointCloud2DLayer(Context context, GraphName topicName, GraphName frameToStick) {
		super(topicName, PointCloud2._TYPE);
		this.frameToStick = frameToStick;
		mutex = new Object();

		cameraModel = new ModelMatrix();
		cameraModel.translate(0, 0, CAMERA_Z);

		//set factors according to screen density, to keep behaviours on different devices.
		final DisplayMetrics displayMetrics = new DisplayMetrics();
		WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getMetrics(displayMetrics);
		float density = displayMetrics.density;
		translateGestureFactor *= (density / 3f);

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

	@Override
	public void draw(VisualizationView view, GL10 gl) {
		if (null != vertexFrontBuffer) {
			synchronized (mutex) {
				setCamera(gl);
				//draw the pcd.
				Vertices.drawPointsWithColors(gl, vertexFrontBuffer, colorsFrontBuffer, POINT_SIZE);
			}
		}
	}

	/**
	 * Sets the camera: moves according to the camera position, and set the lookAt point according to it's Z axis.
	 */
	private void setCamera(GL10 gl) {
		gl.glMatrixMode(GL10.GL_MODELVIEW);     //Select The Modelview Matrix
		gl.glLoadIdentity();
		Vector3 lookAtPoint = cameraModel.getAxisZNormalized();/*Z axis of the "lookAt" is the place we should look*/
		//add the movement of the camera, to get relative point and not absolute.
		lookAtPoint = lookAtPoint.add(cameraModel.getPosition());
		GLU.gluLookAt(gl, cameraModel.getX(), cameraModel.getY(), cameraModel.getZ(), /* look from camera XYZ */
				(float)lookAtPoint.getX(), (float)lookAtPoint.getY(), (float)lookAtPoint.getZ(),
				(float) cameraModel.getAxisYNormalized().getX(), (float) cameraModel.getAxisYNormalized().getY(), (float) cameraModel.getAxisYNormalized().getZ()); /* positive Y up vector */

	}

	@Override
	public boolean onTouchEvent(VisualizationView view, MotionEvent event) {
		if (translateGestureDetector == null || rotateGestureDetector == null || zoomGestureDetector == null || translateMultiGestureDetector == null) {
			return false;
		}
		if (event.getPointerCount() == 1) {
			final boolean translateGestureHandled = translateGestureDetector.onTouchEvent(event);
			return translateGestureHandled || super.onTouchEvent(view, event);
		} else { //multi touch
			final boolean rotateGestureHandled = rotateGestureDetector.onTouchEvent(event);
			final boolean zoomGestureHandled = zoomGestureDetector.onTouchEvent(event);
			final boolean translateMultiGestureHandled = translateMultiGestureDetector.onTouchEvent(event);
			return translateMultiGestureHandled || rotateGestureHandled || zoomGestureHandled || super.onTouchEvent(view, event);
		}
	}

	@Override
	public void onStart(VisualizationView view, ConnectedNode connectedNode) {
		super.onStart(view, connectedNode);
		Subscriber<PointCloud2> subscriber = getSubscriber();
		subscriber.addMessageListener(new MessageListener<PointCloud2>() {
			@Override
			public void onNewMessage(PointCloud2 pointCloud) {
				Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: got new PCD!");
				//Keep the PCD's frame for any case.
				frame = GraphName.of(pointCloud.getHeader().getFrameId());
				updateVertexBuffer(pointCloud);
			}
		});


		//Add gesture listeners
		final VisualizationView finalView = view;
		finalView.post(new Runnable() {
			@Override
			public void run() {
				translateGestureDetector =
						new GestureDetectorCompat(finalView.getContext(), new GestureDetector.SimpleOnGestureListener() {
							@Override
							public boolean onDown(MotionEvent e) {
								// This must return true in order for onScroll() to trigger.
								return true;
							}

							@Override
							public boolean onScroll(MotionEvent event1, MotionEvent event2, final float distanceX, final float distanceY) {
								CogniPointCloud2DLayer.this.onGestureTranslate(-distanceX, distanceY);
								return true;
							}

							@Override
							public boolean onDoubleTap(final MotionEvent e) {
								CogniPointCloud2DLayer.this.onGestureDoubleTap(e.getX(), e.getY());
								return true;
							}
						});

				translateMultiGestureDetector =
						new GestureDetectorCompat(finalView.getContext(), new GestureDetector.SimpleOnGestureListener() {
							@Override
							public boolean onDown(MotionEvent e) {
								// This must return true in order for onScroll() to trigger.
								return true;
							}

							@Override
							public boolean onScroll(MotionEvent event1, MotionEvent event2, final float distanceX, final float distanceY) {
								CogniPointCloud2DLayer.this.onMultiGestureTranslate(-distanceX, distanceY);
								return true;
							}

							@Override
							public boolean onDoubleTap(final MotionEvent e) {
								CogniPointCloud2DLayer.this.onGestureDoubleTap(e.getX(), e.getY());
								return true;
							}
						});

				rotateGestureDetector =
						new RotateGestureDetector(new RotateGestureDetector.OnRotateGestureListener() {
							@Override
							public boolean onRotate(MotionEvent event1, MotionEvent event2, final double deltaAngle) {
								final float focusX = (event1.getX(0) + event1.getX(1)) / 2;
								final float focusY = (event1.getY(0) + event1.getY(1)) / 2;
								CogniPointCloud2DLayer.this.onGestureRotate(focusX, focusY, (float) deltaAngle);
								return true;
							}
						});

				zoomGestureDetector =
						new ScaleGestureDetector(finalView.getContext(),
								new ScaleGestureDetector.SimpleOnScaleGestureListener() {
									@Override
									public boolean onScale(ScaleGestureDetector detector) {
										if (!detector.isInProgress()) {
											return false;
										}
										final float focusX = detector.getFocusX();
										final float focusY = detector.getFocusY();
										final float factor = detector.getScaleFactor();
										CogniPointCloud2DLayer.this.onGestureZoom(focusX, focusY, factor);
										return true;
									}
								});

			}
		});
	}

	private void onGestureDoubleTap(float x, float y) {
		//TODO: ADD
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: double tap detected! : " + x + ", " + y);
	}

	private void onGestureZoom(float focusX, float focusY, float factor) {
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: zoom detected! " + focusX + " , " + focusY + " , " + factor);

		//Move on z axis of the camera.
		float movement = -(scaleMovementFactor * (1 - factor));
		cameraModel.translate(0, 0, movement);
	}

	private void onMultiGestureTranslate(float x, float y) {
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: Multi translate detected! : " + x + ", " + y);
		x *= translateMultiGestureFactor;
		y *= - translateMultiGestureFactor;

		cameraModel.translate(x,y,0);
	}

	private void onGestureTranslate(float x, float y) {
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: translate detected! : " + x + ", " + y);
		x *= translateGestureFactor;
		y *= translateGestureFactor;

		cameraModel.rotateX(y);
		cameraModel.rotateY(x);
	}


	private void onGestureRotate(float focusX, float focusY, float deltaAngle) {
		deltaAngle = (float) Math.toDegrees(deltaAngle);
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: rotation detected! " + focusX + " , " + focusY + " , " + deltaAngle);

		cameraModel.rotateZ(deltaAngle);
	}

	private void updateVertexBuffer(final PointCloud2 pointCloud) {
		// We expect an unordered, XYZ point cloud of 32-bit floats (i.e. the result of
		// pcl::toROSMsg()).
		// TODO(damonkohler): Make this more generic.
		Preconditions.checkArgument(pointCloud.getHeight() == 1);
		Preconditions.checkArgument(pointCloud.getFields().get(0).getDatatype() == PointField.FLOAT32);
		Preconditions.checkArgument(pointCloud.getFields().get(1).getDatatype() == PointField.FLOAT32);
		Preconditions.checkArgument(pointCloud.getFields().get(2).getDatatype() == PointField.FLOAT32);

		final int vertexSize = (pointCloud.getRowStep() / pointCloud.getPointStep()) * 3 /* x, y, z*/;
		if (vertexBackBuffer == null || vertexBackBuffer.capacity() < vertexSize) {
			vertexBackBuffer = Vertices.allocateBuffer(vertexSize);
		}
		vertexBackBuffer.clear();

		final int colorSize = (pointCloud.getRowStep() / pointCloud.getPointStep()) * 4 /* r,g,b,a */;
		if (colorsBackBuffer == null || colorsBackBuffer.capacity() < colorSize) {
			colorsBackBuffer = Vertices.allocateBuffer(colorSize);
		}

		final ChannelBuffer buffer = pointCloud.getData();
		while (buffer.readable()) {
			vertexBackBuffer.put(buffer.readFloat()); //x
			vertexBackBuffer.put(buffer.readFloat()); //y
			vertexBackBuffer.put(buffer.readFloat()); //z


			// intensities
			float intensity = buffer.readFloat();
			intensity = (intensity - MIN_INTENSITY) / (MAX_INTENSITY - MIN_INTENSITY);
			intensity = Math.min(Math.max(intensity, 0f), 1f);
			colorsBackBuffer.put(intensity); //r
			colorsBackBuffer.put(intensity); //g
			colorsBackBuffer.put(intensity); //b
			colorsBackBuffer.put(1f); //a

			//discard index
			buffer.readFloat();
		}
		vertexBackBuffer.position(0);
		colorsBackBuffer.position(0);

		synchronized (mutex) {
			FloatBuffer tmpVertice = vertexFrontBuffer;
			vertexFrontBuffer = vertexBackBuffer;
			vertexBackBuffer = tmpVertice;

			FloatBuffer tmpColors = colorsFrontBuffer;
			colorsFrontBuffer = colorsBackBuffer;
			colorsBackBuffer = tmpColors;
		}
	}

	@Override
	public GraphName getFrame() {
		return frameToStick;
	}
}
