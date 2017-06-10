package com.praveen.cameraview;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.praveen.cameraview.Utils.Utils;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Date;
import java.util.Map;

import static android.hardware.Camera.getCameraInfo;


/**
 * QRCodeReaderView Class which uses ZXING lib and let you easily integrate a QR decoder view.
 * Take some classes and made some modifications in the original ZXING - Barcode Scanner project.
 * <p>
 * Created by Praveen Kanwar on 06/06/17.
 * Copyright (c) 2017 .
 */
public class QRCodeReaderView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback
    {

        public interface OnQRCodeReadListener
            {
                void onQRCodeRead(String text, PointF[] points);

                void cameraAvailable(Camera mCamera);
            }

        private OnQRCodeReadListener mOnQRCodeReadListener;

        private static final String TAG = QRCodeReaderView.class.getName();

        private QRCodeReader mQRCodeReader;
        private int mPreviewWidth;
        private int mPreviewHeight;
        private CameraManager mCameraManager;
        private boolean mQrDecodingEnabled = true;
        private DecodeFrameTask decodeFrameTask;
        private Map<DecodeHintType, Object> decodeHints;

        /**
         * For Video Recording
         */
        private FFmpegFrameRecorder recorder;
        long startTime = 0;
        boolean recording = false;
        private int frameRate = 30;

        /* audio data getting thread */
        private AudioRecord audioRecord;
        private AudioRecordRunnable audioRecordRunnable;
        private int sampleAudioRateInHz = 44100;
        private Thread audioThread;
        volatile boolean runAudioThread = true;

        private Frame yuvImage = null;

        public QRCodeReaderView(Context context)
            {
                this(context, null);
            }

        public QRCodeReaderView(Context context, AttributeSet attrs)
            {
                super(context, attrs);
                if (isInEditMode())
                    {
                        return;
                    }
                if (checkCameraHardware())
                    {
                        mCameraManager = new CameraManager(getContext());
                        mCameraManager.setPreviewCallback(this);
                        getHolder().addCallback(this);
                        setBackCamera();
                    } else
                    {
                        throw new RuntimeException("Error: Camera not found");
                    }
            }

        /**
         * -----------------------------------
         * Set The Callback To Return Decoding
         * -----------------------------------
         *
         * @param onQRCodeReadListener The Listener
         */
        public void setOnQRCodeReadListener(OnQRCodeReadListener onQRCodeReadListener)
            {
                mOnQRCodeReadListener = onQRCodeReadListener;
            }

        /**
         * ----------------------------
         * Enable / Disable QR Decoding
         * ----------------------------
         *
         * @param qrDecodingEnabled (True = Enabled, False = Disabled)
         */
        public void setQRDecodingEnabled(boolean qrDecodingEnabled)
            {
                this.mQrDecodingEnabled = qrDecodingEnabled;
            }

        /**
         * ----------------------------
         * Provides QR Decoding Status.
         * ----------------------------
         */
        public boolean getQRDecodingStatus()
            {
                return this.mQrDecodingEnabled;
            }

        /**
         * ---------------------------------
         * Set QR Hints Required For Deoding
         * ---------------------------------
         */
        public void setDecodeHints(Map<DecodeHintType, Object> decodeHints)
            {
                this.decodeHints = decodeHints;
            }

        /**
         * ---------------------------------
         * Get File Path Of Video Storage
         * ---------------------------------
         */
        public String getFilePath()
            {
                String root = Environment.getExternalStorageDirectory().toString();
                File myDir = new File(root + "/media/");
                if (!myDir.exists())
                    {
                        myDir.mkdirs();
                    }
                String fileName = "video-" + new Date().getTime() + ".mp4";
                File file = new File(myDir, fileName);
                return file.getAbsolutePath();
            }

        /**
         * ---------------------------------
         * Initialize FFMPEG Video Recorder
         * ---------------------------------
         */
        public void initRecorder()
            {
                /**
                 * Variables For Video Capturing
                 */
                Utils.showLog(TAG, "Creating Frame To Store Video Data & Provide To Recorder");
                yuvImage = new Frame(getWidth(), getHeight(), Frame.DEPTH_UBYTE, 2);
                Utils.showLog(TAG, "Initializing FFmpegFrameRecorder");
                recorder = new FFmpegFrameRecorder(getFilePath(), getWidth(), getHeight(), 1);
                Utils.showLog(TAG, "Setting Format Of FFmpegFrameRecorder");
                recorder.setFormat("mp4");
                Utils.showLog(TAG, "Setting Sample Rate Of FFmpegFrameRecorder");
                recorder.setSampleRate(sampleAudioRateInHz);
                Utils.showLog(TAG, "Setting Frame Rate Of FFmpegFrameRecorder");
                recorder.setFrameRate(frameRate);
                Utils.showLog(TAG, "Setting Video Codec Of FFmpegFrameRecorder");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                Utils.showLog(TAG, "Setting Video Options Of FFmpegFrameRecorder");
                recorder.setVideoOption("crf", "28");
                recorder.setVideoOption("preset", "superfast");
                recorder.setVideoOption("tune", "zerolatency");

                /**
                 * Variables For Audio Capturing
                 */
                Utils.showLog(TAG, "Creating AudioRecordRunnable For Recording Audio");
                audioRecordRunnable = new AudioRecordRunnable();
                Utils.showLog(TAG, "Creating Thread For AudioRecordRunnable");
                audioThread = new Thread(audioRecordRunnable);
                Utils.showLog(TAG, "Setting Audio Flag Which Indicates Audio Recording Started");
                runAudioThread = true;
                Utils.showLog(TAG, "Started Audio Thread");
            }

        /**
         * ----------------------
         * Start Capturing Video
         * ----------------------
         */
        public void startRecording()
            {
                initRecorder();
                try
                    {
                        recorder.start();
                        startTime = System.currentTimeMillis();
                        Utils.showLog(TAG, "Start Time Changed To : " + startTime);
                        recording = true;
                        audioThread.start();
                    } catch (FFmpegFrameRecorder.Exception e)
                    {
                        e.printStackTrace();
                    }
            }

        /**
         * --------------------
         * Stop Capturing Video
         * --------------------
         */
        public void stopRecording()
            {
                runAudioThread = false;
                try
                    {
                        audioThread.join();
                    } catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        return;
                    }
                audioRecordRunnable = null;
                audioThread = null;
                if (recorder != null && recording)
                    {
                        recording = false;
                        Utils.showLog(TAG, "Finishing recording, calling stop and release on recorder");
                        try
                            {
                                recorder.stop();
                                recorder.release();
                            } catch (FFmpegFrameRecorder.Exception e)
                            {
                                e.printStackTrace();
                            }
                        recorder = null;
                    }
            }

        /**
         * --------------------------------
         * Starts Camera Preview & Decoding
         * --------------------------------
         */
        public void startCamera()
            {
                mCameraManager.startPreview();
            }

        /**
         * -------------------------------
         * Stops Camera Preview & Decoding
         * -------------------------------
         */
        public void stopCamera()
            {
                mCameraManager.stopPreview();
            }

        /**
         * ---------------------------------------------
         * Set Camera Auto-focus Interval Value,
         * Default Value Is 5000 Milliseconds (5 Seconds)
         * ---------------------------------------------
         *
         * @param autofocusIntervalInMs Auto-focus Value In Milliseconds
         */
        public void setAutofocusInterval(long autofocusIntervalInMs)
            {
                if (mCameraManager != null)
                    {
                        mCameraManager.setAutofocusInterval(autofocusIntervalInMs);
                    }
            }

        /**
         * ------------------
         * Triger Auto Focus
         * ------------------
         */
        public void forceAutoFocus()
            {
                if (mCameraManager != null)
                    {
                        mCameraManager.forceAutoFocus();
                    }
            }


        /**
         * ---------------------------------
         * Enable / Disable Torch Of Camera
         * ---------------------------------
         * <p>
         * * @param enabled (True = Torch On, False = Torch Off)
         */
        public void setTorchEnabled(boolean enabled)
            {
                if (mCameraManager != null)
                    {
                        mCameraManager.setTorchEnabled(enabled);
                    }
            }


        /**
         * ------------------------------------------------------------------
         * Allows User To Specify The Camera ID,
         * Rather Than Determine It Automatically based On Available Cameras.
         * ------------------------------------------------------------------
         * <p>
         * * @param cameraId Camera ID Of The Camera To Use, Negative Value Equals No Preference.
         */
        public void setPreviewCameraId(int cameraId)
            {
                mCameraManager.setPreviewCameraId(cameraId);
            }


        /**
         * --------------------------------
         * Switch To Back Camera Of Device
         * --------------------------------
         */
        public void setBackCamera()
            {
                setPreviewCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
            }

        /**
         * ---------------------------------
         * Switch To Front Camera Of Device
         * ---------------------------------
         */
        public void setFrontCamera()
            {
                setPreviewCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }

        /**
         * ----------------------------------------------------
         * If View Destroyed Cancel Qr Decoding Task If Running
         * ----------------------------------------------------
         */
        @Override
        public void onDetachedFromWindow()
            {
                super.onDetachedFromWindow();
                if (decodeFrameTask != null)
                    {
                        decodeFrameTask.cancel(true);
                        decodeFrameTask = null;
                    }
            }

        /**
         * ------------------------
         * SurfaceHolder.Callbback,
         * Camera.PreviewCallback
         * ------------------------
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder)
            {
                Utils.showLog(TAG, "SurfaceCreated");
                try
                    {
                        // Indicate camera, our View dimensions
                        mCameraManager.openDriver(holder, this.getWidth(), this.getHeight());
                    } catch (IOException e)
                    {
                        Utils.showLog(TAG, "Can not openDriver: " + e.getMessage());
                        mCameraManager.closeDriver();
                    }
                try
                    {
                        mQRCodeReader = new QRCodeReader();
                        mCameraManager.startPreview();
                        new Handler().postDelayed(new Runnable()
                            {
                                @Override
                                public void run()
                                    {
                                        QRCodeReaderView.this.mOnQRCodeReadListener.cameraAvailable(mCameraManager.getOpenCamera().getCamera());
                                    }
                            }, 2000);
                    } catch (Exception e)
                    {
                        Utils.showLog(TAG, "Exception: " + e.getMessage());
                        mCameraManager.closeDriver();
                    }

            }

        /**
         * -------------------------------
         * Called When SurfaceView Changes
         * -------------------------------
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
            {
                Utils.showLog(TAG, "surfaceChanged");
                if (holder.getSurface() == null)
                    {
                        Utils.showLog(TAG, "Error: preview surface does not exist");
                        return;
                    }
                if (mCameraManager.getPreviewSize() == null)
                    {
                        Utils.showLog(TAG, "Error: preview size does not exist");
                        return;
                    }
                mPreviewWidth = mCameraManager.getPreviewSize().x;
                mPreviewHeight = mCameraManager.getPreviewSize().y;
                mCameraManager.stopPreview();
                // Fix the camera sensor rotation
                mCameraManager.setPreviewCallback(this);
                mCameraManager.setDisplayOrientation(getCameraDisplayOrientation());
                mCameraManager.startPreview();
            }

        /**
         * ----------------------------------
         * Called When SurfaceView Destroyed
         * ----------------------------------
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder)
            {
                Utils.showLog(TAG, "Surface Destroyed");
                mCameraManager.setPreviewCallback(null);
                mCameraManager.stopPreview();
                mCameraManager.closeDriver();
            }

        /**
         * --------------------------------
         * Called When Camera Takes A Frame
         * --------------------------------
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera camera)
            {
                /**
                 * For Decoding QR Code
                 */
                if (!mQrDecodingEnabled || decodeFrameTask != null && decodeFrameTask.getStatus() == AsyncTask.Status.RUNNING)
                    {
                        Utils.showLog(TAG, "QR Reader Not Reading");
                    } else
                    {
                        Utils.showLog(TAG, "QR Reader Reading");
                        decodeFrameTask = new DecodeFrameTask(this, decodeHints);
                        decodeFrameTask.execute(data);
                    }

                /**
                 * For Recording Video
                 */
                if (audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                    {
                        startTime = System.currentTimeMillis();
                        Utils.showLog(TAG, "Start Time Changed : " + startTime);
                        return;
                    }

                if (yuvImage != null && recording)
                    {
                        Utils.showLog(TAG, "Image Data Length : " + data.length);
                        Utils.showLog(TAG, "Buffer  Length : " + yuvImage.image[0].position(0).capacity());
                        ((ByteBuffer) yuvImage.image[0].position(0)).put(data);
                        Frame toBeCapturedFrame = new Frame(getWidth(), getHeight(), Frame.DEPTH_UBYTE, 2);
                        ((ByteBuffer) toBeCapturedFrame.image[0].position(0)).put(data);
                        try
                            {
                                Utils.showLog(TAG, "Writing Frame");
                                long t = 1000 * (System.currentTimeMillis() - startTime);
                                if (t > recorder.getTimestamp())
                                    {
                                        recorder.setTimestamp(t);
                                    }
                                recorder.record(toBeCapturedFrame);

                            } catch (FFmpegFrameRecorder.Exception e)
                            {
                                Utils.showLog(TAG, e.getMessage());
                                e.printStackTrace();
                            }
                    }
            }

        /**
         * ----------------------------------
         * Check If This Device Has A Camera
         * ----------------------------------
         */
        private boolean checkCameraHardware()
            {
                if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
                    {
                        // This Device Has Camera
                        return true;
                    } else if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT))
                    {
                        // This Device Has A Front Camera
                        return true;
                    } else
                    {
                        // This Device Has Any Camera
                        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
                    }
            }

        /**
         * -----------------------------------------
         * Fix For The Camera Sensor On Some Devices
         * -----------------------------------------
         */
        @SuppressWarnings("deprecation")
        private int getCameraDisplayOrientation()
            {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.GINGERBREAD)
                    {
                        return 90;
                    }
                Camera.CameraInfo info = new Camera.CameraInfo();
                getCameraInfo(mCameraManager.getPreviewCameraId(), info);
                WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                int rotation = windowManager.getDefaultDisplay().getRotation();
                int degrees = 0;
                switch (rotation)
                    {
                        case Surface.ROTATION_0:
                            degrees = 0;
                            break;
                        case Surface.ROTATION_90:
                            degrees = 90;
                            break;
                        case Surface.ROTATION_180:
                            degrees = 180;
                            break;
                        case Surface.ROTATION_270:
                            degrees = 270;
                            break;
                        default:
                            break;
                    }

                int result;
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    {
                        // Front Facing Camera
                        result = (info.orientation + degrees) % 360;
                        result = (360 - result) % 360;  // compensate the mirror
                    } else
                    {   // Back Facing Camera
                        result = (info.orientation - degrees + 360) % 360;
                    }
                return result;
            }

        /**
         * --------------------------------------
         * AsyncTask To Decode Frames For QR Code
         * --------------------------------------
         */
        private static class DecodeFrameTask extends AsyncTask<byte[], Void, Result>
            {
                private final WeakReference<QRCodeReaderView> viewRef;
                private final WeakReference<Map<DecodeHintType, Object>> hintsRef;
                private final QRToViewPointTransformer qrToViewPointTransformer = new QRToViewPointTransformer();

                public DecodeFrameTask(QRCodeReaderView view, Map<DecodeHintType, Object> hints)
                    {
                        viewRef = new WeakReference<>(view);
                        hintsRef = new WeakReference<>(hints);
                    }

                @Override
                protected Result doInBackground(byte[]... params)
                    {
                        final QRCodeReaderView view = viewRef.get();
                        if (view == null)
                            {
                                return null;
                            }
                        final PlanarYUVLuminanceSource source = view.mCameraManager.buildLuminanceSource(params[0], view.mPreviewWidth, view.mPreviewHeight);
                        final HybridBinarizer hybBin = new HybridBinarizer(source);
                        final BinaryBitmap bitmap = new BinaryBitmap(hybBin);
                        try
                            {
                                return view.mQRCodeReader.decode(bitmap, hintsRef.get());
                            } catch (ChecksumException e)
                            {
                                Utils.showLog(TAG, "ChecksumException : " + e.toString());
                            } catch (NotFoundException e)
                            {
                                Utils.showLog(TAG, "No QR Code found");
                            } catch (FormatException e)
                            {
                                Utils.showLog(TAG, "FormatException : " + e.toString());
                            } finally
                            {
                                view.mQRCodeReader.reset();
                            }
                        return null;
                    }

                @Override
                protected void onPostExecute(Result result)
                    {
                        super.onPostExecute(result);
                        final QRCodeReaderView view = viewRef.get();

                        /**
                         * -----------------------
                         * Notify We Found QR Code
                         * -----------------------
                         */
                        if (view != null && result != null && view.mOnQRCodeReadListener != null)
                            {
                                // Transform resultPoints to View coordinates
                                final PointF[] transformedPoints = transformToViewCoordinates(view, result.getResultPoints());
                                view.mOnQRCodeReadListener.onQRCodeRead(result.getText(), transformedPoints);
                            }
                    }

                /**
                 * ------------------------------------------------------------------------------------
                 * Transform Result To SurfaceView Co-ordinate
                 * <p>
                 * This Method Is Needed Because Co-ordinate are given in landscape Camera Co-ordinate,
                 * When Device Is In Portrait Mode,
                 * And Different Co-ordinates Otherwise.
                 * ------------------------------------------------------------------------------------
                 *
                 * @return a new PointF array with transformed points
                 */
                private PointF[] transformToViewCoordinates(QRCodeReaderView view, ResultPoint[] resultPoints)
                    {
                        int orientationDegrees = view.getCameraDisplayOrientation();
                        Orientation orientation = orientationDegrees == 90 || orientationDegrees == 270 ? Orientation.PORTRAIT : Orientation.LANDSCAPE;
                        Point viewSize = new Point(view.getWidth(), view.getHeight());
                        Point cameraPreviewSize = view.mCameraManager.getPreviewSize();
                        boolean isMirrorCamera = view.mCameraManager.getPreviewCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT;
                        return qrToViewPointTransformer.transform(resultPoints, isMirrorCamera, orientation, viewSize, cameraPreviewSize);
                    }
            }

        /**
         * ---------------------------------------
         * Audio Thread, Gets & Encodes Audio Data
         * ---------------------------------------
         */
        class AudioRecordRunnable implements Runnable
            {

                @Override
                public void run()
                    {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                        // Audio
                        int bufferSize;
                        ShortBuffer audioData;
                        int bufferReadResult;

                        bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                        audioData = ShortBuffer.allocate(bufferSize);


                        Utils.showLog(TAG, "Starting Audio Recording");
                        audioRecord.startRecording();

                        /**
                         * FFMPEG Audio Encoding Loop
                         */
                        while (runAudioThread)
                            {
                                Utils.showLog(TAG, "Recording : " + recording);
                                bufferReadResult = audioRecord.read(audioData.array(), 0, audioData.capacity());
                                audioData.limit(bufferReadResult);
                                if (bufferReadResult > 0)
                                    {
                                        Utils.showLog(TAG, "Buffer Read Result : " + bufferReadResult);
                                        /**
                                         * If Recording Isn't True When Start This Thread, It Will Never Get To This If Statement
                                         */
                                        if (recording)
                                            {
                                                try
                                                    {
                                                        recorder.recordSamples(audioData);
                                                    } catch (FFmpegFrameRecorder.Exception e)
                                                    {
                                                        Utils.showLog(TAG, "FFmpegFrameRecorder Exception : " + e.getMessage());
                                                        e.printStackTrace();
                                                    }
                                            }
                                    }
                            }

                        Utils.showLog(TAG, "AudioThread Finished, Release Audio Recorder");
                        /**
                         * Encoding Finish, Release Recorder
                         */
                        if (audioRecord != null)
                            {
                                audioRecord.stop();
                                audioRecord.release();
                                audioRecord = null;
                                Utils.showLog(TAG, "Audio Recorder Released");
                            }
                    }
            }
    }