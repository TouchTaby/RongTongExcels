package com.hcpda.rongtongexcel;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.adc.decoder.Barcode2DWithSoft;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import utils.FileUtils;
import utils.LogUtil;


public class MyActivity extends AppCompatActivity implements View.OnClickListener {
    private static String TAG = "TAG";
    private Spinner sp_excel;
    private TextView tv_barcode, tv_total_bar, tv_client, tv_hint;
    private Button bt_load_excel, bt_scan;
    private Map<String, String> map_state = new HashMap();//状态
    private Map<String, String> map_client = new HashMap();//客户
    private Map<String, String> map_total = new HashMap();//总单号
    private Map<String, String> map_row_num = new HashMap();//总单号
    private String select_file = "";
    private List<String> fileList = new ArrayList<>();// excel 文件列表
    private Barcode2DWithSoft barcode2DWithSoft;
    private Vibrator mVibrator;//震动
    private TextToSpeech textToSpeech;

    private FileInputStream in = null;
    private FileOutputStream out = null;
    private HSSFWorkbook hssfWorkbook = null;
    private HSSFSheet sheet = null;
    ExecutorService mExecutorService;
    static int TIME_COLUMNS = 8;
    static int SCAN_COUNTS = 7;
    private boolean isSpeak = true;

    //我是999
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        initView();//初始化控件
        requestPermission();//动态请求相机和文件读写权限
        events();//选择事件
        LogUtil.DEBUG = true;
    }

    private void events() {

        sp_excel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                select_file = parent.getSelectedItem().toString();

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initView() {
        fileList.clear();

        fileList = FileUtils.getSuffixFile(fileList, "/mnt/sdcard/", ".xls");
        if (fileList.size() == 0) {
            //提示加入文件
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setPositiveButton("好的", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            }).setMessage("设备内未发现 xls 格式文件，请核验").setTitle("提示");
            builder.setCancelable(false);
            builder.create();
            builder.show();
        }
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {

            }
        });
        //初始化线程池
//        ThreadFactory threadFactory = Executors.defaultThreadFactory();

