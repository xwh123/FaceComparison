package com.example.xuwh.facecomparison;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class MainActivity extends Activity {

    private static final int PICK_FROM_CAMERA = 101;

    @InjectView(R.id.tv_register)
    TextView tvRegister;
    @InjectView(R.id.tv_login)
    TextView tvLogin;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=5b0be8d4");
        ButterKnife.inject(this);
    }


    @OnClick({R.id.tv_register, R.id.tv_login})
    public void onViewClicked(View view) {
        Intent intent = new Intent();
        intent.setClass(this, FaceActivity.class);
        switch (view.getId()) {
            case R.id.tv_register:
                intent.putExtra("type","register");
                break;
            case R.id.tv_login:
                intent.putExtra("type","login");
                break;
        }
        startActivity(intent);
    }

}
