package harness.io.dbm.phoenix;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.split.android.client.SplitClient;
import io.split.android.client.SplitClientConfig;
import io.split.android.client.SplitFactory;
import io.split.android.client.SplitFactoryBuilder;
import io.split.android.client.SplitResult;
import io.split.android.client.api.Key;
import io.split.android.client.events.SplitEvent;
import io.split.android.client.events.SplitEventTask;
import io.split.android.client.exceptions.SplitInstantiationException;
import io.split.android.client.service.impressions.ImpressionsMode;
import io.split.android.client.utils.logger.SplitLogLevel;

public class MainActivity extends AppCompatActivity {
    String TAG = "DBM";
    SplitClient split;

    static class SplitTimer {
        public long startInMillis;

        public SplitTimer(long start) {
            this.startInMillis = start;
        }
    }

    public String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String uuid = prefs.getString("device_uuid", null);

        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString("device_uuid", uuid).apply();
        }

        return uuid;
    }

    public String getAppVersion(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = pm.getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            }
            Log.i(TAG, "PackageInfo: " + packageInfo);
            return packageInfo.versionName; // Returns version name from build.gradle
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
            return "Unknown";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String appVersion = getAppVersion(getApplicationContext());
        String deviceId = getDeviceId(getApplicationContext());
        Log.i(TAG, "DeviceId: " + deviceId);
        Log.d(TAG, "Version: " + appVersion);

        final SplitTimer timer = new SplitTimer(System.currentTimeMillis());

        Log.i(TAG, "onCreate");

        String sdkKey = "";

        try {
            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open("splitClientApiKey");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            reader.close();

            sdkKey = stringBuilder.toString();
            Log.d("AuthKey", "Split SDK Key: " + sdkKey);
        } catch (Exception e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }

        // Build SDK configuration by default
        SplitClientConfig config = SplitClientConfig.builder()
                .impressionsMode(ImpressionsMode.DEBUG)
                .impressionsRefreshRate(5)
                .connectionTimeout(5000)
                .eventFlushInterval(5)
                .logLevel(SplitLogLevel.DEBUG)
                .build();

//        final ServiceEndpoints serviceEndpoints = ServiceEndpoints.builder()
//                .apiEndpoint("http://ProxyServerName:Port/api")
//                .eventsEndpoint("http://ProxyServerName:Port/api")
//                .sseAuthServiceEndpoint("http://ProxyServerName:Port/api")
//                .streamingServiceEndpoint("http://ProxyServerName:Port/api")
//                .telemetryServiceEndpoint("http://ProxyServerName:Port/api")
//                .build();
//
//        SplitClientConfig config = SplitClientConfig.builder()
//                .serviceEndpoints(serviceEndpoints)
//                .build();

        // Create factory
        Key key = new Key(deviceId);
//        Key key = new Key("PLACEHOLDER");
        SplitFactory splitFactory;
        try {
            splitFactory = SplitFactoryBuilder.build(sdkKey, key, config, getApplicationContext());
            split = splitFactory.client();

            Log.i(TAG, "Split initialized");
            Log.i(TAG, "baz");
            split.on(SplitEvent.SDK_READY, new SplitEventTask() {
                @Override
                public void onPostExecution(SplitClient client) {
                    Log.i(TAG, "SDK_READY onPostExecution");
                    UrlTreatment result = getAndDrawUrl();
                    Log.i(TAG, result.treatment);
                }
                @Override
                public void onPostExecutionView(SplitClient client) {
                    Log.i(TAG, "onPostExecutionView");
                }
            });

            split.on(SplitEvent.SDK_UPDATE, new SplitEventTask() {
                @Override
                public void onPostExecution(SplitClient client) {
                    Log.i(TAG, "SDK_UPDATE onPostExecution");
                    UrlTreatment result = getAndDrawUrl();
                    Log.i(TAG, result.treatment);
                }
            });
        } catch (SplitInstantiationException sie) {
            Log.e(TAG, sie.getMessage() != null ? sie.getMessage() : "");
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Button nextButton = findViewById(R.id.next);

        nextButton.setOnClickListener(v -> {
            getAndDrawUrl();
            long clickLatency = System.currentTimeMillis() - timer.startInMillis;
            Map<String, Object> properties = new TreeMap<>();
            properties.put("version", appVersion);
            String randomChoice = new String[]{"iOS", "Android", "Chrome", "Edge", "Firefox"}[new java.util.Random().nextInt(5)];
            properties.put("platform", randomChoice);
            split.track( "user", "click.latency.ms", clickLatency, properties );
            timer.startInMillis = System.currentTimeMillis();
        });


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public static class UrlTreatment {
        String url;
        String treatment;

        @NonNull
        public String
        toString() {
            return "treatment: " + this.treatment + " url: " + this.url;
        }
    }



    private UrlTreatment getAndDrawUrl() {
        Log.i(TAG, "getAndDrawUrl()");
        UrlTreatment value = new UrlTreatment();
        Log.i(TAG, "getTreatmentWithConfig");
        SplitResult result = split.getTreatmentWithConfig("murakami", new HashMap<>());
        value.treatment = result.treatment();
        Log.i(TAG, "treatment: " + value.treatment);
        if(!value.treatment.equalsIgnoreCase("control")) {
            Log.i(TAG, "value.treatment: " + value.treatment);
            try {
                JSONObject configs = new JSONObject(result.config());
                JSONArray urls = configs.getJSONArray("images");
                Log.i(TAG, urls.toString());
                value.url = urls.getString(count++ % urls.length());
                ImageView imageView = findViewById(R.id.imageView);

                new ImageDownloader().downloadImage(value.url, imageView);
            } catch (JSONException e) {
                Log.e(TAG, "error with dynamic config: " + e.getMessage());
            }
        }
        return value;
    }

    public static int count = 0;

    public class ImageDownloader {

        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        public synchronized void downloadImage(String imageUrl, ImageView imageView) {
            executorService.execute(() -> {
                Bitmap bitmap = null;
                try {
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();

                    InputStream input = connection.getInputStream();
                    bitmap = BitmapFactory.decodeStream(input);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage() != null ? e.getMessage() : "");
                }

                Bitmap finalBitmap = bitmap;
                mainHandler.post(() -> {
                    if (finalBitmap != null) {
                        imageView.setImageBitmap(finalBitmap);

                        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                                600, 800); // width and height in pixels

                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;

                        imageView.setLayoutParams(params);
                    }
                });
            });
        }
    }
}