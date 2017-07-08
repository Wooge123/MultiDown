package com.yukang.multidown;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etFileUrl;
    private Button btnDown;
    private int total = 0;
    private boolean downloading = false;
    private File file;
    private URL url;
    private ProgressBar progressBar;

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 0) {
                progressBar.setProgress(msg.arg1);
                if (msg.arg1 == length)
                    Toast.makeText(MainActivity.this, "下载完成", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    });

    private List<HashMap<String, Integer>> threadList;
    private int length;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etFileUrl = (EditText) findViewById(R.id.file_url);
        btnDown = (Button) findViewById(R.id.btn_down);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        threadList = new ArrayList<>();

        btnDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (downloading) {
                    downloading = false;
                    btnDown.setText("下载");
                    return;
                }

                downloading = true;
                btnDown.setText("暂停");

                if (threadList.size() == 0 && downloading) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                url = new URL(etFileUrl.getText().toString());
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                conn.setConnectTimeout(5000);
                                conn.setRequestMethod("GET");
                                conn.setRequestProperty("User-Agent", System.getProperty("http.agent"));
                                length = conn.getContentLength();

                                if (length < 0) {
                                    Log.e("error", "文件不存在");
                                    return;
                                }

                                progressBar.setMax(length);
                                progressBar.setProgress(0);

                                file = new File(Environment.getExternalStorageDirectory(), getFileName(etFileUrl.getText().toString()));
                                RandomAccessFile randomFile = new RandomAccessFile(file, "rw");
                                randomFile.setLength(length);

                                int blockSize = length / 3;
                                for (int i = 0; i < 3; i++) {
                                    int begin = i * blockSize;
                                    int end = (i + 1) * blockSize - 1;

                                    if (i == 2)
                                        end = length;

                                    HashMap<String, Integer> map = new HashMap<>();
                                    map.put("begin", begin);
                                    map.put("end", end);
                                    map.put("finished", 0);
                                    threadList.add(map);

                                    // 创建新的线程下载文件
                                    Thread t = new Thread(new DownloadRunnable(begin, end, file, url, i));
                                    t.start();
                                }
                            } catch (MalformedURLException e) {
                                Toast.makeText(MainActivity.this, "无效的URL", Toast.LENGTH_SHORT).show();
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                } else {
                    //恢复下载
                    for (int i = 0; i < threadList.size(); i++) {
                        HashMap<String, Integer> map = threadList.get(i);
                        int begin = map.get("begin");
                        int end = map.get("end");
                        int finished = map.get("finished");
                        Thread t = new Thread(new DownloadRunnable(begin + finished, end, file, url, i));
                        t.start();
                    }
                }
            }
        });
    }

    private String getFileName(String url) {
        int index = url.lastIndexOf("/") + 1;
        return url.substring(index);
    }

    class DownloadRunnable implements Runnable {

        private int begin;
        private int end;
        private File file;
        private URL url;
        private int id;

        public DownloadRunnable(int begin, int end, File file, URL url, int id) {
            this.begin = begin;
            this.end = end;
            this.file = file;
            this.url = url;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                if (begin > end)
                    return;
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("UserAgent", System.getProperty("http.agent"));
                conn.setRequestProperty("Range", "bytes=" + begin + "-" + end);

                InputStream is = conn.getInputStream();
                byte[] buf = new byte[1024 * 1024];
                RandomAccessFile randomFile = new RandomAccessFile(file, "rw");
                randomFile.seek(begin);
                int len;
                HashMap<String, Integer> map = threadList.get(id);

                while ((len = is.read(buf)) != -1 && downloading) {
                    randomFile.write(buf, 0, len);
                    updateProgress(len);
                    map.put("finished", map.get("finished") + len);
                    Log.d("Downloaded length: ", "" + total);
                }

                is.close();
                randomFile.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized private void updateProgress(int add) {
        total += add;
        handler.obtainMessage(0, total, 0).sendToTarget();
    }
}
