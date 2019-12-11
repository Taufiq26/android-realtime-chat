package com.example.realtimechat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.realtimechat.services.MySingleton;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    SwipeRefreshLayout mSwipeRefreshLayout;

    private static final int limitPerLoad = 10;

    public static String TAG = "FirebaseUI.chat";
    private Firebase mRef;
    private Query mChatRef;
    private String roomChat;
    private int roomChatLimit;

    // variable for data user
//    private long userId = 1;
//    private String mName = "Ipin";
    private long userId = 2;
    private String mName = "Upin";
    private String mDeviceToken;
    private String mTime;
    private Button mSendButton;
    private EditText mMessageEdit;
    // ----------

    // variable for sending notification
    private List<String> devicesToken = new ArrayList<String>();

    final private String FCM_API = "https://fcm.googleapis.com/fcm/send";
    final private String serverKey = "key=" + "AAAAGz6ZO2c:APA91bHZFSDy7ab5vC7I33NFkovQOf2z8VqkT0W5exgNFRCD002saL8ac8chLjo7KnNlQtGbSK5Zyett0AsMNXAkfQTqEpiwKef5P5b4VqMrol0w2K0oRWPFsY-AY3kPi33sdeOTPi0p";
    final private String contentType = "application/json";
    final String TAG_NOTIF = "NOTIFICATION TAG";

    String NOTIFICATION_TITLE;
    String NOTIFICATION_MESSAGE;
    // ----------

    private RecyclerView mMessages;
    private FirebaseRecyclerAdapter<ChatModel, ChatHolder> mRecycleViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Firebase.setAndroidContext(this);

        mSendButton = (Button) findViewById(R.id.sendButton);
        mMessageEdit = (EditText) findViewById(R.id.messageEdit);


        // generate token device
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            //To do//
                            return;
                        }

                        // Get the Instance ID token//
                        String token = task.getResult().getToken();
                        String msg = "FCM Token : "+token;
                        Log.d(TAG, msg);
                        mDeviceToken = token;
                    }
                });



        mRef = new Firebase("https://realtimechat-8b01a.firebaseio.com");
        roomChat = "chat";
        roomChatLimit = limitPerLoad;
        // no limit chat to show
