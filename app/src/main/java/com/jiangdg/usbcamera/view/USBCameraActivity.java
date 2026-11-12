package com.jiangdg.usbcamera.view;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.jiangdg.usbcamera.R;
import com.jiangdg.usbcamera.ai.AiSettings;
import com.jiangdg.usbcamera.ai.DeepSeekVisionClient;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.application.MyApplication;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.encoder.RecordParams;
import com.serenegiant.usb.widget.CameraViewInterface;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * UVCCamera use demo
 * <p>
 * Created by jiangdongguo on 2017/9/30.
 */

public class USBCameraActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback {
    private static final String TAG = "Debug";
    @BindView(R.id.camera_view)
    public View mTextureView;
    @BindView(R.id.toolbar)
    public Toolbar mToolbar;
    @BindView(R.id.seekbar_brightness)
    public SeekBar mSeekBrightness;
    @BindView(R.id.seekbar_contrast)
    public SeekBar mSeekContrast;
    @BindView(R.id.switch_rec_voice)
    public Switch mSwitchVoice;
    @BindView(R.id.switch_ai_player)
    public Switch mSwitchAiPlayer;
    @BindView(R.id.tv_ai_status)
    public TextView mTvAiStatus;

    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private AlertDialog mDialog;

    private boolean isRequest;
    private boolean isPreview;

