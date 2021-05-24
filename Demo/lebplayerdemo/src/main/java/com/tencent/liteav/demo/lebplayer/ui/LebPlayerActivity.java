package com.tencent.liteav.demo.lebplayer.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.tencent.liteav.basic.log.TXCLog;
import com.tencent.liteav.demo.lebplayer.R;
import com.tencent.liteav.demo.lebplayer.ui.setting.AVSettingConfig;
import com.tencent.liteav.demo.lebplayer.ui.setting.ErrorDialog;
import com.tencent.liteav.demo.lebplayer.ui.setting.PlaySetting;
import com.tencent.liteav.demo.lebplayer.ui.widget.MainItemRenderView;
import com.tencent.live2.V2TXLiveCode;
import com.tencent.live2.V2TXLiveDef;
import com.tencent.live2.V2TXLiveDef.V2TXLiveFillMode;
import com.tencent.live2.V2TXLivePlayer;
import com.tencent.live2.V2TXLivePlayerObserver;
import com.tencent.live2.impl.V2TXLivePlayerImpl;

import java.util.ArrayList;
import java.util.List;

public class LebPlayerActivity extends AppCompatActivity {

    private static final String TAG = "LebPlayerActivity";

    private static final int REQ_PERMISSION_CODE = 0x1000;
    private static final int PLAY_ERROR_TIMEOUT  = 5000;

    private static final int PLAY_STATE_INIT = 10;
    private static final int PLAY_STATE_STARTING  = 20;
    private static final int PLAY_STATE_STARTED  = 30;
    private static final int PLAY_STATE_LOADING  = 40;
    private static final int PLAY_STATE_PLAYING  = 50;
    private static final int PLAY_STATE_PAUSE  = 60;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private PlaySetting mPlaySetting;
    
    private MainItemRenderView mPlayerView;
    private int mPlayerState = PLAY_STATE_INIT;
    private String mPlayURL;
    private V2TXLivePlayer mLebPlayer;
    private boolean mIsShowDebugView;
    private boolean mIsMuteVideo;
    private boolean mIsMuteAudio;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TXCLog.i(TAG, "onCreate");
        setContentView(R.layout.lebplayer_activity);
        AVSettingConfig.getInstance().fillMode = V2TXLiveFillMode.V2TXLiveFillModeFit;

        checkPermission();

