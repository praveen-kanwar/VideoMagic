package com.praveen.android.videomagic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.praveen.cameraview.QRCodeReaderView;
import com.praveen.cameraview.Utils.Utils;

/**
 * Created by Praveen Kanwar on 06/06/17.
 * Copyright (c) 2017 .
 */
public class MagicActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, QRCodeReaderView.OnQRCodeReadListener, View.OnTouchListener
    {
        private static final String TAG = MagicActivity.class.getSimpleName();

        private static final int MY_PERMISSION_REQUEST_CAMERA = 0;
        private static final int MY_PERMISSION_RECORD_AUDIO = 1;
        private static final int MY_PERMISSION_WRITE_EXTERNAL_STORAGE = 2;

        private ViewGroup magicLayout;

        /**
         * QR Code Reading Variable
         */
        private QRCodeReaderView qrCodeReaderView;
        private OverlayView qrDetectionView;


        private FloatingActionButton startCaptureButton;
        private PowerManager.WakeLock mWakeLock;

        /**
         * Permissions
         */
        boolean cameraPermission = false;
        boolean audioPermission = false;
        boolean externalStoragePermission = false;


        final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener()
            {
                public void onLongPress(MotionEvent e)
                    {
                        Utils.showLog(TAG, "LongPress detected Starting QR Scanning");
                        qrCodeReaderView.setQRDecodingEnabled(true);
                        qrDetectionView.setVisibility(View.VISIBLE);
                        new Handler().postDelayed(new Runnable()
                            {
                                @Override
                                public void run()
                                    {
                                        if (qrCodeReaderView.getQRDecodingStatus())
                                            {
                                                qrCodeReaderView.setQRDecodingEnabled(false);
                                                qrDetectionView.setVisibility(View.GONE);
                                                Toast.makeText(MagicActivity.this, "No QR Code Found", Toast.LENGTH_LONG).show();
                                            }
                                    }
                            }, 3000);
                    }
            });

        @Override
        protected void onCreate(Bundle savedInstanceState)
            {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_magic);

                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
                mWakeLock.acquire();
                magicLayout = (ViewGroup) findViewById(R.id.magic_layout);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    {
                        cameraPermission = true;
                    } else
                    {
                        requestCameraPermission();
                    }
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    {
                        audioPermission = true;
                    } else
                    {
                        requestAudioPermission();
                    }
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                    {
                        externalStoragePermission = true;
                    } else
                    {
                        requestExternalStoragePermission();
                    }
                if (cameraPermission && audioPermission && externalStoragePermission)
                    {
                        initQRCodeReaderView();
                    }
            }

        @Override
        protected void onResume()
            {
                Utils.showLog(TAG, "onResume Called");
                super.onResume();
                if (qrCodeReaderView != null)
                    {
                        qrCodeReaderView.startCamera();
                    }
                if (mWakeLock == null)
                    {
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
                        mWakeLock.acquire();
                    }
            }

        @Override
        protected void onPause()
            {
                Utils.showLog(TAG, "onPause Called");
                super.onPause();
                if (qrCodeReaderView != null)
                    {
                        qrCodeReaderView.stopCamera();
                    }
                if (mWakeLock != null)
                    {
                        mWakeLock.release();
                        mWakeLock = null;
                    }
            }

        @Override
        protected void onStop()
            {
                Utils.showLog(TAG, "onStop Called");
                super.onStop();
                if (qrCodeReaderView != null)
                    {
                        qrCodeReaderView.stopCamera();
                    }
                if (mWakeLock != null)
                    {
                        mWakeLock.release();
                        mWakeLock = null;
                    }
            }

        @Override
        protected void onDestroy()
            {
                Utils.showLog(TAG, "onDestroy Called");
                super.onDestroy();
                if (qrCodeReaderView != null)
                    {
                        qrCodeReaderView.stopCamera();
                    }
                if (mWakeLock != null)
                    {
                        mWakeLock.release();
                        mWakeLock = null;
                    }
            }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
            {
                if ((requestCode == MY_PERMISSION_REQUEST_CAMERA) && (grantResults.length == 1) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                    {
                        Snackbar.make(magicLayout, "Camera permission was granted.", Snackbar.LENGTH_SHORT).show();
                    } else if ((requestCode == MY_PERMISSION_REQUEST_CAMERA))
                    {
                        Snackbar.make(magicLayout, "Camera permission request was denied.", Snackbar.LENGTH_SHORT).show();
                    }
                if ((requestCode == MY_PERMISSION_RECORD_AUDIO) && (grantResults.length == 1) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                    {
                        Snackbar.make(magicLayout, "Record audio permission was granted.", Snackbar.LENGTH_SHORT).show();
                    } else if ((requestCode == MY_PERMISSION_RECORD_AUDIO))
                    {
                        Snackbar.make(magicLayout, "Record audio request was denied.", Snackbar.LENGTH_SHORT).show();
                    }
                if ((requestCode == MY_PERMISSION_WRITE_EXTERNAL_STORAGE) && (grantResults.length == 1) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                    {
                        Snackbar.make(magicLayout, "Write External Storage permission was granted.", Snackbar.LENGTH_SHORT).show();
                    } else if ((requestCode == MY_PERMISSION_WRITE_EXTERNAL_STORAGE))
                    {
                        Snackbar.make(magicLayout, "Write External Storage permission request was denied.", Snackbar.LENGTH_SHORT).show();
                    }
                if (cameraPermission && audioPermission && externalStoragePermission)
                    {
                        initQRCodeReaderView();
                    }
                return;
            }

        /**
         * Called When QR Code Is Decoded
         * Text Of QR Code
         * Control Points Of QR Code
         *
         * @param text
         * @param points
         */
        @Override
        public void onQRCodeRead(String text, PointF[] points)
            {
                qrCodeReaderView.setQRDecodingEnabled(false);
                qrDetectionView.setPoints(points);
                new Handler().postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                            {
                                qrDetectionView.setVisibility(View.GONE);
                            }
                    }, 2000);
                Toast.makeText(MagicActivity.this, "QR Code Result : " + text, Toast.LENGTH_LONG).show();
            }

        @Override
        public void cameraAvailable(Camera mCamera)
            {
                startCaptureButton.setVisibility(View.VISIBLE);
            }

        private void requestCameraPermission()
            {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
                    {
                        Snackbar.make(magicLayout, "Camera access is required to display the camera preview.",
                                Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener()
                            {
                                @Override
                                public void onClick(View view)
                                    {
                                        ActivityCompat.requestPermissions(MagicActivity.this, new String[]{
                                                Manifest.permission.CAMERA
                                        }, MY_PERMISSION_REQUEST_CAMERA);
                                    }
                            }).show();
                    } else
                    {
                        Snackbar.make(magicLayout, "Permission is not available. Requesting camera permission.",
                                Snackbar.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.CAMERA
                        }, MY_PERMISSION_REQUEST_CAMERA);
                    }
            }

        private void requestAudioPermission()
            {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO))
                    {
                        Snackbar.make(magicLayout, "Record Audio rights required for video capturing.",
                                Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener()
                            {
                                @Override
                                public void onClick(View view)
                                    {
                                        ActivityCompat.requestPermissions(MagicActivity.this, new String[]{
                                                Manifest.permission.CAMERA
                                        }, MY_PERMISSION_RECORD_AUDIO);
                                    }
                            }).show();
                    } else
                    {
                        Snackbar.make(magicLayout, "Permission is not available. Requesting Record Audio permission.",
                                Snackbar.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.CAMERA
                        }, MY_PERMISSION_RECORD_AUDIO);
                    }
            }

        private void requestExternalStoragePermission()
            {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    {
                        Snackbar.make(magicLayout, "Storage access is required to record video",
                                Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener()
                            {
                                @Override
                                public void onClick(View view)
                                    {
                                        ActivityCompat.requestPermissions(MagicActivity.this, new String[]{
                                                Manifest.permission.CAMERA
                                        }, MY_PERMISSION_WRITE_EXTERNAL_STORAGE);
                                    }
                            }).show();
                    } else
                    {
                        Snackbar.make(magicLayout, "Permission is not available. Requesting Storage access permission.",
                                Snackbar.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.CAMERA
                        }, MY_PERMISSION_WRITE_EXTERNAL_STORAGE);
                    }
            }

        private void initQRCodeReaderView()
            {
                View content = getLayoutInflater().inflate(R.layout.magic_view, magicLayout, true);

                /**
                 * Custom Surface View Where Preview Of Camera Is Displayed
                 */
                qrCodeReaderView = (QRCodeReaderView) content.findViewById(R.id.qrcodereaderview);
                qrCodeReaderView.setOnTouchListener(this);
                qrCodeReaderView.setAutofocusInterval(2000L);
                qrCodeReaderView.setOnQRCodeReadListener(this);
                qrCodeReaderView.setBackCamera();
                qrCodeReaderView.setQRDecodingEnabled(false);

                /**
                 * View To Show On Video Where QR Code Is Present
                 */
                qrDetectionView = (OverlayView) findViewById(R.id.overlay_view);
                qrDetectionView.setVisibility(View.GONE);

                /**
                 * Video Recording Variable
                 */
                startCaptureButton = (FloatingActionButton) content.findViewById(R.id.startCaptureButton);
                startCaptureButton.setOnTouchListener(this);
                startCaptureButton.setVisibility(View.GONE);

            }

        @Override
        public boolean onTouch(View v, MotionEvent event)
            {
                if (v.getId() == R.id.startCaptureButton)
                    {
                        switch (event.getAction())
                            {
                                case MotionEvent.ACTION_DOWN:
                                    Utils.showLog(TAG, "Recording Video");
                                    qrCodeReaderView.setTorchEnabled(true);
                                    Toast.makeText(MagicActivity.this, "Started Recording", Toast.LENGTH_LONG).show();
                                    qrCodeReaderView.startRecording();
                                    return true;
                                case MotionEvent.ACTION_UP:
                                    Utils.showLog(TAG, "Saving Video");
                                    qrCodeReaderView.setTorchEnabled(false);
                                    Toast.makeText(MagicActivity.this, "Stopped Recording", Toast.LENGTH_LONG).show();
                                    qrCodeReaderView.stopRecording();
                                    return true;
                            }
                        return false;
                    } else
                    {
                        return gestureDetector.onTouchEvent(event);
                    }
            }
    }