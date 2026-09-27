package app.mdreader.mobile;

import android.graphics.Color;

/** One small, dependency-free palette for native controls and sheets. */
final class UiPalette {
    final boolean dark;
    final int background, surface, raised, text, muted, border, accent, onAccent, accentSoft, danger;

    UiPalette(boolean dark) {
        this.dark = dark;
        background = Color.parseColor(dark ? "#101319" : "#FAFAFC");
        surface = Color.parseColor(dark ? "#191E27" : "#FFFFFF");
        raised = Color.parseColor(dark ? "#242C38" : "#F0F2F6");
        text = Color.parseColor(dark ? "#F2F4F8" : "#202631");
        muted = Color.parseColor(dark ? "#ABB5C5" : "#616C7D");
        border = Color.parseColor(dark ? "#343E4D" : "#E0E5EC");
        accent = Color.parseColor(dark ? "#FFAA62" : "#AD4300");
        onAccent = Color.parseColor(dark ? "#291606" : "#FFFFFF");
        accentSoft = Color.parseColor(dark ? "#38291F" : "#FFF0E5");
        danger = Color.parseColor(dark ? "#FFB4AB" : "#B42318");
    }

    static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
