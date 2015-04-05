package org.ros.android.android_tutorial_teleop;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.ros.address.InetAddressFactory;
import org.ros.android.RosAdapter;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

import std_msgs.String;


/**
 * Created by omri on 01/04/15.
 */
public class RosTest extends Activity {
    RosAdapter rat = null;

    public RosTest(){ super();};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_layout);

        try {
            rat = new RosAdapter(RosTest.this, new URI("http://10.0.0.13:11311"), "STRING 1" , "STRING 2" ) {
                Publisher<std_msgs.String> publisher;
                Subscriber<std_msgs.String> subscriber;
                std_msgs.String msg;

                @Override
                protected void init(NodeMainExecutor nodeMainExecutor) {
                    NodeMain nm = new AbstractNodeMain() {
                        @Override
                        public GraphName getDefaultNodeName() {
                            return GraphName.of("/ros_android_test");
                        }

                        @Override
                        public void onStart(ConnectedNode connectedNode) {
                            publisher = connectedNode.newPublisher("/pub", std_msgs.String._TYPE);
                            msg = publisher.newMessage();
                            msg.setData("hello from android!!");
                            Timer pubTimer = new Timer();
                            pubTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                        publisher.publish(msg);
                                }
                            }, 0 , 80);


                            subscriber = connectedNode.newSubscriber("/sub", std_msgs.String._TYPE);
                            subscriber.addMessageListener(new MessageListener<String>() {
                                @Override
                                public void onNewMessage(String string) {
                                    Log.i("SPAM" , "SPAM: sub got message! " + string.getData());
                                }
                            });
                        }
                    };

                    NodeConfiguration nodeConfiguration =
                            NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress(),
                                    getMasterUri());
                    nodeMainExecutor
                            .execute(nm, nodeConfiguration.setNodeName(nm.getDefaultNodeName()));
                }
            };
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onStart(){
        super.onStart();
        rat.start();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        rat.destroy();
    }
}
