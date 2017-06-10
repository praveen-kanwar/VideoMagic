package com.praveen.cameraview.Utils;

import android.content.Context;
import android.util.Log;

/**
 * Created by Praveen Kanwar on 06/06/17.
 * Copyright (c) 2017 .
 */
public class Utils
    {
        /**
         * Context Reference
         */
        private static Context context;

        /**
         * To Enable/true Or Disable/false DDMS Log
         */
        public static boolean DEBUG = true;

        /**
         * To Display The Given Message In Any Function Or Class In DDMS Log
         *
         * @param TAG
         * @param message
         */
        public static void showLog(String TAG, String message)
            {
                try
                    {
                        if (Utils.DEBUG)
                            {
                                Log.e(TAG, message);
                            }
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                    }

            }
    }
