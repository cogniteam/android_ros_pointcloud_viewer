This android application extends rosjava features, to enable viewing PointCloud2 messages via ros. (rosjava's description can be found at the end of this file)

##For using this application out-of-the-box:
  1. Install the app on your phone, using android-studio.
  2. Run the application.
  3. Enter details for ros master, and press connect.
  4. Publish a PointCloud2 message that you wish to view. NOTE: it may take a few seconds (up to ~30 seconds) until the message is being fully processed. Details on the default structure of the message, and defsult topics, can be found in another topic in this file.
  5. Use the different controls available to view the Pointcloud2 message. Details about the controls can be found in another topic of this file.

##Controls:
There are 3 main controls for viewing the PointCloud:
  1. A vertical slider, which can be found on the right side of the screen. Sliding this contol up will move the camera in ("step in"), and sliding it down will have the same effect to the opposite direction.
  2. A 2-dimensional joystick, which can be found beneath the slider. se it to move the camera vertically or horizontally.
  3. Gestures: 
    1. Rotation - will rotate the camera on its z-axis (clockwise/counter-clockwise).
    2. 1-Finger swiping - will move the camera's view horizontally or vertically. 
    3. 2-Finger swiping - will rotate the point cloud on its center.
    4. Double click - will reset the view, and move the camera to the center of the point cloud.

###Default topics:
-sensor_msgs/PointCloud2 to topic /cloud/source.

###Default structure of the PointCloud2 message:
By default, each point in the message should have the following fields (in order):
  1. Float representing x.
  2. Float representing y.
  3. Float representing z.
  4. Empty float (is being discarded).
  5. Intensity, which is used to set each point's grayscale color.
Parsing of the message can be easily changed in the code.


rosjava is the first pure Java implementation of ROS.

From [ROS.org](http://www.ros.org/wiki/): ROS is an open-source, meta-operating system for your robot. It provides the services you would expect from an operating system, including hardware abstraction, low-level device control, implementation of commonly-used functionality, message-passing between processes, and package management.

Developed at Google in cooperation with Willow Garage, rosjava enables integration of Android and ROS compatible robots. This project is under active development and currently alpha quality software. Please report bugs and feature requests on the [issues list](https://github.com/rosjava/rosjava/issues?state=open).

To get started, visit the [rosjava_core](http://rosjava.github.com/rosjava_core/) and [android_core](http://rosjava.github.com/android_core/) documentation.

Still have questions? Check out the ros-users [discussion list](https://code.ros.org/mailman/listinfo/ros-users), post questions to [ROS Answers](http://answers.ros.org/questions/) with the tag "rosjava," or join #ROS on irc.oftc.net.

rosjava was announced publicly during the [Cloud Robotics tech talk at Google I/O 2011](http://www.youtube.com/watch?feature=player_embedded&v=FxXBUp-4800).

Looking for a robot platform to experiment with ROS, Android, and cloud robotics? The [Willow Garage](http://www.willowgarage.com/) [TurtleBot](http://www.willowgarage.com/turtlebot) is a great mobile perception platform for [getting started with robotics development](http://www.youtube.com/watch?feature=player_embedded&v=MOEjL8JDvd0).

Visit the rosjava_core wiki for instructions.

http://ros.org/wiki/android_core