    private TextToSpeech mTts;
    private volatile boolean mTtsReady;
    private ExecutorService mAiExecutor;

    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
                // initialize seekbar
                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                            mSeekBrightness.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_BRIGHTNESS));
                            mSeekContrast.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_CONTRAST));
                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        ButterKnife.bind(this);
        initView();
        mAiExecutor = Executors.newSingleThreadExecutor();
        initTextToSpeech();

        // step.1 initialize UVCCameraHelper
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);

        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] nv21Yuv) {
                Log.d(TAG, "onPreviewResult: "+nv21Yuv.length);
            }
        });
    }

    private void initView() {
        setSupportActionBar(mToolbar);

        mSeekBrightness.setMax(100);
        mSeekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS,progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mSeekContrast.setMax(100);
        mSeekContrast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_CONTRAST,progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // This control intentionally does not play any audio. It represents
        // the AI observation player state; each user toggle triggers one
        // capture and one DeepSeek request.
        mSwitchAiPlayer.setChecked(true);
        updateAiPlayerLabel(true);
        mSwitchAiPlayer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateAiPlayerLabel(isChecked);
            if (mTts != null) {
                mTts.stop();
            }
            triggerAiAnalysis();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mTts != null) {
            mTts.stop();
        }
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toobar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_takepic:
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("sorry,camera open failed");
                    return super.onOptionsItemSelected(item);
                }
                String picPath = FileUtils.ROOT_PATH + "/" + MyApplication.DIRECTORY_NAME +"/images/"
                        + System.currentTimeMillis() + UVCCameraHelper.SUFFIX_JPEG;

                mCameraHelper.capturePicture(picPath, new AbstractUVCCameraHandler.OnCaptureListener() {
                    @Override
                    public void onCaptureResult(String path) {
                        if(TextUtils.isEmpty(path)) {
                            return;
                        }
                        new Handler(getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(USBCameraActivity.this, "save path:"+path, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });

                break;
            case R.id.menu_recording:
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("sorry,camera open failed");
                    return super.onOptionsItemSelected(item);
                }
                if (!mCameraHelper.isPushing()) {
                    String videoPath = FileUtils.ROOT_PATH + "/" + MyApplication.DIRECTORY_NAME +"/videos/" + System.currentTimeMillis()
                            + UVCCameraHelper.SUFFIX_MP4;

//                    FileUtils.createfile(FileUtils.ROOT_PATH + "test666.h264");
                    // if you want to record,please create RecordParams like this
                    RecordParams params = new RecordParams();
                    params.setRecordPath(videoPath);
                    params.setRecordDuration(0);                        // auto divide saved,default 0 means not divided
                    params.setVoiceClose(mSwitchVoice.isChecked());    // is close voice

                    params.setSupportOverlay(true); // overlay only support armeabi-v7a & arm64-v8a
                    mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                        @Override
                        public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                            // type = 1,h264 video stream
                            if (type == 1) {
                                FileUtils.putFileStream(data, offset, length);
                            }
                            // type = 0,aac audio stream
                            if(type == 0) {

                            }
                        }

                        @Override
                        public void onRecordResult(String videoPath) {
                            if(TextUtils.isEmpty(videoPath)) {
                                return;
                            }
                            new Handler(getMainLooper()).post(() -> Toast.makeText(USBCameraActivity.this, "save videoPath:"+videoPath, Toast.LENGTH_SHORT).show());
                        }
                    });
                    // if you only want to push stream,please call like this
                    // mCameraHelper.startPusher(listener);
                    showShortMsg("start record...");
                    mSwitchVoice.setEnabled(false);
                } else {
                    FileUtils.releaseFile();
                    mCameraHelper.stopPusher();
                    showShortMsg("stop record...");
                    mSwitchVoice.setEnabled(true);
                }
                break;
            case R.id.menu_resolution:
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("sorry,camera open failed");
                    return super.onOptionsItemSelected(item);
                }
                showResolutionListDialog();
                break;
            case R.id.menu_focus:
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("sorry,camera open failed");
                    return super.onOptionsItemSelected(item);
                }
                mCameraHelper.startCameraFoucs();
                break;
            case R.id.menu_ai_settings:
                showAiSettingsDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateAiPlayerLabel(boolean isPlaying) {
        mSwitchAiPlayer.setText(isPlaying ? "AI 播放器：开" : "AI 播放器：关");
    }

    private void showAiStatus(String message) {
        mTvAiStatus.setVisibility(View.VISIBLE);
        mTvAiStatus.setText(message);
    }

    private void triggerAiAnalysis() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showAiStatus("摄像头未打开，无法拍照");
            showShortMsg("请先连接并打开 USB 摄像头");
            return;
        }

        final AiSettings.Config config = AiSettings.load(this);
        if (TextUtils.isEmpty(config.apiKey)) {
            showAiStatus("请先点击菜单中的“AI设置”填写 API Key");
            showAiSettingsDialog();
            return;
        }

        File captureDir = new File(getCacheDir(), "ai_captures");
        if (!captureDir.exists() && !captureDir.mkdirs()) {
            showAiStatus("无法创建临时拍照目录");
            return;
        }
        final File imageFile = new File(captureDir,
                "ai_capture_" + System.currentTimeMillis() + ".jpg");

        showAiStatus("正在拍照...");
        mCameraHelper.capturePicture(imageFile.getAbsolutePath(), path -> runOnUiThread(() -> {
            if (TextUtils.isEmpty(path)) {
                showAiStatus("拍照失败");
                return;
            }
            final ExecutorService executor = mAiExecutor;
            if (executor == null) {
                showAiStatus("AI 请求队列已关闭，请重新打开页面");
                return;
            }
            showAiStatus("正在请求 DeepSeek...");
            try {
                executor.execute(() -> requestDeepSeekAnalysis(new File(path), config));
            } catch (RejectedExecutionException e) {
                showAiStatus("AI 请求队列已关闭，请重新打开页面");
            }
        }));
    }

    private void requestDeepSeekAnalysis(File imageFile, AiSettings.Config config) {
        try {
            String answer = DeepSeekVisionClient.analyze(imageFile, config);
            runOnUiThread(() -> {
                showAiStatus(answer);
                speakAiResult(answer);
            });
        } catch (Exception e) {
            Log.e(TAG, "DeepSeek request failed", e);
            String message = e.getMessage();
            if (TextUtils.isEmpty(message)) {
                message = e.getClass().getSimpleName();
            }
            final String errorMessage = "AI 请求失败：" + message;
            runOnUiThread(() -> {
                showAiStatus(errorMessage);
                showShortMsg("AI 请求失败，请查看界面提示");
            });
        } finally {
            if (imageFile.exists() && !imageFile.delete()) {
                Log.w(TAG, "Failed to delete temporary AI image: " + imageFile);
            }
        }
    }

    private void initTextToSpeech() {
        mTts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                mTtsReady = false;
                Log.w(TAG, "TextToSpeech initialization failed: " + status);
                return;
            }
            int languageResult = mTts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (languageResult == TextToSpeech.LANG_MISSING_DATA
                    || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                languageResult = mTts.setLanguage(Locale.getDefault());
            }
            mTtsReady = languageResult != TextToSpeech.LANG_MISSING_DATA
                    && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
            if (mTtsReady) {
                mTts.setSpeechRate(1.0f);
                mTts.setPitch(1.0f);
            } else {
                Log.w(TAG, "No supported TTS language is available");
            }
        });
    }

    private void speakAiResult(String text) {
        if (mTts == null || !mTtsReady || TextUtils.isEmpty(text)) {
            showShortMsg("系统语音播报不可用，请检查手机 TTS 语音包");
            return;
        }
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "deepseek_ai_result");
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "deepseek_ai_result");
    }

    private void showAiSettingsDialog() {
        AiSettings.Config config = AiSettings.load(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_settings, null);
        final EditText apiKeyInput = dialogView.findViewById(R.id.et_ai_api_key);
        final EditText promptInput = dialogView.findViewById(R.id.et_ai_prompt);
        final EditText modelInput = dialogView.findViewById(R.id.et_ai_model);
        final EditText endpointInput = dialogView.findViewById(R.id.et_ai_endpoint);

        apiKeyInput.setText(config.apiKey);
        promptInput.setText(config.prompt);
        modelInput.setText(config.model);
        endpointInput.setText(config.endpoint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("DeepSeek AI 设置")
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String apiKey = apiKeyInput.getText().toString().trim();
                    String prompt = promptInput.getText().toString().trim();
                    String model = modelInput.getText().toString().trim();
                    String endpoint = endpointInput.getText().toString().trim();

                    if (apiKey.isEmpty()) {
                        apiKeyInput.setError("请输入 DeepSeek API Key");
                        apiKeyInput.requestFocus();
                        return;
                    }
                    if (prompt.isEmpty()) {
                        promptInput.setError("请输入提示词");
                        promptInput.requestFocus();
                        return;
                    }
                    if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://")) {
                        endpointInput.setError("API 地址必须以 https:// 或 http:// 开头");
                        endpointInput.requestFocus();
                        return;
                    }

                    AiSettings.save(this, new AiSettings.Config(apiKey, prompt, model, endpoint));
                    dialog.dismiss();
                    showShortMsg("AI 设置已保存");
                }));
        dialog.show();
    }

    private void showResolutionListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(USBCameraActivity.this);
        View rootView = LayoutInflater.from(USBCameraActivity.this).inflate(R.layout.layout_dialog_list, null);
        ListView listView = (ListView) rootView.findViewById(R.id.listview_dialog);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(USBCameraActivity.this, android.R.layout.simple_list_item_1, getResolutionList());
        if (adapter != null) {
            listView.setAdapter(adapter);
        }
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened())
                    return;
                final String resolution = (String) adapterView.getItemAtPosition(position);
                String[] tmp = resolution.split("x");
                if (tmp != null && tmp.length >= 2) {
                    int widht = Integer.valueOf(tmp[0]);
                    int height = Integer.valueOf(tmp[1]);
                    mCameraHelper.updateResolution(widht, height);
                }
                mDialog.dismiss();
            }
        });

        builder.setView(rootView);
        mDialog = builder.create();
        mDialog.show();
    }

    // example: {640x480,320x240,etc}
    private List<String> getResolutionList() {
        List<Size> list = mCameraHelper.getSupportedPreviewSizes();
        List<String> resolutions = null;
        if (list != null && list.size() != 0) {
            resolutions = new ArrayList<>();
            for (Size size : list) {
                if (size != null) {
                    resolutions.add(size.width + "x" + size.height);
                }
            }
        }
        return resolutions;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FileUtils.releaseFile();
        if (mAiExecutor != null) {
            mAiExecutor.shutdownNow();
            mAiExecutor = null;
        }
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
            mTtsReady = false;
        }
        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("取消操作");
        }
    }

    public boolean isCameraOpened() {
        return mCameraHelper.isCameraOpened();
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }
}
