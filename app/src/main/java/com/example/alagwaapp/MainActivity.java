package com.example.alagwaapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
    private TextView tvNextBooking;
    
    // Dynamic Views
    private FrameLayout headerFrame;
    private ImageView ivDashboardLogo;
    private TextView tvDashboardTagline;
    private TextView tvDashboardTitle;
    private ImageView navHome;
    private CardView fabAction;
    private CardView cvActionCard;
    private Button btnSchedule;
    private ImageView navLogout;
    private ImageView navAppointments;
    private ImageView navRecords;
    private View mainRoot;
    private View servicesContainer;
    private TextView tvServicesLabel;
    
    private SharedPreferences prefs;

    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";

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

        tvNextBooking = findViewById(R.id.tvNextBooking);
        
        headerFrame = findViewById(R.id.headerFrame);
        ivDashboardLogo = findViewById(R.id.ivDashboardLogo);
        tvDashboardTagline = findViewById(R.id.tvDashboardTagline);
        tvDashboardTitle = findViewById(R.id.tvDashboardTitle);
        navHome = findViewById(R.id.navHome);
        fabAction = findViewById(R.id.fabAction);
        cvActionCard = findViewById(R.id.cvActionCard);
        btnSchedule = findViewById(R.id.btnSchedule);
        navLogout = findViewById(R.id.navLogout);
        navAppointments = findViewById(R.id.navAppointments);
        navRecords = findViewById(R.id.navRecords);
        mainRoot = findViewById(R.id.mainRoot);
        servicesContainer = findViewById(R.id.servicesContainer);
        tvServicesLabel = findViewById(R.id.tvServicesLabel);

        ImageView navAppointments = findViewById(R.id.navAppointments);
        ImageView navRecords = findViewById(R.id.navRecords);

        navLogout.setOnClickListener(v -> handleLogout());
        
        navHome.setOnClickListener(v -> Toast.makeText(this, "You are already home", Toast.LENGTH_SHORT).show());
        navAppointments.setOnClickListener(v -> startActivity(new Intent(this, AppointmentsActivity.class)));
        navRecords.setOnClickListener(v -> startActivity(new Intent(this, RecordsActivity.class)));
        fabAction.setOnClickListener(v -> startActivity(new Intent(this, AppointmentsActivity.class)));
        btnSchedule.setOnClickListener(v -> startActivity(new Intent(this, AppointmentsActivity.class)));

        fetchClinicBranding();
        fetchDashboardData();
    }
    
    private void fetchClinicBranding() {
        OkHttpClient client = new OkHttpClient.Builder()
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
                            .addQueryParameter("email", prefs.getString("email", ""))
                            .addQueryParameter("fullname", prefs.getString("fullname", ""))
                            .build();
                            
                    builder.url(newUrl);
                    
                    return chain.proceed(builder.build());
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://alagawa.ct.ws/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        int tenantId = prefs.getInt("tenantId", 1);
        apiService.getClinicInfo("get_clinic_info", tenantId, "true").enqueue(new Callback<ClinicResponse>() {
            @Override
            public void onResponse(Call<ClinicResponse> call, Response<ClinicResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ClinicResponse clinic = response.body();
                    
                    if (clinic.clinic_name != null && !clinic.clinic_name.equals("null") && !clinic.clinic_name.isEmpty()) {
                        tvDashboardTitle.setText(clinic.clinic_name);
                    } else {
                        tvDashboardTitle.setText("Maternity Clinic");
                    }

                    if (clinic.tagline != null && !clinic.tagline.equals("null") && !clinic.tagline.isEmpty()) {
                        tvDashboardTagline.setText(clinic.tagline);
                    }
                    
                    if (clinic.primary_color != null && !clinic.primary_color.equals("null") && clinic.primary_color.startsWith("#")) {
                        try {
                            int parsedColor = Color.parseColor(clinic.primary_color);
                            headerFrame.setBackgroundColor(parsedColor);
                            navHome.setColorFilter(parsedColor);
                            fabAction.setCardBackgroundColor(parsedColor);
                            cvActionCard.setCardBackgroundColor(parsedColor);
                            btnSchedule.setTextColor(parsedColor);
                            
                            // Adjust contrast for header text
                            int headerTextCol = isColorDark(parsedColor) ? Color.WHITE : Color.parseColor("#1e293b");
                            tvDashboardTitle.setTextColor(headerTextCol);
                            tvDashboardTagline.setTextColor(headerTextCol);
                            tvDashboardTagline.setAlpha(isColorDark(parsedColor) ? 0.8f : 0.6f);
                        } catch (Exception e) {
                            Log.e("AlagwaApp", "Primary color parse error: " + e.getMessage());
                        }
                    }

                    // Apply Background Theme
                    if (clinic.bg_color != null && clinic.bg_color.startsWith("#")) {
                        try {
                            int bgColor = Color.parseColor(clinic.bg_color);
                            mainRoot.setBackgroundColor(bgColor);
                            
                            // Adjust contrast for Root text
                            int rootTextCol = isColorDark(bgColor) ? Color.WHITE : Color.parseColor("#1e293b");
                            tvServicesLabel.setTextColor(rootTextCol);
                            
                        } catch (Exception e) {
                            Log.e("AlagwaApp", "Bg color parse error: " + e.getMessage());
                        }
                    }

                    // Apply Feature Toggles
                    if ("false".equals(clinic.show_appointments)) {
                        navAppointments.setVisibility(View.GONE);
                        fabAction.setVisibility(View.GONE);
                    } else {
                        navAppointments.setVisibility(View.VISIBLE);
                        fabAction.setVisibility(View.VISIBLE);
                    }

                    if ("false".equals(clinic.show_records)) {
                        navRecords.setVisibility(View.GONE);
                    } else {
                        navRecords.setVisibility(View.VISIBLE);
                    }

                    if ("false".equals(clinic.show_services)) {
                        servicesContainer.setVisibility(View.GONE);
                        tvServicesLabel.setVisibility(View.GONE);
                    } else {
                        servicesContainer.setVisibility(View.VISIBLE);
                        tvServicesLabel.setVisibility(View.VISIBLE);
                    }
                    
                    if (clinic.logo_url != null && !clinic.logo_url.isEmpty() && !clinic.logo_url.equals("null")) {
                        try {
                            String imgCookies = CookieManager.getInstance().getCookie("http://alagawa.ct.ws/");
                            GlideUrl glideUrl = new GlideUrl(
                                clinic.logo_url, 
                                new LazyHeaders.Builder()
                                    .addHeader("User-Agent", userAgent)
                                    .addHeader("Cookie", imgCookies != null ? imgCookies : "")
                                    .build()
                            );
                            
                            Glide.with(MainActivity.this)
                                 .load(glideUrl)
                                 .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                 .skipMemoryCache(true)
                                 .placeholder(android.R.drawable.ic_menu_myplaces)
                                 .into(ivDashboardLogo);
                            ivDashboardLogo.setPadding(0, 0, 0, 0);
                        } catch (Exception e) {
                            Log.e("AlagwaApp", "Glide error: " + e.getMessage());
                        }
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
        if (email.isEmpty()) {
            Toast.makeText(this, "Session error. Please logout and login again.", Toast.LENGTH_LONG).show();
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
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
                            .addQueryParameter("email", prefs.getString("email", ""))
                            .addQueryParameter("fullname", prefs.getString("fullname", ""))
                            .build();
                            
                    builder.url(newUrl);

                    return chain.proceed(builder.build());
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://alagawa.ct.ws/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        apiService.getSummary("summary", "true", email).enqueue(new Callback<SummaryResponse>() {
            @Override
            public void onResponse(Call<SummaryResponse> call, Response<SummaryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SummaryResponse.Data data = response.body().data;
                    if (data != null) {
                        tvNextBooking.setText(data.next_booking != null ? data.next_booking : "No upcoming visit");
                    }
                } else {
                    Log.e("AlagwaApp", "Server returned error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<SummaryResponse> call, Throwable t) {
                Log.e("AlagwaApp", "Fetch error: " + t.getMessage());
                Toast.makeText(MainActivity.this, "Network error. Try again later.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleLogout() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        startActivity(new Intent(MainActivity.this, LoginActivity.class));
        finish();
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
}