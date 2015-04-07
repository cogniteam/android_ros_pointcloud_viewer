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

import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

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

	private static final Color FREE_SPACE_COLOR = Color.fromHexAndAlpha("377dfa", 0.1f);
	private static final Color OCCUPIED_SPACE_COLOR = Color.fromHexAndAlpha("377dfa", 0.3f);
	private static final float POINT_SIZE = 15.f;

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

	Vector3 pcdTrans = new Vector3(0, 0, 0);
	Vector3 pcdRotate = new Vector3(0, 0, 0);
	float pcdScale = 1;

	private final static float MAX_INTENSITY = 3700f;
	private final static float MIN_INTENSITY = 0f;

	public CogniPointCloud2DLayer(String topicName, String frameToStick) {
		this(GraphName.of(topicName), GraphName.of(frameToStick));
	}

	public CogniPointCloud2DLayer(GraphName topicName, GraphName frameToStick) {
		super(topicName, PointCloud2._TYPE);
		this.frameToStick = frameToStick;
		mutex = new Object();
	}

	@Override
	public void draw(VisualizationView view, GL10 gl) {
		if (null != vertexFrontBuffer) {
			synchronized (mutex) {
				gl.glScalef(pcdScale, pcdScale, pcdScale);
				gl.glRotatef((float) pcdRotate.getX(), 1, 0, 0);
				gl.glRotatef((float) pcdRotate.getY(), 0, 1, 0);
				gl.glRotatef((float) pcdRotate.getZ(), 0, 0, 1);
				gl.glTranslatef((float) pcdTrans.getX(), (float) pcdTrans.getY(), (float) pcdTrans.getZ());
				Vertices.drawPointsWithColors(gl, vertexFrontBuffer, colorsFrontBuffer, POINT_SIZE);
			}
		}
	}

	@Override
	public boolean onTouchEvent(VisualizationView view, MotionEvent event) {
		if (translateGestureDetector == null || rotateGestureDetector == null || zoomGestureDetector == null) {
			return false;
		}
		final boolean translateGestureHandled = translateGestureDetector.onTouchEvent(event);
		final boolean rotateGestureHandled = rotateGestureDetector.onTouchEvent(event);
		final boolean zoomGestureHandled = zoomGestureDetector.onTouchEvent(event);
		return translateGestureHandled || rotateGestureHandled || zoomGestureHandled || super.onTouchEvent(view, event);
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
								CogniPointCloud2DLayer.this.onGestureRotate(focusX, focusY, deltaAngle);
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
		//TODO: ADD
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: zoom detected! " + focusX + " , " + focusY + " , " + factor);
		pcdScale = pcdScale * factor;
	}

	private void onGestureTranslate(float x, float y) {
		//TODO: ADD
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: translate detected! : " + x + ", " + y);
		//x axis is actually y, and vice versa
		pcdRotate = pcdRotate.add((new Vector3(x, 0, y)).scale(0.5f));
	}

	private void onGestureRotate(float focusX, float focusY, double deltaAngle) {
		//TODO: ADD
		Log.i("SPAM", "SPAM: CogniPointCloud2DLayer: rotation detected! " + focusX + " , " + focusY + " , " + deltaAngle);
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
			intensity = (intensity-MIN_INTENSITY)/(MAX_INTENSITY-MIN_INTENSITY);
			intensity = Math.min(Math.max(intensity, 0f),1f);
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
