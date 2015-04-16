package org.ros.android.view.visualization.gl_utils;

import android.opengl.Matrix;
import org.ros.rosjava_geometry.Vector3;

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
