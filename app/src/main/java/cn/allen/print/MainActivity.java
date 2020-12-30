package cn.allen.print;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import static androidx.core.content.PermissionChecker.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {

    private String serialnumber;
    private static final int REQUEST_CODE = 0x004;
    private AppCompatImageView img;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        img = findViewById(R.id.scan_tv);
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)!=PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, REQUEST_CODE);
            return;
        }
        Intent intent = getIntent();
        String action = intent.getAction();
        if(Intent.ACTION_VIEW.equals(action)){
            Uri uri = intent.getData();
            if(uri!=null){
                serialnumber = uri.getQueryParameter("serialnumber");
                AlertDialog.Builder builder = new AlertDialog.Builder(this).setMessage("serialnumber:"+serialnumber);
                builder.show();
            }
        }
        creatScan();
    }
    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE: {
                if(checkIsOk(grantResults)){
                    Intent intent = getIntent();
                    String action = intent.getAction();
                    if(Intent.ACTION_VIEW.equals(action)){
                        Uri uri = intent.getData();
                        if(uri!=null){
                            serialnumber = uri.getQueryParameter("serialnumber");
                            AlertDialog.Builder builder = new AlertDialog.Builder(this).setMessage("serialnumber:"+serialnumber);
                            builder.show();
                        }
                    }
                    creatScan();
                }else{
                    finish();
                }
                break;
            }
        }
    }

    private boolean checkIsOk(int[] grantResults){
        boolean isok = true;
        for(int i:grantResults){
            isok = isok && (i == PackageManager.PERMISSION_GRANTED);
        }
        return isok;
    }

    Bitmap bitmap;
    private void creatScan(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                bitmap = createBarcode(TextUtils.isEmpty(serialnumber)?"01123124":serialnumber);
                handler.sendEmptyMessage(0);
            }
        }).start();
    }

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(){

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case 0:
                    if(bitmap!=null){
                        img.setImageBitmap(bitmap);
                    }
                    break;
            }
        }
    };

    /**
     * 蓝牙连接
     */
    public void btnBluetoothConn( View view ){
        startActivityForResult( new Intent( this, BluetoothDeviceList.class ), Constant.BLUETOOTH_REQUEST_CODE );
    }

    /**
     * 生成条形码（不支持中文）
     *
     * @param content
     * @return
     */
    public static Bitmap createBarcode(String content) {
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, 3000, 700);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? 0xff000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
        }
        return null;
    }
}