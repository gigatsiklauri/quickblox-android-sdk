package com.quickblox.sample.videochatwebrtcnew.services;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.quickblox.auth.QBAuth;
import com.quickblox.auth.model.QBSession;
import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBSignaling;
import com.quickblox.chat.QBWebRTCSignaling;
import com.quickblox.chat.listeners.QBVideoChatSignalingManagerListener;
import com.quickblox.core.QBEntityCallbackImpl;
import com.quickblox.core.QBSettings;
import com.quickblox.sample.videochatwebrtcnew.R;
import com.quickblox.sample.videochatwebrtcnew.SessionManager;
import com.quickblox.sample.videochatwebrtcnew.activities.CallActivity;
import com.quickblox.sample.videochatwebrtcnew.activities.ListUsersActivity;
import com.quickblox.sample.videochatwebrtcnew.activities.OpponentsActivity;
import com.quickblox.sample.videochatwebrtcnew.definitions.Consts;
import com.quickblox.sample.videochatwebrtcnew.holder.DataHolder;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCConfig;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientSessionCallbacks;

import org.jivesoftware.smack.SmackException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tereha on 08.07.15.
 */
public class IncomeCallListenerService extends Service implements QBRTCClientSessionCallbacks {

    private static final String TAG = IncomeCallListenerService.class.getSimpleName();
    private QBChatService chatService;
    private String login;
    private String password;
    private PendingIntent pendingIntent;
    private boolean isServiceAutostart;
//    private NetworkChangeReceiver networkChangeReceiver;


