package gr.kalymnos.sk3m3l10.mybluetoothchat.mvc_model;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.TabLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static gr.kalymnos.sk3m3l10.mybluetoothchat.mvc_model.BluetoothConstants.Actions.ACTION_CLIENT_CONNECTED;
import static gr.kalymnos.sk3m3l10.mybluetoothchat.mvc_model.BluetoothConstants.Actions.ACTION_DISCONNECTED;
import static gr.kalymnos.sk3m3l10.mybluetoothchat.mvc_model.BluetoothConstants.Actions.ACTION_MESSAGE_RECEIVED;
import static gr.kalymnos.sk3m3l10.mybluetoothchat.mvc_model.BluetoothConstants.Actions.ACTION_SERVER_CONNECTED;
import static gr.kalymnos.sk3m3l10.mybluetoothchat.mvc_model.BluetoothConstants.Extras.EXTRA_MESSAGE;

public class BluetoothServiceImpl extends BluetoothService {

    private static BluetoothService instance = null;
    private ChatManager chatManager;

    private BluetoothServiceImpl(Context context) {
        super(context);
    }

    public static synchronized BluetoothService getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothServiceImpl(context);
        }
        return instance;
    }

    @Override
    protected void manageServersConnectedSocket(BluetoothSocket socket) {
        Intent serverData = new Intent(ACTION_SERVER_CONNECTED);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(serverData);
        startChatManager(socket);
    }

    @Override
    protected void manageClientsConnectedSocket(BluetoothSocket socket) {
        Intent clientData = new Intent(ACTION_CLIENT_CONNECTED);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(clientData);
        startChatManager(socket);
    }

    private void startChatManager(BluetoothSocket socket) {
        if (chatManager == null) {
            chatManager = new ChatManager(socket);
            chatManager.start();
        }
    }

    private void stopChatManager() {
        if (chatManager != null) {
            chatManager.cancel();
            chatManager = null;
        }
    }

    private class ChatManager extends Thread {
        private static final int BUFFER_SIZE = 1024;
        private static final String TAG = "skemelio "+"ChatManager";
        private final BluetoothSocket socket;

        private final InputStream inputStream;
        private final OutputStream outputStream;
        private byte[] buffer; // Buffer to store the stream

        public ChatManager(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get input and output streams; using temp objects
            // because member streams are final
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occured when creating input stream", e);
            }

            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error occured when creating output stream", e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        @Override
        public void run() {
            buffer = new byte[BUFFER_SIZE];

            // Keep listening to the inputStream until an exception occurs.
            while (true) {
                try {
                    inputStream.read(buffer);
                    broadcastReceivedMessage();
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    broadcastDisconnection();
                    break;
                }
            }
        }

        private void broadcastDisconnection() {
            Intent intent = new Intent(ACTION_DISCONNECTED);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        }

        private void broadcastReceivedMessage() {
            Intent intent = new Intent(ACTION_MESSAGE_RECEIVED);
            intent.putExtra(EXTRA_MESSAGE, new String(buffer));
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        }

        private void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error occured when sending data");
            }
        }

        private void cancel() {
            try {
                Log.d(TAG,"Attempting to close the socket");
                socket.close();
                Log.d(TAG,"Socket closed");
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }

    }

    @Override
    public void write(String message) {
        if (chatManager != null) {
            chatManager.write(message.getBytes());
        }
    }

    @Override
    public void releaseChatResources() {
        stopChatManager();
    }
}
