package com.stretchx.launcher;

import android.view.Surface;

interface IStretchService {
    int createDisplay(int width, int height, int densityDpi, in Surface surface) = 1;
    int getDisplayId() = 2;
    void releaseDisplay() = 3;
    String getLastError() = 4;
    String getLastFlags() = 5;
    String getContextSource() = 6;
    void destroy() = 16777114;
}
