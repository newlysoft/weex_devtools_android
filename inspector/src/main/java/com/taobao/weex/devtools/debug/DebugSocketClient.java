package com.taobao.weex.devtools.debug;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;
import com.taobao.weex.devtools.websocket.SimpleSession;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okio.Buffer;
import okio.BufferedSource;

public class DebugSocketClient implements WebSocketListener, SimpleSession {

  private static final String TAG = "DebugSocketClient";
  private static final int CONNECT_TO_WEB_SOCKET = 1;
  private static final int SEND_MESSAGE = 2;
  private static final int CLOSE_WEB_SOCKET = 3;
  private static final int DISCONNECT_LOOPER = 4;
  private static final String KEY_MESSAGE = "web_socket_message";
  private Handler mHandler;
  private WebSocket mWebSocket;
  private OkHttpClient mHttpClient;
  private Callback mConnectCallback;
  private DebugServerProxy mProxy;
  private HandlerThread mHandlerThread;

  public DebugSocketClient(DebugServerProxy proxy) {
    mProxy = proxy;
    mHandlerThread = new HandlerThread("DebugServerProxy");
    mHandlerThread.start();
    mHandler = new MessageHandler(mHandlerThread.getLooper());
  }

  public void connect(String url, Callback callback) {
    mConnectCallback = callback;
    Message message = Message.obtain();
    message.what = CONNECT_TO_WEB_SOCKET;
    Bundle data = new Bundle();
    data.putString(KEY_MESSAGE, url);
    message.setData(data);
    if (mHandlerThread != null && mHandlerThread.isAlive()) {
      mHandler.sendMessage(message);
    }
  }

  @Override
  public void onMessage(BufferedSource payload, WebSocket.PayloadType type)
      throws IOException {
    mProxy.handleMessage(payload, type);
  }

  @Override
  public void onClose(int code, String reason) {
    if (mHandlerThread != null && mHandlerThread.isAlive()) {
      mHandler.sendEmptyMessage(CLOSE_WEB_SOCKET);
    }
  }

  @Override
  public void onPong(Buffer payload) {
    // ignore
  }

  @Override
  public void onFailure(IOException e) {
    abort("Websocket exception", e);
  }

  @Override
  public void onOpen(WebSocket webSocket, Request arg1, Response arg2)
      throws IOException {
    mWebSocket = webSocket;
    if (mConnectCallback != null) {
      mConnectCallback.onSuccess(null);
    }
  }

  @Override
  public void sendText(String payload) {
    Message message = Message.obtain();
    message.what = SEND_MESSAGE;
    Bundle data = new Bundle();
    data.putString(KEY_MESSAGE, payload);
    message.setData(data);
    if (mHandlerThread != null && mHandlerThread.isAlive()) {
      mHandler.sendMessage(message);
    }
  }

  @Override
  public void sendBinary(byte[] payload) {
  }

  @Override
  public void close(int closeReason, String reasonPhrase) {
    if (mHandlerThread != null && mHandlerThread.isAlive()) {
      mHandler.sendEmptyMessage(CLOSE_WEB_SOCKET);
    }
  }

  @Override
  public boolean isOpen() {
    return mWebSocket != null;
  }

  private void connect(String url) {
    if (mHttpClient != null) {
      throw new IllegalStateException("DebugSocketClient is already initialized.");
    }
    mHttpClient = new OkHttpClient();
    mHttpClient.setConnectTimeout(30, TimeUnit.SECONDS);
    mHttpClient.setWriteTimeout(30, TimeUnit.SECONDS);
    // Disable timeouts for read
    mHttpClient.setReadTimeout(0, TimeUnit.MINUTES);

    if (!TextUtils.isEmpty(url)) {
      Request request = new Request.Builder().url(url).build();
      WebSocketCall call = WebSocketCall.create(mHttpClient, request);
      call.enqueue(this);
    }
  }

  private void closeQuietly() {
    if (mWebSocket != null) {
      try {
        mWebSocket.close(1000, "End of session");
      } catch (IOException e) {
        // swallow, no need to handle it here
        Log.e(TAG, "closeQuietly IOException " + e.toString());
      }
      mWebSocket = null;
    }
  }

  private void sendMessage(int requestID, String message) {
    if (mWebSocket == null) {
      return;
    }
    Buffer messageBuffer = new Buffer();
    messageBuffer.writeUtf8(message);
    try {
      // Log.v(TAG, "sendMessage " + message);
      mWebSocket.sendMessage(WebSocket.PayloadType.TEXT, messageBuffer);
    } catch (IOException e) {
      Log.e(TAG, "sendMessage IOException " + e.toString());
    }
  }



  private void abort(String message, Throwable cause) {
    Log.v(TAG, "Error occurred, shutting down websocket connection: " + message);
    closeQuietly();

    // Trigger failure callbacks
    if (mConnectCallback != null) {
      mConnectCallback.onFailure(cause);
      mConnectCallback = null;
    }
  }



  public interface Callback {
    void onSuccess(String response);

    void onFailure(Throwable cause);
  }

  class MessageHandler extends Handler {

    MessageHandler(Looper looper) {
      super(looper);
    }

    public void handleMessage(Message msg) {
      switch (msg.what) {
        case CONNECT_TO_WEB_SOCKET:
          connect(msg.getData().getString(KEY_MESSAGE));
          break;
        case SEND_MESSAGE:
          DebugSocketClient.this.sendMessage(0, msg.getData().getString(KEY_MESSAGE));
          break;
        case CLOSE_WEB_SOCKET:
          closeQuietly();
          mHandlerThread.quit();
          break;
        case DISCONNECT_LOOPER:
          closeQuietly();
          mHandlerThread.quit();
          break;
      }
    }
  }
}
