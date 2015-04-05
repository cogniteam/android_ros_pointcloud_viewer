package org.ros.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;

import com.google.common.base.Preconditions;

import org.ros.address.InetAddressFactory;
import org.ros.exception.RosRuntimeException;
import org.ros.node.NodeMainExecutor;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by omri on 01/04/15.
 *
 * An adapter for ROS, based on RosActivity.
 * Currently, supported mainly for activities.
 *
 * INSTRUCTIONS:
 *
 * 1. Use composition.
 * 2. in the "onCreate()" method of the activity, construct an object from this class. (FURTHER INSTRUCTIONS AHEAD)
 * 3. Call this object's start() and destroy() methods on the activity's onStart() and onDestroy().
 *
 * When creating the object:
 * The "init(NodeMainExecutor)" must be implemented. This executor is used to execute NodeMain type.
 * There are many examples of how to do it.
 *
 * Example:
 * A publisher to "/pub" that publisher each 80ms a std_msgs/String.
 * A subscriber to "sub", for a std_msgs/String message.

 rat = new RosAdapter(RosTest.this, new URI("http://10.0.0.13:11311"), "ticker" , "test title" ) {
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
//execute the node
 NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress(), getMasterUri());
 nodeMainExecutor.execute(nm, nodeConfiguration.setNodeName(nm.getDefaultNodeName()));
 }
};
*/

public abstract class RosAdapter {
    private final ServiceConnection nodeMainExecutorServiceConnection;
    private final String notificationTicker;
    private final String notificationTitle;

    protected NodeMainExecutorService nodeMainExecutorService;

    private Context context;

    private final class NodeMainExecutorServiceConnection implements ServiceConnection {

        private URI customMasterUri;

        public NodeMainExecutorServiceConnection(URI customUri) {
            super();
            customMasterUri = customUri;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            nodeMainExecutorService = ((NodeMainExecutorService.LocalBinder) binder).getService();

            if (customMasterUri != null) {
                nodeMainExecutorService.setMasterUri(customMasterUri);
                nodeMainExecutorService.setRosHostname(getDefaultHostAddress());
            }
            nodeMainExecutorService.addListener(new NodeMainExecutorServiceListener() {
                @Override
                public void onShutdown(NodeMainExecutorService nodeMainExecutorService) {
                    // We may have added multiple shutdown listeners and we only want to
                    // call finish() once.

                    //TODO: is the following comment should be handled?
                    /*if (!context.isFinishing()) {
                        context.finish();
                    }*/
                }
            });
            if (getMasterUri() == null) {
                throw new RuntimeException("RosAdapter: no master uri was set!");
//                startMasterChooser();
            } else {
                init();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

    };

    /**
     * Constructs a ros adapter.
     * @param context: The context that is holding this adapter.
     * @param rosMasterUri: The ROS_MASTER_URI.
     * @param notificationTicker: The text that is shown when the notification first appears
     * @param notificationTitle: The title of the notification bar.
     */
    protected RosAdapter(Context context, URI rosMasterUri, String notificationTicker, String notificationTitle) {
        super();
        this.context = context;
        this.notificationTicker = notificationTicker;
        this.notificationTitle = notificationTitle;
        nodeMainExecutorServiceConnection = new NodeMainExecutorServiceConnection(rosMasterUri);
    }

    /**
     * To be called on the onStart() from the parent activity.
     */
    public void start() {
        bindNodeMainExecutorService();
    }

    protected void bindNodeMainExecutorService() {
        Intent intent = new Intent(context, NodeMainExecutorService.class);
        intent.setAction(NodeMainExecutorService.ACTION_START);
        intent.putExtra(NodeMainExecutorService.EXTRA_NOTIFICATION_TICKER, notificationTicker);
        intent.putExtra(NodeMainExecutorService.EXTRA_NOTIFICATION_TITLE, notificationTitle);
        context.startService(intent);
        Preconditions.checkState(
                context.bindService(intent, nodeMainExecutorServiceConnection, Context.BIND_AUTO_CREATE),
                "Failed to bind NodeMainExecutorService.");
    }

    /**
     * to be called on the onDestroy from the parent activity
     */
    public void destroy() {
        context.unbindService(nodeMainExecutorServiceConnection);
    }

    protected void init() {
        // Run init() in a new thread as a convenience since it often requires
        // network access.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                RosAdapter.this.init(nodeMainExecutorService);
                return null;
            }
        }.execute();
    }


    /**
     * This method is called in a background thread once this {@link android.app.Activity} has
     * been initialized with a master {@link java.net.URI} via the {@link org.ros.android.MasterChooser}
     * and a {@link NodeMainExecutorService} has started. Your {@link org.ros.node.NodeMain}s
     * should be started here using the provided {@link org.ros.node.NodeMainExecutor}.
     *
     * @param nodeMainExecutor
     *          the {@link org.ros.node.NodeMainExecutor} created for this {@link android.app.Activity}
     */
    protected abstract void init(NodeMainExecutor nodeMainExecutor);

    /**
     * Sets the ros master uri for the adapter.
     * Equivalent to setRosMaster(rosMasterUri , false, true).
     * @param rosMasterUri: The uri.
     */
    public void setRosMaster(String rosMasterUri) {
        setRosMaster(rosMasterUri, false, true);
    }

    /**
     * Sets the ros maaster uri for the adapter.
     * @param rosMasterUri: The uri.
     * @param createNewRosMaster: True for a new ros master, false otherwise.
     * @param rosMasterPrivate: If creating a new ros master - true for private, false for public.
     */
    public void setRosMaster(String rosMasterUri , boolean createNewRosMaster, boolean rosMasterPrivate){
        String host = getDefaultHostAddress();

        nodeMainExecutorService.setRosHostname(host);
        if (createNewRosMaster) {
            nodeMainExecutorService.startMaster(rosMasterPrivate);
        } else {
            URI uri;
            try {
                uri = new URI(rosMasterUri);
            } catch (URISyntaxException e) {
                throw new RosRuntimeException(e);
            }
            nodeMainExecutorService.setMasterUri(uri);
        }
        // Run init() in a new thread as a convenience since it often requires network access.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                RosAdapter.this.init(nodeMainExecutorService);
                return null;
            }
        }.execute();
    }

    public URI getMasterUri() {
        Preconditions.checkNotNull(nodeMainExecutorService);
        return nodeMainExecutorService.getMasterUri();
    }

    public String getRosHostname() {
        Preconditions.checkNotNull(nodeMainExecutorService);
        return nodeMainExecutorService.getRosHostname();
    }

    private String getDefaultHostAddress() {
        return InetAddressFactory.newNonLoopback().getHostAddress();
    }


}
