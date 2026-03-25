package com.example.alagwaapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView ivMenu;
    private View btnLogout;
    
    // Header Stats
    private TextView tvWelcomeName, tvPregnancyStatus, tvStatValue1, tvStatValue2, tvStatValue3;
    private View viewTimelineProgress;
    private View cardProfileWarning;
    
    // Bottom Navigation Icons
    private View navHome, navAppointments, navRecords, navBilling, navProfile, navRatings, navChat;

    private SharedPreferences prefs;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";
    
    private ApiService apiService;
    private Retrofit retrofit;
    private OkHttpClient sharedClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("isLoggedIn", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        
        // Pregnancy Tracker Views
        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        tvPregnancyStatus = findViewById(R.id.tvPregnancyStatus);
        tvStatValue1 = findViewById(R.id.tvStatValue1);
        tvStatValue2 = findViewById(R.id.tvStatValue2);
        tvStatValue3 = findViewById(R.id.tvStatValue3);
        viewTimelineProgress = findViewById(R.id.viewTimelineProgress);
        cardProfileWarning = findViewById(R.id.cardProfileWarning);
        
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        ivMenu = findViewById(R.id.ivMenu);
        btnLogout = findViewById(R.id.btnLogout);
        
        // Reusable Navigation System
        NavigationHelper.setupBottomNav(this);
        
        if (ivMenu != null) ivMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        if (btnLogout != null) btnLogout.setOnClickListener(v -> handleLogout());

        initNetworking();
        fetchClinicBranding();
        fetchDashboardData();
        setupNavigationDrawer();
    }

    private void handleLogout() {
        prefs.edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initNetworking() {
        if (sharedClient == null) {
            sharedClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    String cookies = CookieManager.getInstance().getCookie("http://alagawa.ct.ws/");
                    Request.Builder builder = chain.request().newBuilder()
                            .header("User-Agent", userAgent)
                            .header("Accept", "application/json");
                    if (cookies != null) builder.header("Cookie", cookies);
                    
                    String token = prefs.getString("token", "");
                    if (!token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    
                    okhttp3.HttpUrl originalHttpUrl = chain.request().url();
                    okhttp3.HttpUrl newUrl = originalHttpUrl.newBuilder()
                            .addQueryParameter("tenant_id", String.valueOf(prefs.getInt("tenantId", 1)))
                            .addQueryParameter("role", prefs.getString("role", "patient"))
                            .addQueryParameter("user_id", String.valueOf(prefs.getInt("userId", 0)))
                            .build();
                            
                    builder.url(newUrl);
                    return chain.proceed(builder.build());
                })
                .build();
        }

        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                .baseUrl("http://alagawa.ct.ws/")
                .client(sharedClient)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();
        }

        apiService = retrofit.create(ApiService.class);
    }

    private void setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chat) {
                Intent chatIntent = new Intent(this, ChatActivity.class);
                chatIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(chatIntent);
                overridePendingTransition(0, 0);
            } else if (id == R.id.nav_logout) {
                handleLogout();
            }
            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });
    }

    private void fetchClinicBranding() {
        int tenantId = prefs.getInt("tenantId", 1);
        apiService.getClinicInfo("get_clinic_info", tenantId, "true").enqueue(new Callback<ClinicResponse>() {
            @Override
            public void onResponse(Call<ClinicResponse> call, Response<ClinicResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ClinicResponse clinic = response.body();
                    String primaryColor = clinic.primary_color;
                    if (primaryColor != null && primaryColor.startsWith("#")) {
                        try {
                            int parsedColor = Color.parseColor(primaryColor);
                            viewTimelineProgress.setBackgroundTintList(ColorStateList.valueOf(parsedColor));
                        } catch (Exception e) {
                            Log.e("AlagwaApp", "Branding color error: " + e.getMessage());
                        }
                    }

                    if (clinic.show_appointments != null && "false".equals(clinic.show_appointments)) {
                        if (navAppointments != null) navAppointments.setVisibility(View.GONE);
                    }
                    if (clinic.show_records != null && "false".equals(clinic.show_records)) {
                        if (navRecords != null) navRecords.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(Call<ClinicResponse> call, Throwable t) {
                Log.e("AlagwaApp", "Fetch branding error: " + t.getMessage());
            }
        });
    }

    private void fetchDashboardData() {
        String email = prefs.getString("email", "");
        if (email.isEmpty()) return;

        apiService.getSummary("summary", "true", email).enqueue(new Callback<SummaryResponse>() {
            @Override
            public void onResponse(Call<SummaryResponse> call, Response<SummaryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SummaryResponse.Data data = response.body().data;
                    if (data != null) {
                        // Persist patient_id for booking and records
                        prefs.edit().putInt("patient_id", data.patient_id).apply();
                        
                        tvWelcomeName.setText(data.fullname != null ? data.fullname.toLowerCase() : "patient");
                        tvStatValue1.setText(String.valueOf(data.today_queue));
                        tvStatValue2.setText(String.valueOf(data.upcoming_appointments));
                        tvStatValue3.setText(String.valueOf(data.records_count));
                    
                        SummaryResponse.PregnancyStats stats = data.pregnancy_stats;
                        if(stats != null) {
                            String statusText = "Week " + stats.weeks_pregnant + " / Month " + String.format("%.0f", stats.months);
                            tvPregnancyStatus.setText(statusText);
                            updateTimelineProgress(Math.min((float)stats.weeks_pregnant / 40f, 1.0f));
                        }
                    
                        if(data.fullname == null || data.fullname.isEmpty()) {
                            cardProfileWarning.setVisibility(View.VISIBLE);
                        } else {
                            cardProfileWarning.setVisibility(View.GONE);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<SummaryResponse> call, Throwable t) {
                Log.e("AlagwaApp", "Fetch error: " + t.getMessage());
            }
        });
    }

    private void updateTimelineProgress(float progress) {
        if (viewTimelineProgress == null) return;
        viewTimelineProgress.post(() -> {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) viewTimelineProgress.getLayoutParams();
            params.weight = progress;
            viewTimelineProgress.setLayoutParams(params);
        });
    }
}