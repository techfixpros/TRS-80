/*
 * Copyright 2012-2013, Arno Puder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.puder.trs80;

import java.util.Arrays;

import org.puder.trs80.cast.RemoteCastScreen;
import org.puder.trs80.cast.RemoteDisplayChannel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;

public class RenderThread extends Thread {

    private int                  model;

    private int                  trsScreenCols;
    private int                  trsScreenRows;
    private int                  trsCharWidth;
    private int                  trsCharHeight;

    private Bitmap               font[];

    private boolean              run              = false;
    private boolean              isRendering      = false;
    private SurfaceHolder        surfaceHolder;
    private byte[]               screenBuffer;

    private boolean              lastExpandedMode = false;
    private char[]               screenCharBuffer;
    private RemoteDisplayChannel remoteDisplay;


    public RenderThread(SurfaceHolder holder) {
        this.surfaceHolder = holder;
        Hardware h = TRS80Application.getHardware();
        model = h.getModel();
        screenBuffer = h.getScreenBuffer();
        trsScreenCols = h.getScreenConfiguration().trsScreenCols;
        trsScreenRows = h.getScreenConfiguration().trsScreenRows;
        trsCharWidth = h.getCharWidth();
        trsCharHeight = h.getCharHeight();
        font = h.getFont();
        screenCharBuffer = new char[trsScreenCols * trsScreenRows];
        remoteDisplay = RemoteCastScreen.get();
    }

    public void setRunning(boolean run) {
        this.run = run;
    }

    public boolean isRendering() {
        return this.isRendering;
    }

    @Override
    public synchronized void run() {
        while (run) {
            isRendering = false;
            try {
                this.wait();
            } catch (InterruptedException e) {
                return;
            }
            isRendering = true;
            Canvas c = surfaceHolder.lockCanvas();
            if (c == null) {
                Log.d("Z80", "Canvas is null");
                continue;
            }
            renderScreen(c);
            surfaceHolder.unlockCanvasAndPost(c);
        }
    }

    public synchronized void renderScreen(Canvas canvas) {
        boolean expandedMode = TRS80Application.getHardware().getExpandedScreenMode();
        int d = expandedMode ? 2 : 1;
        if (expandedMode) {
            canvas.scale(2, 1);
        }

        // Clear buffer when expanded mode changes.
        if (expandedMode != lastExpandedMode) {
            Arrays.fill(screenCharBuffer, (char) 0);
            lastExpandedMode = expandedMode;
        }

        int i = 0;
        for (int row = 0; row < trsScreenRows; row++) {
            for (int col = 0; col < trsScreenCols / d; col++) {
                int ch = screenBuffer[i] & 0xff;
                // Emulate Radio Shack lowercase mod (for Model 1)
                if (this.model == Hardware.MODEL1 && ch < 0x20) {
                    ch += 0x40;
                }
                int startx = trsCharWidth * col;
                int starty = trsCharHeight * row;
                if (font[ch] == null) {
                    Log.d("Z80", "font[" + ch + "] == null");
                    continue;
                }
                canvas.drawBitmap(font[ch], startx, starty, null);

                // TODO: Choose encoding based on current model.
                screenCharBuffer[(row * trsScreenCols) + col] = CharMapping.m3toUnicode[ch];
                i += d;
            }
        }
        remoteDisplay.sendScreenBuffer(expandedMode, String.valueOf(screenCharBuffer));
        Log.d("DBG>>>", "trsScreenCols: " + trsScreenCols + " trsCharWidth: " + trsCharWidth
                + " trsCharHeight: " + trsCharHeight + " trsScreenRows: " + trsScreenRows);
    }

    public synchronized void triggerScreenUpdate() {
        this.notify();
    }

}
