package com.example.xuwh.facecomparison;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.IdentityListener;
import com.iflytek.cloud.IdentityResult;
import com.iflytek.cloud.IdentityVerifier;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class FaceActivity extends Activity {

    private final int REQUEST_CAMERA_IMAGE = 2;
    public final static int REQUEST_CROP_IMAGE = 3;

    // 拍照得到的照片文件
    private File mPictureFile;

    //采用身份识别接口进行在线人脸识别
    private IdentityVerifier mIdVerifier;
    private Bitmap mImage = null;
    private byte[] mImageData = null;

    @InjectView(R.id.iv_face_bitmap)
    ImageView ivFaceBitmap;
    @InjectView(R.id.tv_face_title)
    TextView tvFaceTitle;
    @InjectView(R.id.ed_account)
    EditText edAccount;
    @InjectView(R.id.btn_face)
    Button btnFace;

    private String mAccount;
    private String type;
    private Toast mToast;
    // 进度对话框
    private ProgressDialog mProDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face);
        ButterKnife.inject(this);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        type = getIntent().getStringExtra("type");
        if ("register".equals(type)) {
            tvFaceTitle.setText("注册");
            btnFace.setText("刷脸注册");
        } else if ("login".equals(type)) {
            tvFaceTitle.setText("登录");
            btnFace.setText("刷脸登录");
        }

        mProDialog = new ProgressDialog(this);
        mProDialog.setCancelable(true);
        mProDialog.setTitle("请稍后");

        mProDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                // cancel进度框时,取消正在进行的操作
                if (null != mIdVerifier) {
                    mIdVerifier.cancel();
                }
            }
        });

        mIdVerifier = IdentityVerifier.createVerifier(this, new InitListener() {
            @Override
            public void onInit(int errorCode) {
                if (ErrorCode.SUCCESS == errorCode) {
                    showToast("引擎初始化成功");
                } else {
                    showToast("引擎初始化失败，错误码：" + errorCode);
                }
            }
        });
    }

    @OnClick(R.id.btn_face)
    public void onViewClicked() {
        // 设置相机拍照后照片保存路径
        mPictureFile = new File(Environment.getExternalStorageDirectory(), "picture" + System.currentTimeMillis() / 1000 + ".jpg");
        // 启动拍照,并保存到临时文件
        Intent mIntent = new Intent();
        mIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        mIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mPictureFile));
        mIntent.putExtra(MediaStore.Images.Media.ORIENTATION, 0);
        startActivityForResult(mIntent, REQUEST_CAMERA_IMAGE);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode != RESULT_OK) {
            return;
        }

        String fileSrc = null;

        if (requestCode == REQUEST_CAMERA_IMAGE) {
            if (null == mPictureFile) {
                showToast("拍照失败，请重试");
                return;
            }
            fileSrc = mPictureFile.getAbsolutePath();
            updateGallery(fileSrc);
            // 跳转到图片裁剪页面
            cropPicture(Uri.fromFile(new File(fileSrc)));

        } else if (requestCode == REQUEST_CROP_IMAGE) {
            // 获取返回数据
            Bitmap bmp = data.getParcelableExtra("data");
            // 若返回数据不为null，保存至本地，防止裁剪时未能正常保存
            if (null != bmp) {
                saveBitmapToFile(bmp);
            }
            // 获取图片保存路径
            fileSrc = getImagePath();
            // 获取图片的宽和高
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            mImage = BitmapFactory.decodeFile(fileSrc, options);

            // 压缩图片
            options.inSampleSize = Math.max(1, (int) Math.ceil(Math.max(
                    (double) options.outWidth / 1024f,
                    (double) options.outHeight / 1024f)));
            options.inJustDecodeBounds = false;
            mImage = BitmapFactory.decodeFile(fileSrc, options);


            // 若mImageBitmap为空则图片信息不能正常获取
            if (null == mImage) {
                showToast("图片信息无法正常获取！");
                return;
            }

            // 部分手机会对图片做旋转，这里检测旋转角度
            int degree = readPictureDegree(fileSrc);
            if (degree != 0) {
                // 把图片旋转为正的方向
                mImage = rotateImage(degree, mImage);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            //可根据流量及网络状况对图片进行压缩
            mImage.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            mImageData = baos.toByteArray();
            ivFaceBitmap.setImageBitmap(mImage);
            verifyRegister();
        }
    }

    /**
     * @dec :验证注册
     * @author :xuwh
     * @param  :
     * @return :
     * @date :
     */
    private void verifyRegister(){

        mAccount = edAccount.getText().toString().trim();
        if (TextUtils.isEmpty(mAccount)){
            showToast("账号不能为空");
            return;
        }

        if (null!= mImageData){
            if ("register".equals(type)) {
                mProDialog.setMessage("注册...");
                mProDialog.show();
                // 设置用户标识，格式为6-18个字符（由字母、数字、下划线组成，不得以数字开头，不能包含空格）。
                // 当不设置时，云端将使用用户设备的设备ID来标识终端用户。
                // 设置人脸注册参数
                // 清空参数
                mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
                // 设置会话场景
                mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
                // 设置会话类型
                mIdVerifier.setParameter(SpeechConstant.MFV_SST, "enroll");
                // 设置用户id
                mIdVerifier.setParameter(SpeechConstant.AUTH_ID, mAccount);
                // 设置监听器，开始会话
                mIdVerifier.startWorking(mEnrollListener);

                // 子业务执行参数，若无可以传空字符传
                StringBuffer params = new StringBuffer();
                // 向子业务写入数据，人脸数据可以一次写入
                mIdVerifier.writeData("ifr", params.toString(), mImageData, 0, mImageData.length);
                // 停止写入
                mIdVerifier.stopWrite("ifr");
            } else if ("login".equals(type)) {
                mProDialog.setMessage("验证中...");
                mProDialog.show();
                // 设置人脸验证参数
                // 清空参数
                mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
                // 设置会话场景
                mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
                // 设置会话类型
                mIdVerifier.setParameter(SpeechConstant.MFV_SST, "verify");
                // 设置验证模式，单一验证模式：sin
                mIdVerifier.setParameter(SpeechConstant.MFV_VCM, "sin");
                // 用户id
                mIdVerifier.setParameter(SpeechConstant.AUTH_ID, mAccount);
                // 设置监听器，开始会话
                mIdVerifier.startWorking(mVerifyListener);

                // 子业务执行参数，若无可以传空字符传
                StringBuffer params = new StringBuffer();
                // 向子业务写入数据，人脸数据可以一次写入
                mIdVerifier.writeData("ifr", params.toString(), mImageData, 0, mImageData.length);
                // 停止写入
                mIdVerifier.stopWrite("ifr");
            }

        }
    }

    /**
     * @dec :人脸注册监听器
     * @author :xuwh
     * @param  :
     * @return :
     * @date :
     */
    private IdentityListener mEnrollListener = new IdentityListener() {
        @Override
        public void onResult(IdentityResult identityResult, boolean b) {
            if (null != mProDialog) {
                mProDialog.dismiss();
            }

            try {
                JSONObject object = new JSONObject(identityResult.getResultString());
                int ret = object.getInt("ret");

                if (ErrorCode.SUCCESS == ret) {
                    showToast("注册成功");
                }else {
                    showToast(new SpeechError(ret).getPlainDescription(true));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(SpeechError speechError) {
            if (null != mProDialog) {
                mProDialog.dismiss();
            }

            showToast(speechError.getPlainDescription(true));
        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }
    };

    /**
     * @dec :人脸登录监听器
     * @author :xuwh
     * @param  :
     * @return :
     * @date :
     */
    private IdentityListener mVerifyListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {

            if (null != mProDialog) {
                mProDialog.dismiss();
            }

            try {
                JSONObject object = new JSONObject(result.getResultString());
                String decision = object.getString("decision");

                if ("accepted".equalsIgnoreCase(decision)) {
                    showToast("登录成功");
                } else {
                    showToast("登录失败");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            if (null != mProDialog) {
                mProDialog.dismiss();
            }
            showToast(error.getPlainDescription(true));
        }

    };

    /**
     * @param :angle 旋转角度  bitmap 原图
     * @return : bitmap 旋转后的图片
     * @dec : 旋转图片
     * @author :xuwh
     * @date :
     */
    private Bitmap rotateImage(int angle, Bitmap bitmap) {
        // 图片旋转矩阵
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        // 得到旋转后的图片
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return resizedBitmap;
    }

    /**
     * @desc: 更新图库
     * @className:FaceActivity
     * @author:xuwh
     * @date:2018/5/29 0:06
     * @version 1.0
     */
    private void updateGallery(String fileName) {

        MediaScannerConnection.scanFile(this, new String[]{fileName}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {

            }
        });
    }

    /**
     * @desc: 裁剪图片
     * @className:FaceActivity
     * @author:xuwh
     * @date:2018/5/29 0:08
     * @version 1.0
     */
    private void cropPicture(Uri uri) {
        Intent innerIntent = new Intent("com.android.camera.action.CROP");
        innerIntent.setDataAndType(uri, "image/*");
        innerIntent.putExtra("crop", "true");// 才能出剪辑的小方框，不然没有剪辑功能，只能选取图片
        innerIntent.putExtra("aspectX", 1); // 放大缩小比例的X
        innerIntent.putExtra("aspectY", 1);// 放大缩小比例的X   这里的比例为：   1:1
        innerIntent.putExtra("outputX", 320);  //这个是限制输出图片大小
        innerIntent.putExtra("outputY", 320);
        innerIntent.putExtra("return-data", true);
        // 切图大小不足输出，无黑框
        innerIntent.putExtra("scale", true);
        innerIntent.putExtra("scaleUpIfNeeded", true);
        File imageFile = new File(getImagePath());
        innerIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(imageFile));
        innerIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        startActivityForResult(innerIntent, REQUEST_CROP_IMAGE);
    }

    /**
     * @desc: 保存裁剪的图片的路径
     * @className:FaceActivity
     * @author:xuwh
     * @date:2018/5/29 0:12
     * @version 1.0
     */
    private String getImagePath() {
        String path;

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            path = this.getApplicationContext().getFilesDir().getAbsolutePath();
        } else {
            path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/msc/";
        }

        if (!path.endsWith("/")) {
            path += "/";
        }

        File folder = new File(path);
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }
        path += "ifd.jpg";
        return path;
    }

    /**
     * @desc: 保存Bitmap至本地
     * @className:FaceActivity
     * @author:xuwh
     * @date:2018/5/29 0:14
     * @version 1.0
     */
    private void saveBitmapToFile(Bitmap bmp) {
        String file_path = getImagePath();
        File file = new File(file_path);
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
            fOut.flush();
            fOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param :path 图片绝对路径
     * @return : degree 旋转角度
     * @dec :读取图片属性：旋转的角度
     * @author :xuwh
     * @date :
     */
    private int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
     * @param :
     * @return :
     * @dec :toast
     * @author :xuwh
     * @date :
     */
    private void showToast(String message) {
        mToast.setText(message);
        mToast.show();
    }
}
