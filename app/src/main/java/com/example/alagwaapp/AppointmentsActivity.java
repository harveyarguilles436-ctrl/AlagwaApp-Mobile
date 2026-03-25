package com.example.alagwaapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppointmentsActivity extends AppCompatActivity {

    private RecyclerView rvAppointments;
    private RelativeLayout layoutEmpty, layoutLoading, cardNewBooking;
    private AppointmentAdapter adapter;
    private ApiService apiService;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        rvAppointments = findViewById(R.id.rvAppointments);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutLoading = findViewById(R.id.layoutLoading);
        cardNewBooking = findViewById(R.id.cardNewBooking);
        
        NavigationHelper.setupBottomNav(this);
        cardNewBooking.setOnClickListener(v -> showBookAppointmentDialog());

        rvAppointments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppointmentAdapter(new ArrayList<>());
        rvAppointments.setAdapter(adapter);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        
        initNetworking();
        fetchAppointments();
    }

    private void initNetworking() {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                String cookies = CookieManager.getInstance().getCookie("http://alagawa.ct.ws/");
                Request.Builder builder = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "application/json");
                if (cookies != null) builder.header("Cookie", cookies);
                
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
                
                okhttp3.HttpUrl originalHttpUrl = chain.request().url();
                okhttp3.HttpUrl newUrl = originalHttpUrl.newBuilder()
                        .addQueryParameter("tenant_id", String.valueOf(prefs.getInt("tenantId", 1)))
                        .addQueryParameter("role", prefs.getString("role", "patient"))
                        .addQueryParameter("user_id", String.valueOf(prefs.getInt("userId", 0)))
                        .build();
                return chain.proceed(builder.url(newUrl).build());
            })
            .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://alagawa.ct.ws/") 
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();
        apiService = retrofit.create(ApiService.class);
    }

    private void fetchAppointments() {
        String email = prefs.getString("email", "");
        if (email.isEmpty()) return;

        layoutLoading.setVisibility(View.VISIBLE);
        
        // Use raw response first to handle InfinityFree's unpredictable responses
        apiService.getBookingsRaw("list", "true").enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                layoutLoading.setVisibility(View.GONE);
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String rawJson = response.body().string();
                        Log.d("AlagwaApp", "Raw Bookings: " + rawJson);
                        
                        // Parse manually to handle "Expected BEGIN_OBJECT" errors
                        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setLenient().create();
                        try {
                            AppointmentResponse apResponse = gson.fromJson(rawJson, AppointmentResponse.class);
                            if (apResponse != null && apResponse.data != null) {
                                adapter.updateList(apResponse.data);
                                layoutEmpty.setVisibility(View.GONE);
                                rvAppointments.setVisibility(View.VISIBLE);
                            } else {
                                handleEmptyState();
                            }
                        } catch (Exception e) {
                            Log.e("AlagwaApp", "Parse Error, checking if empty string data: " + e.getMessage());
                            handleEmptyState();
                        }
                    } else {
                        handleEmptyState();
                    }
                } catch (Exception e) {
                    Log.e("AlagwaApp", "Fatal Fetch Error: " + e.getMessage());
                    handleEmptyState();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                layoutLoading.setVisibility(View.GONE);
                handleEmptyState();
                Log.e("AlagwaApp", "Network Failure: " + t.getMessage());
            }
        });
    }

    private void handleEmptyState() {
        layoutEmpty.setVisibility(View.VISIBLE);
        rvAppointments.setVisibility(View.GONE);
    }

    private void showBookAppointmentDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_book_appointment, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TransparentDialog).setView(dialogView).create();

        Spinner spinnerService = dialogView.findViewById(R.id.spinnerService);
        Spinner spinnerTime = dialogView.findViewById(R.id.spinnerTime);
        EditText etDate = dialogView.findViewById(R.id.etDate);
        EditText etNotes = dialogView.findViewById(R.id.etNotes);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView btnSubmit = dialogView.findViewById(R.id.btnSubmit);

        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String date = year + "-" + String.format("%02d", (month + 1)) + "-" + String.format("%02d", dayOfMonth);
                etDate.setText(date);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String date = etDate.getText().toString();
            if (date.isEmpty()) {
                Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
                return;
            }

            int patientId = prefs.getInt("patient_id", 0);
            if (patientId == 0) {
                Toast.makeText(this, "Profile data missing. Please refresh dashboard.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                return;
            }

            layoutLoading.setVisibility(View.VISIBLE);
            apiService.createAppointment("create", "true", patientId, date, 
                    spinnerTime.getSelectedItem().toString(), 
                    spinnerService.getSelectedItem().toString(), 
                    etNotes.getText().toString()
            ).enqueue(new Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                    layoutLoading.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        Toast.makeText(AppointmentsActivity.this, "Appointment booked successfully!", Toast.LENGTH_SHORT).show();
                        fetchAppointments(); // Refresh list
                    } else {
                        Toast.makeText(AppointmentsActivity.this, "Error: Booking failed.", Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                }

                @Override
                public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                    layoutLoading.setVisibility(View.GONE);
                    Toast.makeText(AppointmentsActivity.this, "Network error: booking failed.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }

    private static class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.ViewHolder> {
        private List<AppointmentResponse.Appointment> list;
        public AppointmentAdapter(List<AppointmentResponse.Appointment> list) { this.list = list; }
        public void updateList(List<AppointmentResponse.Appointment> newList) { this.list = newList; notifyDataSetChanged(); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppointmentResponse.Appointment item = list.get(position);
            holder.tvService.setText(item.serviceType != null ? item.serviceType : "General Checkup");
            holder.tvDateTime.setText(item.date + " • " + item.time);
            
            String status = item.status != null ? item.status.toUpperCase() : "PENDING";
            holder.tvStatus.setText(status);
            if (status.contains("CANCELLED") || status.contains("DECLINED")) {
                holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(0x20FF5252));
                holder.tvStatus.setTextColor(0xFFFF5252);
            } else if (status.contains("CONFIRMED") || status.contains("COMPLETED")) {
                holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(0x204CAF50));
                holder.tvStatus.setTextColor(0xFF4CAF50);
            } else {
                holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(0x20A17E64));
                holder.tvStatus.setTextColor(0xFFA17E64);
            }
        }

        @Override public int getItemCount() { return list.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvService, tvDateTime, tvStatus;
            ViewHolder(View v) { super(v); 
                tvService = v.findViewById(R.id.tvServiceType);
                tvDateTime = v.findViewById(R.id.tvDateTime);
                tvStatus = v.findViewById(R.id.tvStatus);
            }
        }
    }
}
