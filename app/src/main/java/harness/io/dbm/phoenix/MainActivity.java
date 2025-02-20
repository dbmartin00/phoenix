package harness.io.dbm.phoenix;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    class SplitTimer {
        public long startInMillis;

        public SplitTimer(long start) {
            this.startInMillis = start;
        }
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
            return packageInfo.versionName; // Returns version name from build.gradle
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage());
            return "Unknown";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String appVersion = getAppVersion(getApplicationContext());
        Log.d(TAG, "Version: " + appVersion);

        final SplitTimer timer = new SplitTimer(System.currentTimeMillis());

        Log.i(TAG, "onCreate");

        String sdkKey = "2d20dfejlhn8ihi1tla2e27bs4ishqa54nt5";
        // Build SDK configuration by default
        SplitClientConfig config = SplitClientConfig.builder()
                .impressionsMode(ImpressionsMode.DEBUG)
                .connectionTimeout(5000)
                .eventFlushInterval(5000)
                .logLevel(SplitLogLevel.DEBUG)
                .build();

        // Create a new user key to be evaluated
        // key represents your internal user id, or the account id that
        // the user belongs to.
        String matchingKey = "dmartin";

        // Create factory
        Key key = new Key("dmartin");
        SplitFactory splitFactory = null;
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

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getAndDrawUrl();
                long clickLatency = System.currentTimeMillis() - timer.startInMillis;
                Map<String, Object> properties = new TreeMap<String, Object>();
                properties.put("version", appVersion);
                String randomChoice = new String[]{"iOS", "Android", "Chrome", "Edge", "Firefox"}[new java.util.Random().nextInt(5)];
                properties.put("platform", randomChoice);
                split.track( "user", "click.latency.ms", clickLatency, properties );
                timer.startInMillis = System.currentTimeMillis();
            }
        });


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public class UrlTreatment {
        String url;
        String treatment;

        public String
        toString() {
            return "treatment: " + this.treatment + " url: " + this.url;
        }
    }



    private UrlTreatment getAndDrawUrl() {
        Log.i(TAG, "getAndDrawUrl()");
        UrlTreatment value = new UrlTreatment();
        Log.i(TAG, "getTreatmentWithConfig");
        SplitResult result = split.getTreatmentWithConfig("murakami", new HashMap<String, Object>());
        value.treatment = result.treatment();
        Log.i(TAG, "treatment: " + value.treatment);
        if(!value.treatment.equalsIgnoreCase("control")) {
            Log.i(TAG, "value.treatment: " + value.treatment);
            TextView ctaView = (TextView) findViewById(R.id.cta);
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
                    } else {
                        // Handle error case (optional)
                    }
                });
            });
        }
    }
}