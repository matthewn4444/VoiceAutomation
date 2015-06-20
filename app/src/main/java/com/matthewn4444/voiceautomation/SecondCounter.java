package com.matthewn4444.voiceautomation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class SecondCounter implements Runnable {
    private final Context mCtx;
    private final Handler mHandler;

    private Thread mThread;
    private OnTimeUpdateListener mListener;

    public interface OnTimeUpdateListener {
        public void onTimeUpdate();
    }

    public SecondCounter(Context ctx) {
        mCtx = ctx;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                work();
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setOnTimeUpdateListener(OnTimeUpdateListener listener) {
        mListener = listener;
    }

    public void start() {
        synchronized (this) {
            if (mThread == null) {
                mThread = new Thread(this);
                mThread.start();
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
        }
    }

    private void work() {
        if (mListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onTimeUpdate();
                    }
                }
            });
        }
    }
}