        findViewById(R.id.lebplayer_ibtn_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mPlayerView = (MainItemRenderView) findViewById(R.id.lebplayer_render_view);
        mPlayURL = getIntent().getStringExtra(Constants.INTENT_URL);

        resetPlayerView();

        startPlay();
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(LebPlayerActivity.this,
                        (String[]) permissions.toArray(new String[0]),
                        REQ_PERMISSION_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TXCLog.i(TAG, "onDestroy");
        resetPlayer();
        mPlayerView = null;
        mLebPlayer = null;
        mPlayURL = null;
    }

    private void resetPlayerView() {
        mPlayerState = PLAY_STATE_INIT;
        if (mPlayerView != null) {
            mPlayerView.updatePlayerStatus(false);
            mPlayerView.dismissLoading();
            mPlayerView.setVolumeProgress(0);
        }
        if (mIsShowDebugView && mLebPlayer != null) {
            mLebPlayer.showDebugView(true);
        }
    }

    private void startPlay() {
        if (TextUtils.isEmpty(mPlayURL) || !mPlayURL.startsWith("webrtc://")) {
            Toast.makeText(LebPlayerActivity.this, getString(R.string.lebplayer_toast_error_url_is_null), Toast.LENGTH_SHORT).show();
            resetPlayer();
            return;
        }

        TXCLog.i(TAG, "[Player] startPlay url " + mPlayURL);
        mLebPlayer = new V2TXLivePlayerImpl(LebPlayerActivity.this);

        mPlayerView.setSwitchListener(new PlayerViewCallback());
        mPlayerView.showLoading();
        mPlayerView.updatePlayerStatus(true);

        mLebPlayer.setRenderView(mPlayerView.getCloudView());
//        mLebPlayer.setRenderView(mPlayerView.getSurfaceView());
//        mLebPlayer.setRenderView(mPlayerView.getTextureView());
        mLebPlayer.setRenderRotation(AVSettingConfig.getInstance().rotation);
        mLebPlayer.setRenderFillMode(AVSettingConfig.getInstance().fillMode);

        mLebPlayer.setPlayoutVolume(100);
        mLebPlayer.enableVolumeEvaluation(200);
        mLebPlayer.setObserver(new MyPlayerObserver());
        final int result = mLebPlayer.startPlay(mPlayURL);
        if (result != 0) {
            if (result == V2TXLiveCode.V2TXLIVE_ERROR_REFUSED) {
                Toast.makeText(LebPlayerActivity.this, getString(R.string.lebplayer_toast_error_player_stream_id_duplicate), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(LebPlayerActivity.this, getString(R.string.lebplayer_toast_player_failure), Toast.LENGTH_SHORT).show();
            }
            TXCLog.e(TAG, "[Player] startPlay failed, result " + result);
            resetPlayer();
            return;
        }
        mPlayerState = PLAY_STATE_STARTING;

        updateVideoStatus(false);
        updateAudioStatus(false);

        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mPlayerState < PLAY_STATE_STARTED) {
                    Toast.makeText(LebPlayerActivity.this, getString(R.string.lebplayer_toast_player_failure), Toast.LENGTH_SHORT).show();
                    TXCLog.e(TAG, "[Player] play error, timeout to receive first video");
                    resetPlayer();
                }
            }
        }, PLAY_ERROR_TIMEOUT); // 5s内没有收到首帧视频或者音频，认为播放异常
    }

    // player
    private class MyPlayerObserver extends V2TXLivePlayerObserver {

        @Override
        public void onWarning(V2TXLivePlayer player, int code, String msg, Bundle extraInfo) {
            TXCLog.w(TAG, "[Player] onWarning: player-" + player + " code-" + code + " msg-" + msg + " info-" + extraInfo);
        }

        @Override
        public void onError(V2TXLivePlayer player, int code, String msg, Bundle extraInfo) {
            TXCLog.e(TAG, "[Player] onError: player-" + player + " code-" + code + " msg-" + msg + " info-" + extraInfo);
            Toast.makeText(LebPlayerActivity.this, msg, Toast.LENGTH_SHORT).show();
            ErrorDialog.showMsgDialog(LebPlayerActivity.this, "onError errorCode: " + code);
        }

        @Override
        public void onSnapshotComplete(V2TXLivePlayer v2TXLivePlayer, Bitmap bitmap) {
            if (mPlaySetting != null) {
                mPlaySetting.setSnapshotImage(bitmap);
            }
        }

        @Override
        public void onVideoPlayStatusUpdate(V2TXLivePlayer player, V2TXLiveDef.V2TXLivePlayStatus status, V2TXLiveDef.V2TXLiveStatusChangeReason reason, Bundle bundle) {
            TXCLog.i(TAG, "[Player] onVideoPlayStatusUpdate: player-" + player + ", status-" + status + ", reason-" + reason);
            onPlayStatusUpdate(player, status, reason, bundle, false);
        }

        @Override
        public void onAudioPlayStatusUpdate(V2TXLivePlayer player, V2TXLiveDef.V2TXLivePlayStatus status, V2TXLiveDef.V2TXLiveStatusChangeReason reason, Bundle bundle) {
            TXCLog.i(TAG, "[Player] onAudioPlayStatusUpdate: player-" + player + ", status-" + status + ", reason-" + reason);
            onPlayStatusUpdate(player, status, reason, bundle, true);
        }

        private void onPlayStatusUpdate(V2TXLivePlayer player,
                                        V2TXLiveDef.V2TXLivePlayStatus status,
                                        V2TXLiveDef.V2TXLiveStatusChangeReason reason,
                                        Bundle bundle,
                                        boolean audio) {
            switch (status) {
                case V2TXLivePlayStatusPlaying:
                    if (mPlayerView == null) {
                        break;
                    }
                    mPlayerState = PLAY_STATE_PLAYING;
                    mPlayerView.dismissLoading();
                    break;
                case V2TXLivePlayStatusLoading:
                    if (mPlayerView == null) {
                        break;
                    }
                    mPlayerState = PLAY_STATE_LOADING;
                    mPlayerView.showLoading();
                    break;
                case V2TXLivePlayStatusStopped:
                    if (mPlayerView == null) {
                        break;
                    }
                    if (audio) {
                        break;
                    }
                    mPlayerState = PLAY_STATE_INIT;
                    mPlayerView.dismissLoading();
                    if (reason == V2TXLiveDef.V2TXLiveStatusChangeReason.V2TXLiveStatusChangeReasonRemoteStopped) {
                        Toast.makeText(LebPlayerActivity.this, "disconnect!", Toast.LENGTH_LONG).show();
                        mPlayerView.updatePlayerStatus(false);
                        resetPlayer();
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onPlayoutVolumeUpdate(V2TXLivePlayer player, int volume) {
            if (mPlayerView != null) {
                mPlayerView.setVolumeProgress(volume);
            }
        }

        @Override
        public void onStatisticsUpdate(V2TXLivePlayer player, V2TXLiveDef.V2TXLivePlayerStatistics statistics) {
            if (mPlayerView != null) {
                mPlayerView.setVolumeProgress(0);
            }
//            TXCLog.i(TAG, "[Player] onStatisticsUpdate: statistics cpu-" + statistics.appCpu
//                    + " syscpu-" + statistics.systemCpu
//                    + " width-" + statistics.width
//                    + " height-" + statistics.height
//                    + " fps-" + statistics.fps
//                    + " video bitrate-" + statistics.videoBitrate
//                    + " audio bitrate-" + statistics.audioBitrate
//            );
        }

        @Override
        public void onRenderVideoFrame(V2TXLivePlayer player, V2TXLiveDef.V2TXLiveVideoFrame videoFrame) {

        }
    }

    private void updateVideoStatus(boolean isMuteVideo) {
        mIsMuteVideo = isMuteVideo;
        mPlayerView.updateMuteVideoStatus(isMuteVideo);
        if (isMuteVideo) {
            mPlayerView.updateMuteVideoStatus(isMuteVideo);
            if (mLebPlayer != null) {
                mLebPlayer.pauseVideo();
            }
        } else {
            mPlayerView.updateMuteVideoStatus(isMuteVideo);
            if (mLebPlayer != null) {
                mLebPlayer.resumeVideo();
            }
        }
    }

    private void updateAudioStatus(boolean isMuteAudio) {
        mIsMuteAudio = isMuteAudio;
        mPlayerView.updateMuteAudioStatus(isMuteAudio);
        if (isMuteAudio) {
            if (mLebPlayer != null) {
                mLebPlayer.pauseAudio();
            }
        } else {
            if (mLebPlayer != null) {
                mLebPlayer.resumeAudio();
            }
        }
    }

    private class PlayerViewCallback implements MainItemRenderView.ILiveRenderViewSwitchCallback {

        @Override
        public void onMuteVideo(View view) {
            updateVideoStatus(!mIsMuteVideo);
        }

        @Override
        public void onMuteAudio(View view) {
            updateAudioStatus(!mIsMuteAudio);
        }

        @Override
        public void onShowSetting() {
            mPlaySetting = new PlaySetting();
            mPlaySetting.setLivePlayer(mLebPlayer);
            mPlaySetting.show(getFragmentManager(), "remote_config_fragment");
        }

        @Override
        public void onShowDebugView(View view) {
            if (!mIsShowDebugView) {
                mIsShowDebugView = true;
                if (mLebPlayer != null) {
                    mLebPlayer.showDebugView(true);
                }
            } else {
                mIsShowDebugView = false;
                if (mLebPlayer != null) {
                    mLebPlayer.showDebugView(false);
                }
            }
        }

        @Override
        public void onStart(View view) {
            TXCLog.i(TAG, "[Player] start btn clicked: mPlayerState-" + mPlayerState);
            if (mPlayerState == PLAY_STATE_INIT) {
                mPlayerView.updatePlayerStatus(true);
                startPlay();
            } else {
                mPlayerView.updatePlayerStatus(false);
                resetPlayer();
            }
        }
    }

    private void resetPlayer() {
        TXCLog.i(TAG, "[Player] resetPlayer: player-" + this);
        mPlayerState = PLAY_STATE_INIT;
        mIsShowDebugView = false;
        mIsMuteAudio = false;
        mIsMuteVideo = false;
        if (mLebPlayer != null) {
            mLebPlayer.showDebugView(false);
            mLebPlayer.stopPlay();
        }
        resetPlayerView();
        mHandler.removeCallbacksAndMessages(null);
    }

}
