package com.tokbox.android.textchatsample;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.opentok.android.OpentokError;
import com.tokbox.android.accpack.textchat.ChatMessage;
import com.tokbox.android.accpack.textchat.TextChatFragment;
import com.tokbox.android.listeners.BasicUIListener;
import com.tokbox.android.listeners.UIListenerException;
import com.tokbox.android.logging.OTKAnalytics;
import com.tokbox.android.logging.OTKAnalyticsData;
import com.tokbox.android.textchatsample.config.OpenTokConfig;
import com.tokbox.android.textchatsample.ui.PreviewCameraFragment;
import com.tokbox.android.textchatsample.ui.PreviewControlFragment;
import com.tokbox.android.textchatsample.ui.RemoteControlFragment;
import com.tokbox.android.utils.ConferenceInfo;
import com.tokbox.android.utils.MediaType;
import com.tokbox.android.utils.PreviewInfo;
import com.tokbox.android.wrapper.OTWrapper;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BasicUIListener, PreviewControlFragment.PreviewControlCallbacks,
        RemoteControlFragment.RemoteControlCallbacks, PreviewCameraFragment.PreviewCameraCallbacks, TextChatFragment.TextChatListener {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    private final String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
    private final int permsRequestCode = 200;

    private RelativeLayout mPreviewViewContainer;
    private RelativeLayout mRemoteViewContainer;
    private RelativeLayout mAudioOnlyView;
    private RelativeLayout mLocalAudioOnlyView;
    private RelativeLayout.LayoutParams layoutParamsPreview;
    private FrameLayout mTextChatContainer;
    private RelativeLayout mCameraFragmentContainer;
    private RelativeLayout mActionBarContainer;

    private TextView mAlert;
    private ImageView mAudioOnlyImage;

    //UI control bars fragments
    private PreviewControlFragment mPreviewFragment;
    private RemoteControlFragment mRemoteFragment;
    private PreviewCameraFragment mCameraFragment;
    private FragmentTransaction mFragmentTransaction;

    //TextChat fragment
    private TextChatFragment mTextChatFragment;

    //Dialog
    ProgressDialog mProgressDialog;

    private OTKAnalyticsData mAnalyticsData;
    private OTKAnalytics mAnalytics;

    private boolean mAudioPermission = false;
    private boolean mVideoPermission = false;


    private OTWrapper sOTWrapper;
    private boolean isConnected = false;
    private boolean isLocal = false;
    private boolean isRemote = false;

    private boolean isCallInProgress = false;

    private String mRemoteId;
    private View mRemoteView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);

        //Init the analytics logging for internal use
        String source = this.getPackageName();

        SharedPreferences prefs = this.getSharedPreferences("opentok", Context.MODE_PRIVATE);
        String guidVSol = prefs.getString("guidVSol", null);
        if (null == guidVSol) {
            guidVSol = UUID.randomUUID().toString();
            prefs.edit().putString("guidVSol", guidVSol).commit();
        }

        setContentView(R.layout.activity_main);

        mPreviewViewContainer = (RelativeLayout) findViewById(R.id.publisherview);
        mRemoteViewContainer = (RelativeLayout) findViewById(R.id.subscriberview);
        mAlert = (TextView) findViewById(R.id.quality_warning);
        mAudioOnlyView = (RelativeLayout) findViewById(R.id.audioOnlyView);
        mLocalAudioOnlyView = (RelativeLayout) findViewById(R.id.localAudioOnlyView);
        mTextChatContainer = (FrameLayout) findViewById(R.id.textchat_fragment_container);
        mCameraFragmentContainer = (RelativeLayout) findViewById(R.id.camera_preview_fragment_container);
        mActionBarContainer = (RelativeLayout) findViewById(R.id.actionbar_preview_fragment_container);

        //request Marshmallow camera permission
        if (ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions, permsRequestCode);
            }
        } else {
            mVideoPermission = true;
            mAudioPermission = true;
        }

        //init the sdk-wrapper
        ConferenceInfo info =
                new ConferenceInfo.ConferenceInfoBuilder(OpenTokConfig.SESSION_ID, OpenTokConfig.TOKEN,
                        OpenTokConfig.API_KEY).name("text-chat-sample-app").build();
        sOTWrapper = new OTWrapper(MainActivity.this, info);
        sOTWrapper.setBasicUIListener(this);

        if (sOTWrapper != null) {
            sOTWrapper.connect();
        }

        //init controls fragments
        if (savedInstanceState == null) {
            mFragmentTransaction = getSupportFragmentManager().beginTransaction();
            initCameraFragment(); //to swap camera
            initPreviewFragment(); //to enable/disable local media
            initRemoteFragment(); //to enable/disable remote media
            initTextChatFragment(); //to send/receive text-messages
            mFragmentTransaction.commitAllowingStateLoss();
        }

        //show connecting dialog
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Please wait");
        mProgressDialog.setMessage("Connecting...");
        mProgressDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if ( sOTWrapper != null && isConnected ){
            sOTWrapper.disconnect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if ( sOTWrapper != null && isCallInProgress() ){
            sOTWrapper.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if ( sOTWrapper != null && isCallInProgress() ){
            sOTWrapper.resume(true);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if ( sOTWrapper != null && isConnected ){
            sOTWrapper.disconnect();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int permsRequestCode, final String[] permissions,
                                           int[] grantResults) {
        switch (permsRequestCode) {
            case 200:
                mVideoPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                mAudioPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED;


                if (!mVideoPermission || !mAudioPermission) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(getResources().getString(R.string.permissions_denied_title));
                    builder.setMessage(getResources().getString(R.string.alert_permissions_denied));
                    builder.setPositiveButton("I'M SURE", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.setNegativeButton("RE-TRY", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestPermissions(permissions, permsRequestCode);
                            }
                        }
                    });
                    builder.show();
                }

                break;
        }
    }

    public void showRemoteControlBar(View v) {
        if (mRemoteFragment != null && isRemote) {
            mRemoteFragment.show();
        }
    }

    //Audio local button event
    @Override
    public void onDisableLocalAudio(boolean audio) {
        if (sOTWrapper != null) {
            sOTWrapper.enableLocalMedia(MediaType.AUDIO, audio);
        }
    }

    //Video local button event
    @Override
    public void onDisableLocalVideo(boolean video) {
        if (sOTWrapper != null) {
            sOTWrapper.enableLocalMedia(MediaType.VIDEO, video);

            if (isRemote) {
                if (!video) {
                    mAudioOnlyImage = new ImageView(this);
                    mAudioOnlyImage.setImageResource(R.drawable.avatar);
                    mAudioOnlyImage.setBackgroundResource(R.drawable.bckg_audio_only);
                    mPreviewViewContainer.addView(mAudioOnlyImage, layoutParamsPreview);
                } else {
                    mPreviewViewContainer.removeView(mAudioOnlyImage);
                }
            } else {
                if (!video) {
                    mLocalAudioOnlyView.setVisibility(View.VISIBLE);
                    mPreviewViewContainer.addView(mLocalAudioOnlyView);
                } else {
                    mLocalAudioOnlyView.setVisibility(View.GONE);
                    mPreviewViewContainer.removeView(mLocalAudioOnlyView);
                }
            }
        }
    }

    //Call button event
    @Override
    public void onCall() {
        Log.i(LOG_TAG, "OnCall");
        if ( sOTWrapper != null && isConnected ) {
            if ( !isCallInProgress ) {
                sOTWrapper.startSharingMedia(new PreviewInfo.PreviewInfoBuilder().
                        name("Tokboxer").build());
                if ( mPreviewFragment != null ) {
                    mPreviewFragment.setEnabled(true);
                }
                isCallInProgress = true;

                //Check if there are some connected remotes
                if ( isRemote ){
                    if (!sOTWrapper.isRemoteMediaEnabled(mRemoteId, MediaType.VIDEO)){
                        onAudioOnly(true);
                    }
                    else {
                        setRemoteView(mRemoteView);
                    }
                }
            } else {
                sOTWrapper.stopSharingMedia();
                isCallInProgress = false;
                cleanViewsAndControls();
            }
        }
    }

    //TextChat button event
    @Override
    public void onTextChat() {

        if (mTextChatContainer.getVisibility() == View.VISIBLE) {
            mTextChatContainer.setVisibility(View.GONE);
            showAVCall(true);
        } else {
            showAVCall(false);
            mTextChatContainer.setVisibility(View.VISIBLE);
        }
    }

    //Audio remote button event
    @Override
    public void onDisableRemoteAudio(boolean audio) {
        if (sOTWrapper != null) {
            sOTWrapper.enableRemoteMedia(mRemoteId, MediaType.AUDIO, audio);
        }
    }

    //Video remote button event
    @Override
    public void onDisableRemoteVideo(boolean video) {
       if (sOTWrapper != null) {
           sOTWrapper.enableRemoteMedia(mRemoteId, MediaType.VIDEO, video);
       }
    }

    //Camera control button event
    @Override
    public void onCameraSwap() {
        if (sOTWrapper != null) {
            sOTWrapper.cycleCamera();
        }
    }



   /* @Override
    public void onReconnecting() {
        Log.i(LOG_TAG, "The session is reconnecting.");
        Toast.makeText(this, R.string.reconnecting, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onReconnected() {
        Log.i(LOG_TAG, "The session reconnected.");
        Toast.makeText(this, R.string.reconnected, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCameraChanged(int newCameraId) {
        Log.i(LOG_TAG, "The camera changed. New camera id is: "+newCameraId);
    }*/

    public void onAudioOnly(boolean enabled) {
        if (enabled) {
            mRemoteView.setVisibility(View.GONE);
            mAudioOnlyView.setVisibility(View.VISIBLE);
        }
        else {
            mAudioOnlyView.setVisibility(View.GONE);
            mRemoteView.setVisibility(View.VISIBLE);
        }
    }

    //TextChat Fragment listener events
    @Override
    public void onNewSentMessage(ChatMessage message) {
        Log.i(LOG_TAG, "New sent message");
    }

    @Override
    public void onNewReceivedMessage(ChatMessage message) {
        Log.i(LOG_TAG, "New received message");
    }

    @Override
    public void onTextChatError(String error) {
        Log.i(LOG_TAG, "Error on text chat " + error);
    }

    @Override
    public void onClosed() {
        Log.i(LOG_TAG, "OnClosed text-chat");
        mTextChatContainer.setVisibility(View.GONE);
        showAVCall(true);
        restartTextChatLayout(true);
    }

    @Override
    public void onRestarted() {
        Log.i(LOG_TAG, "OnRestarted text-chat");
    }

    //Private methods
    private void initPreviewFragment() {
        mPreviewFragment = new PreviewControlFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.actionbar_preview_fragment_container, mPreviewFragment).commit();
    }

    private void initRemoteFragment() {
        mRemoteFragment = new RemoteControlFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.actionbar_remote_fragment_container, mRemoteFragment).commit();
    }

    private void initCameraFragment() {
        mCameraFragment = new PreviewCameraFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.camera_preview_fragment_container, mCameraFragment).commit();
    }

    private void initTextChatFragment() {
        mTextChatFragment = TextChatFragment.newInstance(sOTWrapper, OpenTokConfig.API_KEY);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.textchat_fragment_container, mTextChatFragment).commit();
        try {
            mTextChatFragment.setSenderAlias("Tokboxer");
            mTextChatFragment.setMaxTextLength(140);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mTextChatFragment.setListener(this);
    }

    //cleans views and controls
    private void cleanViewsAndControls() {

        if (isRemote) {
            mRemoteView = null;
            isRemote = false;
            setRemoteView(null);
        }
        if (isLocal) {
            isLocal = false;
            setLocalView(null);
        }

        if (mPreviewFragment != null)
            mPreviewFragment.restart();
        if (mRemoteFragment != null)
            mRemoteFragment.restart();
        if (mTextChatFragment != null) {
            restartTextChatLayout(true);
            mTextChatFragment.restart();
            mTextChatContainer.setVisibility(View.GONE);
        }
    }

    private void showAVCall(boolean show) {
        if (show) {
            mActionBarContainer.setVisibility(View.VISIBLE);
            mPreviewViewContainer.setVisibility(View.VISIBLE);
            mRemoteViewContainer.setVisibility(View.VISIBLE);
            mCameraFragmentContainer.setVisibility(View.VISIBLE);
        } else {
            mActionBarContainer.setVisibility(View.GONE);
            mPreviewViewContainer.setVisibility(View.GONE);
            mRemoteViewContainer.setVisibility(View.GONE);
            mCameraFragmentContainer.setVisibility(View.GONE);
        }
    }

    private void restartTextChatLayout(boolean restart) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mTextChatContainer.getLayoutParams();

        if (restart) {
            //restart to the original size
            params.width = RelativeLayout.LayoutParams.MATCH_PARENT;
            params.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        } else {
            //go to the minimized size
            params.height = dpToPx(40);
            params.addRule(RelativeLayout.ABOVE, R.id.actionbar_preview_fragment_container);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
        }
        mTextChatContainer.setLayoutParams(params);
    }

    /**
     * Converts dp to real pixels, according to the screen density.
     *
     * @param dp A number of density-independent pixels.
     * @return The equivalent number of real pixels.
     */
    private int dpToPx(int dp) {
        double screenDensity = this.getResources().getDisplayMetrics().density;
        return (int) (screenDensity * (double) dp);
    }

    private void addLogEvent(String action, String variation) {
        if (mAnalytics != null) {
            mAnalytics.logEvent(action, variation);
        }
    }

    @Override
    public void onConnected(Object o, int participantsNumber, String connId, String data) throws UIListenerException {
        Log.i(LOG_TAG, "onConnected");
        isConnected = true;
        mProgressDialog.dismiss();
    }

    @Override
    public void onDisconnected(Object o, int participantsNumber, String connId, String data) throws UIListenerException {
        Log.i(LOG_TAG, "onDisconnected");
        cleanViewsAndControls();
    }

    @Override
    public void onPreviewViewReady(Object o, View localView) throws UIListenerException {
        Log.i(LOG_TAG, "onPreviewViewReady");
        setLocalView(localView);
    }

    public void setLocalView(View localView){
        if (localView != null) {
            mPreviewViewContainer.removeAllViews();
            isLocal = true;
            layoutParamsPreview = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

            if (isRemote) {
                layoutParamsPreview.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
                        RelativeLayout.TRUE);
                layoutParamsPreview.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
                        RelativeLayout.TRUE);
                layoutParamsPreview.width = (int) getResources().getDimension(R.dimen.preview_width);
                layoutParamsPreview.height = (int) getResources().getDimension(R.dimen.preview_height);
                layoutParamsPreview.rightMargin = (int) getResources().getDimension(R.dimen.preview_rightMargin);
                layoutParamsPreview.bottomMargin = (int) getResources().getDimension(R.dimen.preview_bottomMargin);
            }
            mPreviewViewContainer.addView(localView, layoutParamsPreview);
        }
        else {
            mPreviewViewContainer.removeAllViews();
        }
    }

    @Override
    public void onPreviewViewDestroyed(Object o, View localView) throws UIListenerException {
        Log.i(LOG_TAG, "onPreviewViewDestroyed");
        setLocalView(null);
    }

    @Override
    public void onRemoteViewReady(Object o, View remoteView, String remoteId, String data) throws UIListenerException {
        Log.i(LOG_TAG, "onRemoteViewReady");
        if (isCallInProgress()) {
            setRemoteView(remoteView);
        }
        mRemoteView = remoteView;
        isRemote = true;
    }

    public void setRemoteView(View remoteView){
        Log.i(LOG_TAG, "setRemoteView");
        if (mPreviewViewContainer.getChildCount() > 0) {
            setLocalView(mPreviewViewContainer.getChildAt(0)); //main preview view
        }

        if ( remoteView != null ){
            //show remote view
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    this.getResources().getDisplayMetrics().widthPixels, this.getResources()
                    .getDisplayMetrics().heightPixels);
            mRemoteViewContainer.removeView(remoteView);
            mRemoteViewContainer.addView(remoteView, layoutParams);
            mRemoteViewContainer.setClickable(true);
        }
        else {
            Log.i(LOG_TAG, "remove remote view");
            mRemoteViewContainer.removeViewAt(mRemoteViewContainer.getChildCount()-1);
            mRemoteViewContainer.setClickable(false);
            mAudioOnlyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRemoteViewDestroyed(Object o, View remoteView, String remoteId) throws UIListenerException {
        Log.i(LOG_TAG, "onRemoteViewDestroyed");
        //update preview
        setRemoteView(null);
        mRemoteView = null;
    }

    @Override
    public void onRemoteJoined(Object o, String remoteId) throws UIListenerException {
        Log.i(LOG_TAG, "onRemoteJoined");
        this.mRemoteId = remoteId;
        isRemote = true;
    }

    @Override
    public void onRemoteLeft(Object o, String remoteId) throws UIListenerException {
        Log.i(LOG_TAG, "onRemoteLeft");
        isRemote = false;
        mRemoteId = null;
    }

    @Override
    public void onRemoteVideoChange(Object o, String remoteId, String reason, boolean videoActive, boolean subscribed) throws UIListenerException {
        Log.i(LOG_TAG, "OnRemoteVideo Change");
        if (isCallInProgress) {
            if (reason.equals("quality")) {  //network quality alert
                mAlert.setBackgroundResource(R.color.quality_alert);
                mAlert.setTextColor(this.getResources().getColor(R.color.white));
                mAlert.bringToFront();
                mAlert.setVisibility(View.VISIBLE);
                mAlert.postDelayed(new Runnable() {
                    public void run() {
                        mAlert.setVisibility(View.GONE);
                    }
                }, 7000);
            }

            if (!videoActive) {
                onAudioOnly(true); //video is not active
            } else {
                onAudioOnly(false);
            }
        }
    }

    @Override
    public void onCameraChanged(Object o) throws UIListenerException {
        Log.i(LOG_TAG, "onCameraChanged");
    }

    @Override
    public void onError(Object o, OpentokError error) throws UIListenerException {
        Log.i(LOG_TAG, "OnError "+error.getErrorCode()+"-"+error.getMessage());

        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        sOTWrapper.disconnect(); //end communication
        mProgressDialog.dismiss();
        cleanViewsAndControls(); //restart views
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isCallInProgress() {
        return isCallInProgress;
    }

    public OTWrapper getsOTWrapper() {
        return sOTWrapper;
    }

}
