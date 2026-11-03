package oolong.client.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import oolong.client.utils.TiebaUtil;

public class OKSignActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TiebaUtil.startSign(this);
        finish();
    }
}
