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
import org.ros.android.view.visualization.Color;
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

		public void move(float dx, float dy, float dz) {
			Matrix.translateM(this.mModel, 0, dx, dy, dz);
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
	private RotateGestureDetector rotateGestureDetector;
	private ScaleGestureDetector zoomGestureDetector;

	//Opengl camera, etc.
	private ModelMatrix cameraPositionMat;
	private float[] mProjMatrix = new float[16];


	private ModelMatrix pcdModel;
//	private final float PCD_Z = 10f;
	private final float pcdScale = 100; //initial scale for the pcd.
	private float translateGestureFactor = 0.2f; //translation between translate gesture to rotating
	private final float scaleMovementFactor = 100f; //translation between scaling to movement in z axis


	Vector3 pcdTrans = new Vector3(0, 0, 0);
	Vector3 pcdRotate = new Vector3(0, 0, 0);

	private final static float MAX_INTENSITY = 3700f;
	private final static float MIN_INTENSITY = 0f;

	public CogniPointCloud2DLayer(Context context, String topicName, String frameToStick) {
		this(context, GraphName.of(topicName), GraphName.of(frameToStick));
	}

	public CogniPointCloud2DLayer(Context context,  GraphName topicName, GraphName frameToStick) {
		super(topicName, PointCloud2._TYPE);
		this.frameToStick = frameToStick;
		mutex = new Object();

		pcdModel = new ModelMatrix();
		pcdModel.scale(pcdScale);
//		pcdModel.move(0,0,-PCD_Z);


		//set factors according to screen density, to keep behaviours on different devices.
		final DisplayMetrics displayMetrics = new DisplayMetrics();
		WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getMetrics(displayMetrics);
		float density = displayMetrics.density;
		translateGestureFactor*= (density/3f);

		cameraPositionMat = new ModelMatrix();
	}

	@Override
	public void draw(VisualizationView view, GL10 gl) {
		if (null != vertexFrontBuffer) {
			float mVP[] = getMVPMatrix();
			//draw the pcd
			synchronized (mutex) {
//				float mMVP[] =new float[16];
//				Matrix.multiplyMM(mMVP, 0 , pcdModel.getMat(), 0, mVP, 0);
//				gl.glLoadMatrixf(mMVP, 0);

				gl.glLoadMatrixf(pcdModel.getMat(), 0);
				gl.glTranslatef(0,0,pcdModel.getZ());
				Vertices.drawPointsWithColors(gl, vertexFrontBuffer, colorsFrontBuffer, POINT_SIZE);
			}
		}
	}

	/**
	 * Sets the camera before drawing.
	 * @return View-Projection matrix.
	 */
	public float[] getMVPMatrix(){
		//first set the camera matrix.
		float mV[] = new float[16];
		float mVP[] = new float[16];
		final float CAMERA_Z = 0.01f;
		// Set the camera position (View matrix)
		Matrix.setLookAtM(mV, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
		Matrix.multiplyMM(mV, 0, cameraPositionMat.getMat(), 0, mV, 0);
		// Calculate the projection and view transformation
		Matrix.multiplyMM(mVP, 0, mProjMatrix, 0, mV, 0);
		return mVP;
	}

	@Override
	public void onSurfaceChanged(VisualizationView view, GL10 gl, int width, int height) {
		// Adjust the viewport based on geometry changes,
		// such as screen rotation
		gl.glViewport(0, 0, width, height);

		float ratio = (float) width / height;

		// this projection matrix is applied to object coordinates
		// in the draw() method
		Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 3, 7);

	}

	@Override
	public boolean onTouchEvent(VisualizationView view, MotionEvent event) {
		if (translateGestureDetector == null || rotateGestureDetector == null || zoomGestureDetector == null) {
			return false;
		}
		if (event.getPointerCount() == 1) {
			final boolean translateGestureHandled = translateGestureDetector.onTouchEvent(event);
			return translateGestureHandled || super.onTouchEvent(view, event);
		} else { //multi touch
//		if(event.getPointerCount()>=2) {
			final boolean rotateGestureHandled = rotateGestureDetector.onTouchEvent(event);
			final boolean zoomGestureHandled = zoomGestureDetector.onTouchEvent(event);
			return rotateGestureHandled || zoomGestureHandled || super.onTouchEvent(view, event);
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


		finalView.post(new Runnable() {
			@Override
			public void run() {
				finalView.setBackgroundColor(BACKGRUND_COLOR);
			}
		});

	}

	private void onGestureDoubleTap(float x, float y) {
		//TODO: ADD
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: double tap detected! : " + x + ", " + y);
	}

	private void onGestureZoom(float focusX, float focusY, float factor) {
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: zoom detected! " + focusX + " , " + focusY + " , " + factor);

		/*float movement =scaleMovementFactor * (1-factor);

		//Move "inside", on z axis of the user.
		float moveX = (float)pcdModel.getAxisXNormalized().getZ() * movement;
		float moveY = (float)pcdModel.getAxisYNormalized().getZ() * movement;
		float moveZ = (float)pcdModel.getAxisZNormalized().getZ() * movement;

		pcdModel.move(moveX, moveY, moveZ);*/
		pcdModel.scale(factor);
	}

	private void onGestureTranslate(float x, float y) {
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: translate detected! : " + x + ", " + y);
		x *= translateGestureFactor;
		y *= translateGestureFactor;


		//rotate on each of the model axes, relative to the user's XY surface.
		//switch between x and y axes. also, y axis is reversed.
		y *= -1;
		pcdModel.rotateX((float)pcdModel.getAxisXNormalized().getX() * y);
		pcdModel.rotateY((float) pcdModel.getAxisYNormalized().getX() * y);
		pcdModel.rotateZ((float) pcdModel.getAxisZNormalized().getX() * y);

		pcdModel.rotateX((float)pcdModel.getAxisXNormalized().getY() * x);
		pcdModel.rotateY((float)pcdModel.getAxisYNormalized().getY() * x);
		pcdModel.rotateZ((float)pcdModel.getAxisZNormalized().getY() * x);
	}


	private void onGestureRotate(float focusX, float focusY, float deltaAngle) {
		deltaAngle= (float)Math.toDegrees(deltaAngle);
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: rotation detected! " + focusX + " , " + focusY + " , " + deltaAngle);

		//rotate on each of the model axes, relative to the user's Z Axis.
		pcdModel.rotateX((float)pcdModel.getAxisXNormalized().getZ() * deltaAngle);
		pcdModel.rotateY((float)pcdModel.getAxisYNormalized().getZ() * deltaAngle);
		pcdModel.rotateZ((float)pcdModel.getAxisZNormalized().getZ() * deltaAngle);
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
