package smart.rowan.chatting;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.util.HashMap;

import smart.rowan.HomeActivity;
import smart.rowan.etc.MethodClass;

public class EmployerService extends Service {
    private EmployerService service;
    public static NotificationManager mNotificationManager;
    private static final String WAITER = "WAITER";
    private static final String ERROR_MSG = "Occurred temporary error. Please check network stat and try again. - employerService";
    private String mId;
    private long lastKey;
    private MethodClass methodClass;
    private SharedPreferences myData;
    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference();
    private LayoutInflater inflater;
    private Toast toast;
    private long parentCountMsg;
    String key;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public EmployerService() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
            mNotificationManager = null;
        }
        //  Log.d("service", "is down");
        if (databaseReference != null) {
            for (int i = 0; i < parentCountMsg * 64; i++) {
                databaseReference.removeEventListener(parentListener);
                databaseReference.child(key).removeEventListener(childListener);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            myData = getSharedPreferences("SharedData", Context.MODE_PRIVATE);
            inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            toast = new Toast(getApplicationContext());
            service = this;
            methodClass = new MethodClass();
            mId = methodClass.replaceComma(myData.getString("email", "noEmail"));
            lastKey = myData.getLong("oLastMsg", 0L);

            databaseReference.addValueEventListener(parentListener);
        } catch (Exception e) {
            Toast.makeText(this, ERROR_MSG, Toast.LENGTH_SHORT).show();
        }
        return START_STICKY;
    }

    ValueEventListener parentListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            parentCountMsg = dataSnapshot.getChildrenCount();
            for (DataSnapshot receiveSnapShot : dataSnapshot.getChildren()) {
                key = receiveSnapShot.getKey();
                if (key.contains(mId)) {
                    databaseReference.child(key).limitToLast(30).addChildEventListener(childListener);
                }
            }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
    };

    ChildEventListener childListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            ChatData chatData = dataSnapshot.getValue(ChatData.class);
            long oldKey = Long.parseLong(chatData.getTimes());
            String senderName = chatData.getUserName();
            String msg = chatData.getMessage();
            String senderName2 = myData.getString("senderEmail", "");
            String hashString = myData.getString("messageMap", null);
            HashMap<String, Integer> messageMap;
            if (hashString == null) {
                messageMap = new HashMap<>();
            } else {
                messageMap = MethodClass.changeStringToHashMap(hashString);
            }
            if (!senderName.equals(mId)) {
                if (lastKey < oldKey && !senderName2.equals(senderName)) {
                    if (!messageMap.containsKey(senderName)) {
                        messageMap.put(senderName, 1);
                    } else if (messageMap.containsKey(senderName)) {
                        int count = messageMap.get(senderName);
                        count++;
                        messageMap.put(senderName, count);
                    }
                    Gson gson = new Gson();
                    String hashMapString = gson.toJson(messageMap);

                    SharedPreferences.Editor editor = myData.edit();
                    lastKey = oldKey;
                    editor.putLong("oLastMsg", oldKey);
                    editor.putString("messageMap", hashMapString);
                    editor.apply();
                    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (Build.VERSION.SDK_INT <= 23) {
                        toast = methodClass.showToastMsg(inflater, chatData.getMyName(), toast, msg, getApplicationContext());
                    }
                    Intent intent = new Intent(service, HomeActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                    Resources resources = getResources();
                    methodClass.showNotificationMsg(builder, chatData.getMessage(), pendingIntent, resources, WAITER);
                    mNotificationManager.notify(777, builder.build());
                }
            }
        }
        @Override public void onChildChanged(DataSnapshot dataSnapshot, String s) {}
        @Override public void onChildRemoved(DataSnapshot dataSnapshot) {}
        @Override public void onChildMoved(DataSnapshot dataSnapshot, String s) {}
        @Override public void onCancelled(DatabaseError databaseError) {}
    };
}