package com.anewtech.phone.speechsample;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.qihancloud.opensdk.base.TopBaseActivity;
import com.qihancloud.opensdk.beans.FuncConstant;
import com.qihancloud.opensdk.function.beans.FaceRecognizeBean;
import com.qihancloud.opensdk.function.beans.SpeakOption;
import com.qihancloud.opensdk.function.beans.StreamOption;
import com.qihancloud.opensdk.function.beans.speech.Grammar;
import com.qihancloud.opensdk.function.unit.MediaManager;
import com.qihancloud.opensdk.function.unit.SpeechManager;
import com.qihancloud.opensdk.function.unit.interfaces.media.FaceRecognizeListener;
import com.qihancloud.opensdk.function.unit.interfaces.media.MediaStreamListener;
import com.qihancloud.opensdk.function.unit.interfaces.speech.RecognizeListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends TopBaseActivity implements SurfaceHolder.Callback {

    private final static String TAG = MainActivity.class.getSimpleName();
    SpeechManager sm;
    CountDownTimer timer;

    SurfaceView svMedia;
    ImageView ivCapture;
    TextView tx;
    TextView showName;

    private MediaManager mediaManager;
    MediaCodec mediaCodec;

    long decodeTimeout = 16000;
    MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
    ByteBuffer[] videoInputBuffers;

    String checkName = "";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(128, 128);
        setContentView(R.layout.activity_main);

        sm = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
        tx = findViewById(R.id.showtext);
        showName = findViewById(R.id.showName);
        ivCapture = findViewById(R.id.iv_capture);
        svMedia = findViewById(R.id.sv_media);

        mediaManager = (MediaManager) getUnitManager(FuncConstant.MEDIA_MANAGER);
        svMedia.getHolder().addCallback(this);

        final SpeakOption speakOption = new SpeakOption();
        speakOption.setLanguageType(SpeakOption.LAG_ENGLISH_US);
        speakOption.setSpeed(50);
        speakOption.setIntonation(70);

        initListener();

        Toast.makeText(MainActivity.this, "Check", Toast.LENGTH_SHORT).show();
        sm.setOnSpeechListener(new RecognizeListener() {
            @Override
            public boolean onRecognizeResult(Grammar grammar) {
                //timer.cancel();
                Toast.makeText(MainActivity.this, grammar.getText(), Toast.LENGTH_SHORT).show();
                sm.startSpeak(grammar.getText(),speakOption);
                tx.setText(grammar.getText());
                return true;
            }

            @Override
            public void onRecognizeVolume(int i) {

            }
        });

        timer = new CountDownTimer(30000, 1000) {
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                //sm.startSpeak("Welcome to Anewtech Systems. How may I help you today?",speakOption);
                sm.doWakeUp();
                try{
                    timer.start();
                }catch(Exception ex){
                    Log.e("Error", "Error: " + ex.toString());
                }
            }
        }.start();

        showName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(charSequence.length() != 0 && !checkName.contains(charSequence.toString())){
                    sm.startSpeak(charSequence.toString());
                    checkName = charSequence.toString();
                }

            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void initListener() {
        mediaManager.setMediaListener(new MediaStreamListener() {
            @Override
            public void getVideoStream(byte[] bytes) {
                showViewData(ByteBuffer.wrap(bytes));
            }

            @Override
            public void getAudioStream(byte[] bytes) {
            }
        });
        mediaManager.setMediaListener(new FaceRecognizeListener() {
            @Override
            public void recognizeResult(List<FaceRecognizeBean> list) {
                StringBuilder sb = new StringBuilder();
                for (FaceRecognizeBean bean : list) {
                    sb.append(new Gson().toJson(bean));
                    sb.append("\n");
                }
                //showName.setText(sb.toString());
                new GreetHimOrHer().execute(sb.toString());
            }
        });
    }

    private class GreetHimOrHer extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... strings) {
            for( int i = 0 ; i < strings.length; i++){
                try{
                    JSONObject mObj = new JSONObject(strings[i]);
                    String myName = mObj.getString("user");
                    return myName;
                }catch (JSONException ex){
                    Log.e(TAG, ex.toString());
                }
            }
            return "Human Being";
        }
        @Override
        protected void onPostExecute(String s) {
            showName.setText("Hello " + s);
        }

    }



    private void showViewData(ByteBuffer sampleData) {
        try {
            int inIndex = mediaCodec.dequeueInputBuffer(decodeTimeout);
            if (inIndex >= 0) {
                ByteBuffer buffer = videoInputBuffers[inIndex];
                int sampleSize = sampleData.limit();
                buffer.clear();
                buffer.put(sampleData);
                buffer.flip();
                mediaCodec.queueInputBuffer(inIndex, 0, sampleSize, 0, 0);
            }
            int outputBufferId = mediaCodec.dequeueOutputBuffer(videoBufferInfo, decodeTimeout);
            if (outputBufferId >= 0) {
                mediaCodec.releaseOutputBuffer(outputBufferId, true);
            } else {
                Log.e(TAG, "dequeueOutputBuffer() error");
            }

        } catch (Exception e) {
            Log.e(TAG, "发生错误", e);
        }
    }



    @Override
    protected void onMainServiceConnected() {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        //设置参数并打开媒体流
        StreamOption streamOption = new StreamOption();
        streamOption.setChannel(StreamOption.MAIN_STREAM);
        streamOption.setDecodType(StreamOption.HARDWARE_DECODE);
        streamOption.setJustIframe(false);
        mediaManager.openStream(streamOption);
        //配置MediaCodec
        startDecoding(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //关闭媒体流
        mediaManager.closeStream();
        stopDecoding();
    }

    /**
     * 初始化视频编解码器
     *
     * @param surface
     */
    private void startDecoding(Surface surface) {
        if (mediaCodec != null) {
            return;
        }
        try {
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            MediaFormat format = MediaFormat.createVideoFormat(
                    "video/avc", 1280, 720);
            mediaCodec.configure(format, surface, null, 0);
            mediaCodec.start();
            videoInputBuffers = mediaCodec.getInputBuffers();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 结束视频编解码器
     */
    private void stopDecoding() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
            Log.i(TAG, "stopDecoding");
        }
        videoInputBuffers = null;
    }
}
