package cn.allen.print;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import static androidx.core.content.PermissionChecker.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {

    private static final String	TAG	= "MainActivity";
    private String serialnumber;
    private static final int REQUEST_CODE = 0x004;
    private AppCompatImageView img;
    private AppCompatTextView status;
    /**
     * 连接状态断开
     */
    private static final int CONN_STATE_DISCONN = 0x007;


    /**
     * 使用打印机指令错误
     */
    private static final int PRINTER_COMMAND_ERROR = 0x008;


    /**
     * ESC查询打印机实时状态指令
     */
    private byte[] esc = { 0x10, 0x04, 0x02 };


    /**
     * CPCL查询打印机实时状态指令
     */
    private byte[] cpcl = { 0x1b, 0x68 };


    /**
     * TSC查询打印机状态指令
     */
    private byte[] tsc = { 0x1b, '!', '?' };

    private static final int	CONN_MOST_DEVICES	= 0x11;
    private static final int	CONN_PRINTER		= 0x12;
    private PendingIntent mPermissionIntent;

    /**
     * 判断打印机所使用指令是否是ESC指令
     */
    private int	id = 0;
    private ThreadPool threadPool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUi();
        addEvent();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ( resultCode == RESULT_OK ){
            switch (requestCode){
                /*蓝牙连接*/
                case Constant.BLUETOOTH_REQUEST_CODE:
                    closeport();
                    /*获取蓝牙mac地址*/
                    String macAddress = data.getStringExtra(BluetoothDeviceList.EXTRA_DEVICE_ADDRESS);
                    /* 初始化话DeviceConnFactoryManager */
                    new DeviceConnFactoryManager.Build()
                            .setId(id)
                            /* 设置连接方式 */
                            .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                            /* 设置连接的蓝牙mac地址 */
                            .setMacAddress(macAddress)
                            .build();
                    /* 打开端口 */
                    Log.d(TAG, "onActivityResult: 连接蓝牙" + id);
                    threadPool = ThreadPool.getInstantiation();
                    threadPool.addTask(new Runnable() {
                        @Override
                        public void run() {
                            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort();
                        }
                    });

                    break;
                case CONN_MOST_DEVICES:
                    id = data.getIntExtra( "id", -1 );
                    if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null &&
                            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState()){
                        status.setText( getString( R.string.str_conn_state_connected ) + "\n" + getConnDeviceInfo() );
                    } else {
                        status.setText( getString( R.string.str_conn_state_disconnect ) );
                    }
                    break;
                }
            }
    }

    private void initUi(){
        img = findViewById(R.id.scan_tv);
        status = findViewById(R.id.connect_tv);
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
                creatScan();
            }
        }
    }

    private void addEvent(){
        findViewById(R.id.bluetooth_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnBluetoothConn();
            }
        });
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
                            creatScan();
                        }
                    }
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
        if(TextUtils.isEmpty(serialnumber)){
            AlertDialog.Builder builder = new AlertDialog.Builder(this).setMessage("条码号为空!");
            builder.setPositiveButton("知道了", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    finish();
                }
            }).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                bitmap = createBarcode(serialnumber);
                handler.sendEmptyMessage(0);
            }
        }).start();
    }

    /**
     * 重新连接回收上次连接的对象，避免内存泄漏
     */
    private void closeport(){
        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null
                &&DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort != null ){
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].reader.cancel();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort.closePort();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort = null;
        }
    }

    private String getConnDeviceInfo()
    {
        String str = "";
        DeviceConnFactoryManager deviceConnFactoryManager = DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id];
        if (deviceConnFactoryManager != null
                && deviceConnFactoryManager.getConnState()){
            if ("BLUETOOTH".equals( deviceConnFactoryManager.getConnMethod().toString())){
                str	+= "BLUETOOTH\n";
                str	+= "MacAddress: " + deviceConnFactoryManager.getMacAddress();
            }
        }
        return(str);
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
    public void btnBluetoothConn(){
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