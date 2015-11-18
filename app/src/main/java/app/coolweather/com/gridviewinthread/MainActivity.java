package app.coolweather.com.gridviewinthread;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import app.coolweather.com.gridviewinthread.adapter.MyAdapter;


public class MainActivity extends Activity {

    private ProgressDialog mProgressDialog;
    private ImageView mImageView;

    /**
     * �洢�ļ����е�ͼƬ����
     */
    private int mPicsSize;
    /**
     * ͼƬ���������ļ���
     */
    private File mImgDir;
    /**
     * ���е�ͼƬ
     */
    private List<String> mImgs;

    private GridView mGirdView;
    private ListAdapter mAdapter;
    /**
     * ��ʱ�ĸ����࣬���ڷ�ֹͬһ���ļ��еĶ��ɨ��
     */
    private HashSet<String> mDirPaths = new HashSet<String>();

    private Handler mHandler = new Handler()
    {
        public void handleMessage(android.os.Message msg)
        {
            mProgressDialog.dismiss();
            mImgs = Arrays.asList(mImgDir.list(new FilenameFilter()
            {
                @Override
                public boolean accept(File dir, String filename)
                {
                    if (filename.endsWith(".jpg"))
                        return true;
                    return false;
                }
            }));
            /**
             * ���Կ����ļ��е�·����ͼƬ��·���ֿ����棬����ļ������ڴ�����ģ�
             */
            mAdapter = new MyAdapter(getApplicationContext(), mImgs,
                    mImgDir.getAbsolutePath());
            mGirdView.setAdapter(mAdapter);
        };
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mGirdView = (GridView) findViewById(R.id.id_grid_view);
        getImages();

    }

    /**
     * ����ContentProviderɨ���ֻ��е�ͼƬ���˷��������������߳��� ���ͼƬ��ɨ�裬���ջ��jpg�����Ǹ��ļ���
     */
    private void getImages()
    {
        if (!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED))
        {
            Toast.makeText(this, "�����ⲿ�洢", Toast.LENGTH_SHORT).show();
            return;
        }
        // ��ʾ������
        mProgressDialog = ProgressDialog.show(this, null, "���ڼ���...");

        new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                Uri mImageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver mContentResolver = MainActivity.this
                        .getContentResolver();

                // ֻ��ѯjpeg��png��ͼƬ
                Cursor mCursor = mContentResolver.query(mImageUri, null,
                        MediaStore.Images.Media.MIME_TYPE + "=? or "
                                + MediaStore.Images.Media.MIME_TYPE + "=?",
                        new String[] { "image/jpeg", "image/png" },
                        MediaStore.Images.Media.DATE_MODIFIED);

                while (mCursor.moveToNext())
                {
                    // ��ȡͼƬ��·��
                    String path = mCursor.getString(mCursor
                            .getColumnIndex(MediaStore.Images.Media.DATA));
                    // ��ȡ��ͼƬ�ĸ�·����
                    File parentFile = new File(path).getParentFile();
                    String dirPath = parentFile.getAbsolutePath();

                    //����һ��HashSet��ֹ���ɨ��ͬһ���ļ��У���������жϣ�ͼƬ�����������൱�ֲ���~~��
                    if(mDirPaths.contains(dirPath))
                    {
                        continue;
                    }
                    else
                    {
                        mDirPaths.add(dirPath);
                    }

                    int picSize = parentFile.list(new FilenameFilter()
                    {
                        @Override
                        public boolean accept(File dir, String filename)
                        {
                            if (filename.endsWith(".jpg"))
                                return true;
                            return false;
                        }
                    }).length;
                    if (picSize > mPicsSize)
                    {
                        mPicsSize = picSize;
                        mImgDir = parentFile;
                    }
                }
                mCursor.close();
                //ɨ����ɣ�������HashSetҲ�Ϳ����ͷ��ڴ���
                mDirPaths = null ;
                // ֪ͨHandlerɨ��ͼƬ���
                mHandler.sendEmptyMessage(0x110);

            }
        }).start();

    }
}
