package com.pixel.weather;

import android.app.Activity;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final Pattern RGB_PATTERN =
            Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)");

    private static final int INITIAL_BG = 0xFFF2F5F9;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(INITIAL_BG);
        window.setNavigationBarColor(INITIAL_BG);
        applyBarIconMode(INITIAL_BG);

        webView = new WebView(this);
        webView.setBackgroundColor(INITIAL_BG);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        // Пасхалка разработчика — короткий звук по нажатию.
        // false гарантирует воспроизведение data:audio даже в строгих WebView.
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new ThemeBridge(), "Android");
        setContentView(webView);

        // Загружаем HTML через https-базу, чтобы fetch() и localStorage
        // работали так же, как в обычном браузере (без file:// ограничений).
        webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/",
                readAsset("weather.html"),
                "text/html",
                "utf-8",
                null
        );
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "<html><body style=\"font-family:sans-serif;padding:24px\">"
                    + "Не удалось загрузить приложение.</body></html>";
        }
    }

    private void applyBarIconMode(int color) {
        boolean lightBackground = isLightColor(color);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();

        if (lightBackground) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private boolean isLightColor(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;

        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 0.55;
    }

    private int parseCssColor(String cssColor) {
        if (cssColor == null) return Color.TRANSPARENT;

        Matcher matcher = RGB_PATTERN.matcher(cssColor);
        if (matcher.find()) {
            return Color.rgb(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        }

        if (cssColor.startsWith("#")) {
            try {
                return Color.parseColor(cssColor);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Color.TRANSPARENT;
    }

    private int averageColor(Drawable drawable) {
        try {
            int size = 32;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);

            long r = 0, g = 0, b = 0, count = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if (Color.alpha(pixel) < 128) continue;
                    r += Color.red(pixel);
                    g += Color.green(pixel);
                    b += Color.blue(pixel);
                    count++;
                }
            }
            bitmap.recycle();

            if (count == 0) return Color.TRANSPARENT;
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (Exception e) {
            return Color.TRANSPARENT;
        }
    }

    private int hueFromColor(int color) {
        if (color == Color.TRANSPARENT) return -1;

        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        // Почти серый, чёрный или пересвеченный цвет не должен
        // превращаться в случайный красный/синий оттенок
        if (hsv[1] < 0.12f || hsv[2] < 0.10f || hsv[2] > 0.97f) return -1;
        return Math.round(hsv[0]) % 360;
    }

    public class ThemeBridge {
        @JavascriptInterface
        public int getWallpaperHue() {
            try {
                WallpaperManager manager = WallpaperManager.getInstance(getApplicationContext());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    WallpaperColors colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                    if (colors == null) {
                        colors = manager.getWallpaperColors(WallpaperManager.FLAG_LOCK);
                    }
                    if (colors != null) {
                        // Сначала основной цвет, затем акцентные:
                        // так серые обои не дают ложный оттенок
                        int hue = hueFromColor(colors.getPrimaryColor().toArgb());
                        if (hue >= 0) return hue;

                        Color secondary = colors.getSecondaryColor();
                        if (secondary != null) {
                            hue = hueFromColor(secondary.toArgb());
                            if (hue >= 0) return hue;
                        }

                        Color tertiary = colors.getTertiaryColor();
                        if (tertiary != null) {
                            hue = hueFromColor(tertiary.toArgb());
                            if (hue >= 0) return hue;
                        }
                    }
                }

                Drawable drawable = manager.getDrawable();
                if (drawable != null) {
                    return hueFromColor(averageColor(drawable));
                }
                return -1;
            } catch (Exception e) {
                return -1;
            }
        }

        @JavascriptInterface
        public void vibrate(int durationMs) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            int duration = Math.max(1, Math.min(durationMs, 50));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }

        @JavascriptInterface
        public void setThemeColor(final String cssColor) {
            final int color = parseCssColor(cssColor);
            if (color == Color.TRANSPARENT) return;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getWindow().setStatusBarColor(color);
                    getWindow().setNavigationBarColor(color);
                    applyBarIconMode(color);
                    if (webView != null) {
                        webView.setBackgroundColor(color);
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Пользователь мог сменить обои, пока приложение было в фоне
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.applyWallpaperIfEnabled && window.applyWallpaperIfEnabled();",
                    null
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