//        mChatRef = mRef.child(roomChat);
        // set limit chat to show from last
        mChatRef = mRef.child(roomChat).limitToLast(roomChatLimit);

        mRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // check if room chat doesn't exists
                if (!dataSnapshot.hasChild(roomChat)) {
                    // create new room chat
                    DatabaseReference root = FirebaseDatabase.getInstance().getReference().getRoot();
                    Map<String,Object> map = new HashMap<String,Object>();
                    map.put(roomChat,"");
                    root.updateChildren(map);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });

        initRecycler();

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // set data
                ChatModel chat = new ChatModel(mMessageEdit.getText().toString(), mName, userId, System.currentTimeMillis(), mTime, mDeviceToken);
                // send data to realtime database
                mRef.child(roomChat).push().setValue(chat, new Firebase.CompletionListener() {
                    @Override
                    public void onComplete(FirebaseError firebaseError, Firebase firebase) {
                        if (firebaseError != null) {
                            Log.e(TAG, firebaseError.toString());
                        }
                    }
                });

                // send notification to other user(s)
                NOTIFICATION_TITLE = mName;
                NOTIFICATION_MESSAGE = mMessageEdit.getText().toString();

                JSONObject notification = new JSONObject();
                JSONObject notifcationBody = new JSONObject();
                for (int i=0; i<devicesToken.size(); i++) {
                    Log.i("Other Device Token ",devicesToken.get(i));
                    try {
                        notifcationBody.put("title", NOTIFICATION_TITLE);
                        notifcationBody.put("body", NOTIFICATION_MESSAGE);

                        notification.put("to", devicesToken.get(i));
                        notification.put("data", notifcationBody);
                        notification.put("notification", notifcationBody);
                    } catch (JSONException e) {
                        Log.e(TAG, "onCreate: " + e.getMessage() );
                    }
                    sendNotification(notification);
                }
                // ----------

                mMessageEdit.setText("");
            }
        });
    }

    /**
     * This method is called when swipe refresh is pulled down
     */
    @Override
    public void onRefresh() {
        roomChatLimit += limitPerLoad;
        mChatRef = mRef.child(roomChat).limitToLast(roomChatLimit);

        loadRecyclerViewData();
    }

    private void initRecycler() {
        mMessages = (RecyclerView) findViewById(R.id.messagesList);

        LinearLayoutManager manager = new LinearLayoutManager(this);
        // keep order to normal (top to bottom)
        manager.setReverseLayout(false);
        // auto to the bottom of the view
        manager.setStackFromEnd(true);

        mMessages.setHasFixedSize(false);
        mMessages.setLayoutManager(manager);

        // pull to refresh (load more)
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener((SwipeRefreshLayout.OnRefreshListener) this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary,
                android.R.color.holo_green_dark,
                android.R.color.holo_orange_dark,
                android.R.color.holo_blue_dark);

        /**
         * Showing Swipe Refresh animation on activity create
         * As animation won't start on onCreate, post runnable is used
         */
        mSwipeRefreshLayout.post(new Runnable() {

            @Override
            public void run() {

                mSwipeRefreshLayout.setRefreshing(true);

                // Fetching data from server
                loadRecyclerViewData();
            }
        });
    }

    private void loadRecyclerViewData() {
        mSwipeRefreshLayout.setRefreshing(true);

        if (roomChatLimit > limitPerLoad) {
            LinearLayoutManager manager = new LinearLayoutManager(this);
            // keep order to normal (top to bottom)
            manager.setReverseLayout(false);

            manager.setStackFromEnd(false);

            mMessages.setHasFixedSize(false);
            mMessages.setLayoutManager(manager);
        }

        devicesToken.clear();
        mRecycleViewAdapter = new FirebaseRecyclerAdapter<ChatModel, ChatHolder>(ChatModel.class, R.layout.text_message, ChatHolder.class, mChatRef) {
            @Override
            public void populateViewHolder(ChatHolder chatView, ChatModel chat, int position) {
                chatView.setText(chat.getMessage());
                chatView.setName(chat.getName());
                chatView.setTime(chat.getFormattedTime());

                if (chat.getUserId() == userId) {
                    chatView.setIsSender(true);
                } else {
                    chatView.setIsSender(false);
                }

                // save devices token
                if (devicesToken.size() == 0) {
                    Log.i("Devices Token yeuh : ","masuk if 1");
                    if (!mDeviceToken.equals(chat.getDeviceToken())) {
                        Log.i("Devices Token yeuh : ","masuk if 2");
                        devicesToken.add(chat.getDeviceToken());
                    }
                } else {
                    int duplicate = 0;
                    // find token in array
                    for (int i = 0; i < devicesToken.size(); i++) {
                        if (chat.getDeviceToken().equals(devicesToken.get(i)))
                            duplicate = 1;
                    }

                    // check if no device token exactly the same and not equal with mine
                    if (duplicate == 0 && !devicesToken.equals(chat.getDeviceToken()))
                        devicesToken.add(chat.getDeviceToken());
                }
            }
        };

        mMessages.setAdapter(mRecycleViewAdapter);

        // Stopping swipe refresh
        mSwipeRefreshLayout.setRefreshing(false);
    }

    private void sendNotification(JSONObject notification) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                new com.android.volley.Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(TAG_NOTIF, "onResponse: " + response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MainActivity.this, "Request error", Toast.LENGTH_LONG).show();
                        Log.i(TAG_NOTIF, "onErrorResponse: Didn't work\n"+error);
                    }
                }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", serverKey);
                params.put("Content-Type", contentType);
                return params;
            }
        };
        MySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjectRequest);
    }

    public static class ChatHolder extends RecyclerView.ViewHolder {
        View mView;

        public ChatHolder(View itemView) {
            super(itemView);
            mView = itemView;
        }

        public void setIsSender(Boolean isSender) {
            FrameLayout left_arrow = (FrameLayout) mView.findViewById(R.id.left_arrow);
            FrameLayout right_arrow = (FrameLayout) mView.findViewById(R.id.right_arrow);
            RelativeLayout messageContainer = (RelativeLayout) mView.findViewById(R.id.message_container);
            LinearLayout message = (LinearLayout) mView.findViewById(R.id.message);


            if (isSender) {
                left_arrow.setVisibility(View.GONE);
                right_arrow.setVisibility(View.VISIBLE);
                messageContainer.setGravity(Gravity.RIGHT);
            } else {
                left_arrow.setVisibility(View.VISIBLE);
                right_arrow.setVisibility(View.GONE);
                messageContainer.setGravity(Gravity.LEFT);
            }
        }

        public void setName(String name) {
            TextView field = (TextView) mView.findViewById(R.id.name_text);
            field.setText(name);
        }

        public void setText(String text) {
            TextView field = (TextView) mView.findViewById(R.id.message_text);
            field.setText(text);
        }

        public void setTime(String time){
            TextView field = (TextView) mView.findViewById(R.id.time_text);
            field.setText(time);
        }
    }
}
