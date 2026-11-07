package com.pixel.weather;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final Pattern RGB_PATTERN =
            Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)");

    private static final int REQUEST_FONT = 4102;
    private static final long MAX_FONT_BYTES = 10L * 1024L * 1024L;
    private static final String FONT_FILE = "user-font.ttf";
    private static final String FONT_PREFS = "pixel_weather_font";
    private static final String FONT_NAME_KEY = "display_name";
    private static final String FONT_MIME_KEY = "mime";

    private WebView webView;
    private FrameLayout root;
    private WeatherSplashView splashView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile boolean appReady = false;
    private int pendingThemeColor = Color.TRANSPARENT;

    private final Runnable revealFallback = new Runnable() {
        @Override
        public void run() {
            revealApp();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int launchBg = getResources().getColor(R.color.launch_background);
        Window window = getWindow();
        window.setStatusBarColor(launchBg);
        window.setNavigationBarColor(launchBg);
        applyBarIconMode(launchBg);

        // WebView рисует тёмный фон ещё до появления HTML:
        // так между системным splash и приложением нет белой вспышки.
        webView = new WebView(this);
        webView.setBackgroundColor(launchBg);

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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Страховка: если JS не успел позвать appReady, показываем приложение
                handler.postDelayed(revealFallback, 1200);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                // Свой шрифт лежит во внутренней памяти и отдаётся тем же origin,
                // что и HTML, поэтому FontFace загружает его без CORS-проблем.
                if (url != null && url.contains("/user-font.ttf")) {
                    return serveUserFont();
                }
                return super.shouldInterceptRequest(view, url);
            }
        });
        webView.addJavascriptInterface(new ThemeBridge(), "Android");

        root = new FrameLayout(this);
        root.setBackgroundColor(launchBg);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        splashView = new WeatherSplashView(this);
        root.addView(splashView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);

        // Общая страховка на случай, если WebView вообще не загрузился
        handler.postDelayed(revealFallback, 8000);

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

    private File fontFile() {
        return new File(getFilesDir(), FONT_FILE);
    }

    private SharedPreferences fontPrefs() {
        return getSharedPreferences(FONT_PREFS, MODE_PRIVATE);
    }

    private String getFontMime() {
        String mime = fontPrefs().getString(FONT_MIME_KEY, "font/ttf");
        return "font/otf".equals(mime) ? "font/otf" : "font/ttf";
    }

    private WebResourceResponse serveUserFont() {
        File font = fontFile();
        if (!font.isFile()) {
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }
        try {
            WebResourceResponse response = new WebResourceResponse(
                    getFontMime(), null, new FileInputStream(font));
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            headers.put("Access-Control-Allow-Origin", "*");
            response.setResponseHeaders(headers);
            return response;
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }
    }

    private String detectFontMime(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] header = new byte[4];
            if (in.read(header) != 4) return null;
            int tag = ((header[0] & 0xFF) << 24)
                    | ((header[1] & 0xFF) << 16)
                    | ((header[2] & 0xFF) << 8)
                    | (header[3] & 0xFF);
            if (tag == 0x4F54544F) return "font/otf";
            if (tag == 0x00010000 || tag == 0x74727565 || tag == 0x74797031) return "font/ttf";
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private String queryDisplayName(Uri uri) {
        String name = "font.ttf";
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) name = value.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FONT) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            notifyFont(false, "", "cancel");
            return;
        }

        Uri uri = data.getData();
        String displayName = queryDisplayName(uri);
        File target = fontFile();
        File temp = new File(getFilesDir(), FONT_FILE + ".tmp");

        boolean copied = false;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IOException("No input stream");
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FONT_BYTES) {
                    notifyFont(false, "", "size");
                    return;
                }
                out.write(buffer, 0, read);
            }
            copied = total > 0;
        } catch (Exception e) {
            notifyFont(false, "", "read");
            return;
        } finally {
            if (!copied) temp.delete();
        }

        String mime = copied ? detectFontMime(temp) : null;
        if (mime == null) {
            temp.delete();
            notifyFont(false, "", "type");
            return;
        }

        if (target.exists() && !target.delete()) {
            temp.delete();
            notifyFont(false, "", "read");
            return;
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            notifyFont(false, "", "read");
            return;
        }

        String lower = displayName.toLowerCase(Locale.US);
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
            displayName = displayName + ("font/otf".equals(mime) ? ".otf" : ".ttf");
        }

        fontPrefs().edit()
                .putString(FONT_NAME_KEY, displayName)
                .putString(FONT_MIME_KEY, mime)
                .apply();
        notifyFont(true, displayName, "");
    }

    private void notifyFont(boolean ok, String name, String reason) {
        final WebView view = webView;
        if (view == null) return;
        final String script = "window.onFontPicked && window.onFontPicked("
                + JSONObject.quote(name == null ? "" : name) + ","
                + (ok ? "true" : "false") + ","
                + JSONObject.quote(reason == null ? "" : reason) + ");";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webView != null) webView.evaluateJavascript(script, null);
            }
        });
    }

    // Прячем нативный splash только после первого кадра HTML.
    private void revealApp() {
        if (appReady) return;
        appReady = true;
        handler.removeCallbacks(revealFallback);

        if (pendingThemeColor != Color.TRANSPARENT) {
            applySystemColor(pendingThemeColor);
        }
        if (splashView != null) {
            splashView.dismiss();
        }
    }

    private void applySystemColor(int color) {
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        applyBarIconMode(color);
        if (webView != null) {
            webView.setBackgroundColor(color);
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
        public boolean hasFont() {
            return fontFile().isFile();
        }

        @JavascriptInterface
        public String getFontName() {
            return fontPrefs().getString(FONT_NAME_KEY, "");
        }

        @JavascriptInterface
        public void pickFont() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                "font/ttf",
                                "font/otf",
                                "application/x-font-ttf",
                                "application/x-font-opentype",
                                "application/octet-stream"
                        });
                        startActivityForResult(intent, REQUEST_FONT);
                    } catch (Exception e) {
                        notifyFont(false, "", "read");
                    }
                }
            });
        }

        @JavascriptInterface
        public void clearFont() {
            fontFile().delete();
            fontPrefs().edit().clear().apply();
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
                    if (!appReady) {
                        pendingThemeColor = color;
                        return;
                    }
                    applySystemColor(color);
                }
            });
        }

        @JavascriptInterface
        public void appReady() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    revealApp();
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
        handler.removeCallbacks(revealFallback);
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /**
     * Нативный экран запуска в стиле Material 3: тёмный фон, знак погоды,
     * название приложения и круговой индикатор загрузки по центру.
     */
    private static class WeatherSplashView extends View {

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF badgeRect = new RectF();
        private final RectF spinnerRect = new RectF();

        private final float density;
        private final int badgeStartColor;
        private final int badgeEndColor;
        private float phase = 0f;
        private boolean dismissed = false;
        private ValueAnimator animator;

        WeatherSplashView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;

            int launchBackground = context.getResources().getColor(R.color.launch_background);
            int launchAccent = context.getResources().getColor(R.color.launch_accent);
            badgeStartColor = context.getResources().getColor(R.color.launch_badge_start);
            badgeEndColor = context.getResources().getColor(R.color.launch_badge_end);

            backgroundPaint.setColor(launchBackground);

            shadowPaint.setColor(0x33000000);

            cloudPaint.setColor(0xFFF7FAFF);

            sunPaint.setColor(0xFFFFD955);

            titlePaint.setColor(0xFFF5F8FF);
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            titlePaint.setTextSize(25f * getResources().getDisplayMetrics().scaledDensity);
            titlePaint.setLetterSpacing(0.01f);

            subtitlePaint.setColor(launchAccent);
            subtitlePaint.setTextAlign(Paint.Align.CENTER);
            subtitlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            subtitlePaint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
            subtitlePaint.setLetterSpacing(0.14f);

            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(4f * density);
            trackPaint.setColor(0x33FFFFFF);

            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(4f * density);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setColor(launchAccent);

            setClickable(true);
            setFocusable(true);

            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1650);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    phase = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        }

        void dismiss() {
            if (dismissed) return;
            dismissed = true;
            if (animator != null) {
                animator.cancel();
            }
            animate()
                    .alpha(0f)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            setVisibility(View.GONE);
                        }
                    })
                    .start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float width = getWidth();
            float height = getHeight();
            float cx = width / 2f;
            float cy = height / 2f;

            canvas.drawRect(0, 0, width, height, backgroundPaint);

            float badgeSize = 96f * density;
            float badgeCy = cy - 76f * density;
            badgeRect.set(
                    cx - badgeSize / 2f,
                    badgeCy - badgeSize / 2f,
                    cx + badgeSize / 2f,
                    badgeCy + badgeSize / 2f
            );

            float radius = 27f * density;

            // Мягкая тень под знаком
            RectF shadowRect = new RectF(badgeRect);
            shadowRect.offset(0, 9f * density);
            canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint);

            badgePaint.setShader(new LinearGradient(
                    badgeRect.left, badgeRect.top, badgeRect.right, badgeRect.bottom,
                    badgeStartColor, badgeEndColor, Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(badgeRect, radius, radius, badgePaint);

            // Солнце
            canvas.drawCircle(
                    cx - 13f * density,
                    badgeCy - 13f * density,
                    12f * density,
                    sunPaint
            );

            // Облако: три круга и скруглённое основание
            canvas.drawCircle(cx - 15f * density, badgeCy + 8f * density, 12f * density, cloudPaint);
            canvas.drawCircle(cx + 1f * density, badgeCy + 2f * density, 16f * density, cloudPaint);
            canvas.drawCircle(cx + 17f * density, badgeCy + 8f * density, 12f * density, cloudPaint);
            RectF cloudBase = new RectF(
                    cx - 27f * density,
                    badgeCy + 8f * density,
                    cx + 29f * density,
                    badgeCy + 24f * density
            );
            canvas.drawRoundRect(cloudBase, 8f * density, 8f * density, cloudPaint);

            // Название и подпись
            canvas.drawText("Pixel Weather", cx, cy + 10f * density, titlePaint);
            canvas.drawText("MATERIAL YOU", cx, cy + 35f * density, subtitlePaint);

            // Круговой индикатор загрузки M3
            float spinnerCy = cy + 88f * density;
            float spinnerRadius = 22f * density;
            spinnerRect.set(
                    cx - spinnerRadius,
                    spinnerCy - spinnerRadius,
                    cx + spinnerRadius,
                    spinnerCy + spinnerRadius
            );
            canvas.drawCircle(cx, spinnerCy, spinnerRadius, trackPaint);

            float rotation = phase * 360f;
            float sweep = 24f + 250f * (0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2));
            canvas.drawArc(spinnerRect, rotation - 90f, sweep, false, arcPaint);
        }
    }
}
