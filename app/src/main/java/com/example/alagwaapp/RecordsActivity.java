package com.example.alagwaapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class RecordsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);
        NavigationHelper.setupBottomNav(this);
    }
}
