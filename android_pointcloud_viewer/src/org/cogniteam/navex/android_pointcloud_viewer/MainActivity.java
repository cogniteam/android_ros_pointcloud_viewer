/*
 * Copyright (C) 2011 Google Inc.
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

package org.cogniteam.navex.android_pointcloud_viewer;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.ros.address.InetAddressFactory;
import org.ros.android.RosActivity;
import org.ros.android.view.visualization.PointCloudView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

/**
 * An app that can be used to control a remote robot. This app also demonstrates
 * how to use some of views from the rosjava android library.
 *
 * @author munjaldesai@google.com (Munjal Desai)
 * @author moesenle@google.com (Lorenz Moesenlechner)
 */
public class MainActivity extends RosActivity {

    private PointCloudView pcdVisualizationView;

    public MainActivity() {
        super("Point Cloud Viewer", "Point Cloud Viewer");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    /*switch (item.getItemId()) {
      case R.id.virtual_joystick_snap:
        if (!item.isChecked()) {
          item.setChecked(true);
          virtualJoystickView.EnableSnapping();
        } else {
          item.setChecked(false);
          virtualJoystickView.DisableSnapping();
        }
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }*/
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        pcdVisualizationView = (PointCloudView) findViewById(R.id.pcd_visualization);
		pcdVisualizationView.setControlMode(PointCloudView.BUTTONS_AND_GESTURES);
		pcdVisualizationView.onCreate(MainActivity.this, "/cloud/source", "map");
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        pcdVisualizationView.init(nodeMainExecutor);

        NodeConfiguration nodeConfiguration =
                NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress(),
                        getMasterUri());

        nodeMainExecutor.execute(pcdVisualizationView.getNodeMain(), nodeConfiguration.setNodeName("android/pcd_view"));
    }
}
