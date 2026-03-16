package com.example.alagwaapp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.res.ColorStateList;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvError, tvLoadingStatus, tvWelcomeTitle, tvTagline;
    private ImageView ivClinicLogo, loadingIcon;
    private android.widget.ProgressBar premiumProgress;
    private ValueAnimator progressAnimator;
    private View loadingOverlay;
    private View loginRoot;
    private WebView webViewBypass;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";
    private boolean isSecuritySolved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if already logged in
        SharedPreferences prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("isLoggedIn", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Bind views
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvError = findViewById(R.id.tvError);
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus);
        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        tvTagline = findViewById(R.id.tvTagline);
        ivClinicLogo = findViewById(R.id.ivClinicLogo);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loginRoot = findViewById(R.id.loginRoot);
        webViewBypass = findViewById(R.id.webViewBypass);
        
        // Minimalist Loading views
        loadingIcon = findViewById(R.id.loadingIcon);
        premiumProgress = findViewById(R.id.premiumProgress);

        // Initial state
        btnLogin.setEnabled(false);
        tvLoadingStatus.setText("SYNCING CLINIC ASSETS");

        startElegantLoading(); // Start the elegant animations

        setupWebView();
        solveSecurityChallenge();

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void startElegantLoading() {
        // 1. SUBTLE LOGO BREATHING ANIMATION
        ObjectAnimator alpha = ObjectAnimator.ofFloat(loadingIcon, "alpha", 0.4f, 1.0f, 0.4f);
        alpha.setDuration(2000);
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.start();

        // 2. SMOOTH SLOW PROGRESS ANIMATION
        progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(4000); // Slower for a more "premium" feel
        progressAnimator.setRepeatCount(ValueAnimator.INFINITE);
        progressAnimator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            premiumProgress.setProgress(progress);
        });
        progressAnimator.start();
    }

    private void finalizeInit() {
        if (isSecuritySolved) {
            loadingOverlay.animate().alpha(0f).setDuration(800).withEndAction(() -> {
                loadingOverlay.setVisibility(View.GONE);
                if (progressAnimator != null) progressAnimator.cancel();
                btnLogin.setEnabled(true);
            });
        }
    }

    private void setupWebView() {
        WebSettings settings = webViewBypass.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUserAgentString(userAgent);

        webViewBypass.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && cookies.contains("__test=")) {
                    Log.d("AlagwaApp", "Security Solved!");
                    isSecuritySolved = true;
                    finalizeInit();
                }
            }
        });
    }

    private void solveSecurityChallenge() {
        webViewBypass.loadUrl("http://alagawa.ct.ws/api_login.php?mobile=true");
    }

    private void handleLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter all fields");
            return;
        }

        performLogin(username, password, AppConfig.TENANT_ID);
    }

    private void performLogin(String username, String password, int tenantId) {

        // Show elegant loading screen during auth
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(1.0f);
        tvLoadingStatus.setText("AUTHENTICATING");
        tvError.setVisibility(View.GONE);
        btnLogin.setEnabled(false);
        
        if (progressAnimator != null) progressAnimator.start();

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    String cookies = CookieManager.getInstance().getCookie("http://alagawa.ct.ws/");
                    Request.Builder builder = chain.request().newBuilder()
                            .header("User-Agent", userAgent)
                            .header("Accept", "application/json");
                    if (cookies != null && !cookies.isEmpty()) {
                        builder.header("Cookie", cookies);
                        Log.d("AlagwaApp", "Sending Cookies: " + cookies);
                    } else {
                        Log.e("AlagwaApp", "No Cookies Found for request!");
                    }
                    return chain.proceed(builder.build());
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://alagawa.ct.ws/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();

        ApiService apiService = retrofit.create(ApiService.class);


        apiService.login(username, password, tenantId, "true").enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String rawJson = response.body() != null ? response.body().string() : "";
                    Log.d("AlagwaApp", "Login Response: " + rawJson);
                    
                    JSONObject json = new JSONObject(rawJson);
                    
                    // CHECK FOR MULTIPLE ACCOUNTS
                    if (json.optString("status").equals("multiple_accounts")) {
                        showClinicSelectionDialog(username, password, json.getJSONArray("accounts"));
                        return;
                    }

                    if (json.optBoolean("success")) {
                        String role = json.optString("role");
                        
                        if (!"patient".equalsIgnoreCase(role)) {
                            showError("Access Denied: Patients only.");
                            return;
                        }

                        // Save session
                        SharedPreferences.Editor editor = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).edit();
                        editor.putBoolean("isLoggedIn", true);
                        editor.putInt("userId", json.optInt("user_id"));
                        editor.putInt("tenantId", json.optInt("tenant_id"));
                        editor.putString("email", json.optString("email"));
                        editor.putString("role", role);
                        editor.putString("fullname", json.optString("fullname"));
                        editor.putString("token", json.optString("token")); // NEW: Save JWT Token
                        editor.apply();

                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showError(json.optString("message", "Login failed"));
                    }
                } catch (Exception e) {
                    showError("Error: " + e.getMessage());
                } finally {
                    btnLogin.setEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                showError("Network failure. Please retry.");
                btnLogin.setEnabled(true);
            }
        });
    }

    private void showClinicSelectionDialog(String username, String password, JSONArray accounts) throws Exception {
        loadingOverlay.setVisibility(View.GONE);
        if (progressAnimator != null) progressAnimator.cancel();

        List<String> clinicNames = new ArrayList<>();
        final List<Integer> tenantIds = new ArrayList<>();

        for (int i = 0; i < accounts.length(); i++) {
            JSONObject acc = accounts.getJSONObject(i);
            clinicNames.add(acc.optString("clinic_name"));
            tenantIds.add(acc.optInt("tenant_id"));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
        builder.setTitle("Select your Clinic");
        builder.setItems(clinicNames.toArray(new String[0]), (dialog, which) -> {
            int selectedTenantId = tenantIds.get(which);
            AppConfig.TENANT_ID = selectedTenantId; // Update global config
            Log.d("AlagwaApp", "Selected Clinic Tenant ID: " + selectedTenantId);
            performLogin(username, password, selectedTenantId); // Force exact ID
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            btnLogin.setEnabled(true);
            dialog.dismiss();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void showError(String msg) {
        loadingOverlay.setVisibility(View.GONE);
        if (progressAnimator != null) progressAnimator.cancel();
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
}
