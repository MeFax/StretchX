package com.stretchx.launcher;

import android.view.Surface;

interface IStretchService {
    int createDisplay(int width, int height, int densityDpi, in Surface surface);
    int getDisplayId();
    void releaseDisplay();
    void destroy();
}