    @Override
    public void onCreate() {
        super.onCreate();
        QBSettings.getInstance().fastConfigInit(Consts.APP_ID, Consts.AUTH_KEY, Consts.AUTH_SECRET);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        if (!QBChatService.isInitialized()) {
            Log.d(TAG, "!QBChatService.isInitialized()");
            QBChatService.init(this);
            chatService = QBChatService.getInstance();
        }

        if (intent != null && intent.getExtras()!= null) {
            parseIntentExtras(intent);
            pendingIntent = intent.getParcelableExtra(Consts.PARAM_PINTENT);
        }

        if(!QBChatService.getInstance().isLoggedIn()){
            QBRTCClient.getInstance().removeSessionsCallbacksListener(this);
            createSession(login, password);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification createNotification() {
        Context context = getApplicationContext();
        Intent notificationIntent = new Intent(context, ListUsersActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
//        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context,
                0, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        Notification.Builder notificationBuilder = new Notification.Builder(context);
        notificationBuilder.setSmallIcon(R.drawable.logo_qb)
                .setContentIntent(contentIntent)
                .setTicker(getResources().getString(R.string.service_launched))
                .setWhen(System.currentTimeMillis())
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.logged_in_as) + " " +
                        DataHolder.getUserNameByLogin(login));

        Notification notification = notificationBuilder.build();

        return notification;
    }

    private void initQBRTCClient() {

        try {
            QBChatService.getInstance().startAutoSendPresence(60);
        } catch (SmackException.NotLoggedInException e) {
            e.printStackTrace();
        }

        // Add signalling manager
        QBChatService.getInstance().getVideoChatWebRTCSignalingManager().addSignalingManagerListener(new QBVideoChatSignalingManagerListener() {
            @Override
            public void signalingCreated(QBSignaling qbSignaling, boolean createdLocally) {
                if (!createdLocally) {
                    QBRTCClient.getInstance().addSignaling((QBWebRTCSignaling) qbSignaling);
                }
            }
        });
        QBRTCConfig.setAnswerTimeInterval(Consts.ANSWER_TIME_INTERVAL);

        // Add activity as callback to RTCClient
        QBRTCClient.getInstance().addSessionCallbacksListener(this);

        // Start mange QBRTCSessions according to VideoCall parser's callbacks
        QBRTCClient.getInstance().prepareToProcessCalls(this);
    }

    private void parseIntentExtras(Intent intent) {
        Log.d(TAG, "parseIntentExtras()");
        login = intent.getStringExtra(Consts.USER_LOGIN);
        password = intent.getStringExtra(Consts.USER_PASSWORD);
        isServiceAutostart = intent.getBooleanExtra(Consts.IS_SERVICE_AUTOSTARTED, false);
        Log.d(TAG, "login = " + login + " password = " + password);

        if (TextUtils.isEmpty(login) && TextUtils.isEmpty(password)){
            SharedPreferences sharedPreferences = getSharedPreferences(Consts.SHARED_PREFERENCES, Context.MODE_PRIVATE);
            login = sharedPreferences.getString(Consts.USER_LOGIN, null);
            password = sharedPreferences.getString(Consts.USER_PASSWORD, null);
            Log.d(TAG, "login = " + login + " password = " + password + " from SharedPreferences");
        }
    }

    private void createSession(final String login, final String password) {
        Log.d(TAG, "createSession()");
        if (!TextUtils.isEmpty(login) && !TextUtils.isEmpty(password)) {
            final QBUser user = new QBUser(login, password);
            QBAuth.createSession(login, password, new QBEntityCallbackImpl<QBSession>() {
                @Override
                public void onSuccess(QBSession session, Bundle bundle) {
                    Log.d(TAG, "onSuccess create session with params");
                    user.setId(session.getUserId());

                    if (chatService.isLoggedIn()) {
                        Log.d(TAG, "chatService.isLoggedIn()");
                        startActionsOnSuccessLogin(login, password);
                    } else {
                        Log.d(TAG, "!chatService.isLoggedIn()");
                        chatService.login(user, new QBEntityCallbackImpl<QBUser>() {

                            @Override
                            public void onSuccess(QBUser result, Bundle params) {
                                Log.d(TAG, "onSuccess login to chat with params");
                                startActionsOnSuccessLogin(login, password);
                            }

                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "onSuccess login to chat");
                                startActionsOnSuccessLogin(login, password);
                            }

                            @Override
                            public void onError(List errors) {
                                sendResultToActivity(false);
                                Toast.makeText(IncomeCallListenerService.this, "Error when login", Toast.LENGTH_SHORT).show();
                                for (Object error : errors) {
                                    Log.d(TAG, error.toString());
                                }
                            }
                        });
                    }
                }

                @Override
                public void onSuccess() {
                    super.onSuccess();
                    Log.d(TAG, "onSuccess create session");
                }

                @Override
                public void onError(List<String> errors) {
                    sendResultToActivity(false);
                    Toast.makeText(IncomeCallListenerService.this, "Error when login, check test users login and password", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            sendResultToActivity(false);
            Toast.makeText(IncomeCallListenerService.this, "Error when login, check test users login and password", Toast.LENGTH_SHORT).show();
        }
    }

    private void startActionsOnSuccessLogin(String login, String password) {
        initQBRTCClient();
        sendResultToActivity(true);
        startOpponentsActivity();
        startForeground(1, createNotification());
        saveUserDataToPreferences(login, password);
//        registerNetworkChangeReceiver();
    }

//    private void registerNetworkChangeReceiver() {
//        networkChangeReceiver = new NetworkChangeReceiver();
//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
//        registerReceiver(networkChangeReceiver, intentFilter);
//    }

    private void saveUserDataToPreferences(String login, String password){
        SharedPreferences sharedPreferences = getSharedPreferences(Consts.SHARED_PREFERENCES, MODE_PRIVATE);
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(Consts.USER_LOGIN, login);
        ed.putString(Consts.USER_PASSWORD, password);
        ed.commit();

        Log.d(TAG, "login = " + sharedPreferences.getString(Consts.USER_LOGIN, null)
                + " password = " + sharedPreferences.getString(Consts.USER_PASSWORD, null)
                + " isAutoStarted = " + sharedPreferences.getBoolean(Consts.IS_SERVICE_AUTOSTARTED, false));
    }

    private void startOpponentsActivity(){
        if (!isServiceAutostart) {
            Intent intent = new Intent(IncomeCallListenerService.this, OpponentsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        Log.d(TAG, " isAutoStarted = " + isServiceAutostart);
        isServiceAutostart = false;
    }

    private void sendResultToActivity (boolean isSuccess){
        if (!isServiceAutostart) {
            try {
                Intent intent = new Intent().putExtra(Consts.LOGIN_RESULT, isSuccess);
                pendingIntent.send(IncomeCallListenerService.this, Consts.LOGIN_RESULT_CODE, intent);
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        QBRTCClient.getInstance().removeSessionsCallbacksListener(this);
        QBChatService.getInstance().destroy();
        SessionManager.setCurrentSession(null);
//        unregisterReceiver(networkChangeReceiver);
//        resetAutoStartFlag();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }



    //========== Implement methods ==========//

    @Override
    public void onReceiveNewSession(QBRTCSession qbrtcSession) {
        if (SessionManager.getCurrentSession() == null){
            SessionManager.setCurrentSession(qbrtcSession);
            CallActivity.start(this,
                    qbrtcSession.getConferenceType(),
                    qbrtcSession.getOpponents(),
                    qbrtcSession.getUserInfo(),
                    Consts.CALL_DIRECTION_TYPE.INCOMING);
        } else if (SessionManager.getCurrentSession() != null && !qbrtcSession.equals(SessionManager.getCurrentSession())){
            qbrtcSession.rejectCall(new HashMap<String, String>());
        }
    }

    @Override
    public void onUserNotAnswer(QBRTCSession qbrtcSession, Integer integer) {

    }

    @Override
    public void onCallRejectByUser(QBRTCSession qbrtcSession, Integer integer, Map<String, String> map) {

    }

    @Override
    public void onReceiveHangUpFromUser(QBRTCSession qbrtcSession, Integer integer) {

    }

    @Override
    public void onSessionClosed(QBRTCSession qbrtcSession) {
    }

    @Override
    public void onSessionStartClose(QBRTCSession qbrtcSession) {

    }

//    public class NetworkChangeReceiver extends BroadcastReceiver {
//
//        @Override
//        public void onReceive(final Context context, final Intent intent) {
//
//            final ConnectivityManager connMgr = (ConnectivityManager) context
//                    .getSystemService(Context.CONNECTIVITY_SERVICE);
//
//            final android.net.NetworkInfo wifi = connMgr
//                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
//
//            final android.net.NetworkInfo mobile = connMgr
//                    .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
//
//            if (wifi.isAvailable() || mobile.isAvailable()) {
//                reloginToChat(login, password);
//                Toast.makeText(context, "Login to chat by connected to internet", Toast.LENGTH_LONG).show();
//            } else {
//                try {
//                    QBChatService.getInstance().logout();
//                    Toast.makeText(context, "Log out from chat by disconnected from internet", Toast.LENGTH_LONG).show();
//                } catch (SmackException.NotConnectedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }
//
//    private void reloginToChat(String login, String password) {
//        if (!TextUtils.isEmpty(login) && !TextUtils.isEmpty(password)) {
//            final QBUser user = new QBUser(login, password);
//            if (chatService != null && !chatService.isLoggedIn()) {
//                chatService.login(user, new QBEntityCallbackImpl<QBUser>() {
//
//                    @Override
//                    public void onSuccess(QBUser result, Bundle params) {
//                        Log.d(TAG, "onSuccess login to chat with params");
//                    }
//
//                    @Override
//                    public void onSuccess() {
//                        Log.d(TAG, "onSuccess login to chat");
//                    }
//
//                    @Override
//                    public void onError(List errors) {
//                        Toast.makeText(IncomeCallListenerService.this, "Error when login", Toast.LENGTH_SHORT).show();
//                        for (Object error : errors) {
//                            Log.d(TAG, error.toString());
//                        }
//                    }
//                });
//
//            }
//        }
//    }
}