//        mExecutorService = new ThreadPoolExecutor(6, //核心线程数量
//                Integer.MAX_VALUE,                                             //最大线程数量
//                30L,                                                //非核心线程等待时间，过时就销毁
//                TimeUnit.MILLISECONDS,                                             //等待时间的单位
//                new SynchronousQueue<Runnable>(),//队列，这个队列接收到任务的时候，会直接提交给线程处理，而不保留它，如果所有线程都在工作怎么办？那就新建一个线程来处理这个任务！
//                threadFactory);
        mExecutorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        mVibrator = (Vibrator) getApplicationContext().getSystemService(Service.VIBRATOR_SERVICE);

        sp_excel = findViewById(R.id.sp_fileName);              //文件列表
        tv_barcode = findViewById(R.id.tv_barcode);             //运单号
        tv_client = findViewById(R.id.tv_client);               //客户
        tv_hint = findViewById(R.id.tv_hint);                   //状态
        tv_total_bar = findViewById(R.id.tv_total_barcode);     //总运单号
        bt_load_excel = findViewById(R.id.bt_load_file);        //加载按钮
        bt_scan = findViewById(R.id.bt_scan);                   //扫描按钮
        bt_scan.setOnClickListener(this);
        bt_load_excel.setOnClickListener(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fileList);
        sp_excel.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_load_file:
                //加载文件备用
                if (select_file != null) {
                    LogUtil.e("加载的文件是", select_file);
                    LogUtil.e("文件长度", fileList.size());

                    new ReadTask().execute(select_file);
                }
                break;
            case R.id.bt_scan:
                //开始扫描条码
                ScanBarcode();
                break;
        }
    }

    public Barcode2DWithSoft.ScanCallback ScanBack = new Barcode2DWithSoft.ScanCallback() {
        @Override
        public void onScanComplete(int i, int length, byte[] bytes) {
            if (length < 1) {
                if (length == -1) {

                } else if (length == 0) {

                } else {

                }
            } else {
                String barCode = new String(bytes, 0, length);
                if (map_client.containsKey(barCode)) {
                    String total_bar = map_total.get(barCode);
                    String client_bar = map_client.get(barCode);
                    String state_bar = map_state.get(barCode);
                    tv_barcode.setText(barCode);        //运单号
                    tv_total_bar.setText(total_bar);    //总运单号
                    tv_client.setText(client_bar);      //客户
                    if (state_bar.equals("")) {
                        tts_speak("通过");
                        tv_hint.setText("通过");
                        tv_hint.setBackgroundColor(Color.GREEN);
                    } else {
                        tts_speak(state_bar);
                        tv_hint.setText(state_bar);
                        tv_hint.setBackgroundColor(Color.YELLOW);
                        mVibrator.vibrate(new long[]{100, 100, 100, 1000}, -1);
                    }
                    LogUtil.e("转换前", map_row_num.get(barCode));
                    LogUtil.e("转换后 ", (map_row_num.get(barCode)));
                    threadWrite(Integer.parseInt(map_row_num.get(barCode)), false, barCode);//线程写入数据
                } else {
                    tts_speak("异常条码");//修改
                    //没有这个条码-处理
                    mVibrator.vibrate(new long[]{100, 100, 100, 1000}, -1);
                    clearText();
                    tv_barcode.setText(barCode);
                    threadWrite(1, true, barCode);//线程写入数据
                    tv_hint.setText("异常码");
                    tv_hint.setBackgroundColor(Color.RED);
                }


            }
            barcode2DWithSoft.stopScan();

        }
    };

    private void threadWrite(int row, boolean isError, String barcode) {
        mExecutorService.execute(new WriteRunnable(row, isError, barcode));

    }



    public class WriteRunnable implements Runnable {
        int row;
        String barcode;
        boolean isError;

        public WriteRunnable(int row, boolean isError, String barcode) {
            this.row = row;
            this.barcode = barcode;
            this.isError = isError;
        }

        @Override
        public void run() {
            boolean is = writeXls(row, isError, barcode);
            LogUtil.e("写入", is);
            if (!is) {
                FileUtils.writeError("写入失败，条码 " + barcode + "---行数---" + row);
            }
        }
    }


    public boolean writeXls(int row, boolean isError, String barcode) {
        LogUtil.e(" 当前操作行数 ", row);
        //获取当前时间
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String date = df.format(new Date());
        try {
            in = new FileInputStream(new File("/mnt/sdcard/" + select_file));
            hssfWorkbook = new HSSFWorkbook(in);
            sheet = hssfWorkbook.getSheetAt(0);
            if (isError) {
                //异常条码处理
                HSSFRow writeRow = sheet.getRow(sheet.getLastRowNum() + 1);
                if (writeRow == null) {
                    writeRow = sheet.createRow(sheet.getLastRowNum() + 1);
                    HSSFCell write_err = writeRow.getCell(0);
                    //同理
                    if (write_err == null) {
                        write_err = writeRow.createCell(writeRow.getPhysicalNumberOfCells());
                    }
                    write_err.setCellValue("异常条码：" + barcode);
                }
            } else {
                //正常数据需要添加盘点次数和时间
                HSSFRow writeRow = sheet.getRow(row);//获取操作的行数
//              Log.e(TAG, "writeExcel: 最后一行是" + sheet.getLastRowNum());
                //如果获取不到现有的行，就新建一行
                if (writeRow == null) {
                    writeRow = sheet.createRow(row);
                }
                HSSFCell write_count = writeRow.getCell(SCAN_COUNTS);
                HSSFCell write_time = writeRow.getCell(TIME_COLUMNS);
                //同理
                if (write_count == null) {
                    write_count = writeRow.createCell(SCAN_COUNTS);
                }
                if (write_time == null) {
                    write_time = writeRow.createCell(TIME_COLUMNS);
                }
                int oldNumber = (int) write_count.getNumericCellValue();
                oldNumber++;//次数叠加
                write_count.setCellValue(oldNumber);//写入次数
                write_time.setCellValue(date);//写入时间
            }
            //再用wb写到输出流
            in.close();
            out = new FileOutputStream("/mnt/sdcard/" + select_file);
            //再用wb写到输出流
            hssfWorkbook.write(out);
            out.close();
            hssfWorkbook.close();
        } catch (FileNotFoundException e) {
            LogUtil.e(" 当前 ", e.toString());
            FileUtils.writeError(e.toString());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            FileUtils.writeError(e.toString());
            LogUtil.e(" 当前 ", e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void tts_speak(String speak) {
        if (isSpeak) {
            textToSpeech.speak(speak, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void clearText() {
        tv_barcode.setText("");
        tv_client.setText("");
        tv_total_bar.setText("");
    }

    /**
     * 加载excel
     *
     * @param file 加载文件
     */
    public boolean loadExcel(String file) {
        LogUtil.e("加载文件 ", file);
        map_client.clear();
        map_state.clear();
        map_total.clear();
        try {

            Workbook wb = new HSSFWorkbook(new FileInputStream("/mnt/sdcard/" + file));
            Sheet sheet = wb.getSheetAt(0);
            int row = sheet.getLastRowNum();//总行数
            HSSFCell cell_barcode;//条码
            HSSFCell cell_state;//状态
            HSSFCell cell_client;//客户
            HSSFCell cell_total;//总单号
            HSSFCell cell_row_num;//行号
            String barcode = null, state, clients, total, row_num;
            for (int i = 1; i <= row; i++) {
                HSSFRow titleRow = (HSSFRow) sheet.getRow(i);

                cell_barcode = titleRow.getCell(2);
                cell_state = titleRow.getCell(1);
                cell_client = titleRow.getCell(4);
                cell_total = titleRow.getCell(3);
                cell_row_num = titleRow.getCell(0);

                barcode = getCellString(cell_barcode);//条码获取


                if (cell_state != null) {
                    state = getCellString(cell_state);
                } else {
                    state = "";
                }

                clients = getCellString(cell_client);
                total = getCellString(cell_total);
                row_num = getCellString(cell_row_num);
                map_state.put(barcode, state);//添加条码对应状态
                map_client.put(barcode, clients);//添加客户
                map_total.put(barcode, total);//添加总单号
                map_row_num.put(barcode, row_num);//添加条码对应行号，用于写入时定位行号

            }
            wb.close();

        } catch (IOException e) {
            LogUtil.e("长度", e.toString());
            FileUtils.writeError("加载异常 " + e.toString());
            return false;
        }
        return true;

    }

    public String getCellString(Cell cell) {
        String value = "";
        if (cell == null) {
            return "";
        }
        switch (cell.getCellTypeEnum()) {
            case _NONE:
                value = "";
                break;
            case STRING:
                value = cell.getStringCellValue();
                break;
            case NUMERIC:
                value = String.valueOf(cell.getNumericCellValue());
                if (value.indexOf(".") > 0) {
                    value = value.replaceAll("0+?$", "");
                    value = value.replaceAll("[.]$", "");
                }
                break;


        }
        return value;

    }

    //读取Excel
    public class ReadTask extends AsyncTask<String, Integer, Boolean> {
        ProgressDialog mypDialog;

        @Override
        protected Boolean doInBackground(String... strings) {

            return loadExcel(strings[0]);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (aBoolean) {
                Toast.makeText(getApplicationContext(), "加载完成", Toast.LENGTH_SHORT).show();
                mypDialog.cancel();
            }
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            mypDialog = new ProgressDialog(MyActivity.this);
            mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mypDialog.setMessage("载入中...");
            mypDialog.setCanceledOnTouchOutside(false);
            mypDialog.show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 280) {
            ScanBarcode();
        } else if (keyCode == 139) {
            ScanBarcode();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void ScanBarcode() {

        if (barcode2DWithSoft != null) {
            barcode2DWithSoft.scan();
            barcode2DWithSoft.setScanCallback(ScanBack);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (barcode2DWithSoft != null) {
            barcode2DWithSoft.stopScan();
            barcode2DWithSoft.close();

        }
        textToSpeech.shutdown();
        if (in != null) {
            try {
                in.close();
                out.close();
                hssfWorkbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcode2DWithSoft == null) {
            new InitTask().execute();
        }
    }

    //读头初始化
    public class InitTask extends AsyncTask<String, Integer, Boolean> {
        ProgressDialog mypDialog;

        @Override
        protected Boolean doInBackground(String... params) {
            // TODO Auto-generated method stub
            if (barcode2DWithSoft == null) {
                barcode2DWithSoft = Barcode2DWithSoft.getInstance();
            }
            boolean result = false;
            if (barcode2DWithSoft != null) {
                result = barcode2DWithSoft.open(MyActivity.this);

            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                barcode2DWithSoft.setParameter(324, 1);
                barcode2DWithSoft.setParameter(300, 0); // Snapshot Aiming
                barcode2DWithSoft.setParameter(361, 0); // Image Capture Illumination
                barcode2DWithSoft.setParameter(6, 1);
                barcode2DWithSoft.setParameter(22, 0);
                barcode2DWithSoft.setParameter(23, 55);
            } else {
                Toast.makeText(MyActivity.this, "上电失败", Toast.LENGTH_SHORT).show();
            }
            mypDialog.cancel();
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            mypDialog = new ProgressDialog(MyActivity.this);
            mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mypDialog.setMessage("init...");
            mypDialog.setCanceledOnTouchOutside(false);
            mypDialog.show();
        }

    }

    /**
     * 请求权限
     */
    private void requestPermission() {
        //判断是否已经赋予权限
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //如果应用之前请求过此权限但用户拒绝了请求，此方法将返回 true。
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {//这里可以写个对话框之类的项向用户解释为什么要申请权限，并在对话框的确认键后续再次申请权限
            } else {

                //申请权限，字符串数组内是一个或多个要申请的权限，1是申请权限结果的返回参数，在onRequestPermissionsResult可以得知申请结果
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.VIBRATE}, 1);
            }
        }
    }
}

