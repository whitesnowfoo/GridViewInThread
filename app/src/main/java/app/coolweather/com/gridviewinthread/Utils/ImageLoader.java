package app.coolweather.com.gridviewinthread.Utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static android.view.ViewGroup.*;


/**
 * Created by Owner on 2015-11-14.
 */
public class ImageLoader {

    /**
     * ͼƬ����ĺ�����
     */
    private LruCache<String,Bitmap> mLruCache;

    /**
     * �̳߳�
     */
    private ExecutorService mThreadPool;

    /**
     * �̳߳����̵߳�������Ĭ��Ϊ1
     */
    private int mThreadCount = 1;

    /**
     * �жӵĵ��ȷ�ʽ
     */
    private Type mType =  Type.LIFO;

    /**
     * �������
     */
    private LinkedList<Runnable>  mTasks;

    /**
     * ��ѯ���߳�
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;
    /**
     * ������UI�̵߳�Handler�����ڸ�ImageView����ͼƬ
     */
    private Handler mHandler;
    /**
     * ����һ��ֵΪ1����Ϣ������ֹmThreadPoolHandlerδ��ʼ�����
     */
    private volatile Semaphore mSemaphore = new Semaphore(1);

    /**
     * ����һ����Ϣ���������̳߳��ڲ�Ҳ��һ�������̣߳���ֹ����������ٶȹ��죬ʹLIFO������
     */
    private volatile Semaphore mPoolSemaphore;

    private static ImageLoader mInstance;

    public enum Type{
        FIFO,LIFO
    }


    /**
     * ������ø�ʵ������
     * @return
     */
    public static ImageLoader getInstance(){
        if(mInstance == null){
            synchronized(ImageLoader.class){
                if(mInstance == null){
                    mInstance = new ImageLoader(1,Type.LIFO);
                }
            }
        }
        return mInstance;
    }

    /**
     * ������ø�ʵ������
     * @return
     */
    public static ImageLoader getInstance(int mThreadCount,Type type){
        if(mInstance == null){
            synchronized(ImageLoader.class){
                if(mInstance == null){
                    mInstance = new ImageLoader(mThreadCount,type);
                }
            }
        }
        return mInstance;
    }

    private  ImageLoader(int threadCount,Type type) {
        init(threadCount,type);
    }

    private void init(int threadCount, Type type) {

        mPoolThread = new Thread(){
            @Override
            public void run() {
               try {
                   //����һ���ź���
                   mSemaphore.acquire();
               }catch (InterruptedException ie){

               }
                Looper.prepare();
                mPoolThreadHandler = new Handler(){
                    @Override
                    public void handleMessage(Message msg) {
                        mThreadPool.execute(getTask());
                        try {
                            //����һ���ź���
                            mPoolSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                //�ͷ�һ���ź���
                mSemaphore.release();
                Looper.loop();
            }
        };
        mPoolThread.start();

        //���Ӧ�ó�������ڴ�,����û���
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory/8;
        mLruCache = new LruCache<String,Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes()*value.getHeight();
            }
        };

        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mPoolSemaphore = new Semaphore(threadCount);
        mTasks = new LinkedList<Runnable>();
        mType = type == null ? Type.LIFO:type;

    }

    /**
     * ����ͼƬ
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView){
        //��ֹͼƬ����
        imageView.setTag(path);
        //UI�߳�
        if(mHandler == null) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
                    ImageView imageView = holder.imageView;
                    Bitmap bitmap = holder.bitmap;
                    String path = holder.path;
                    if (imageView.getTag().toString().equals(path)) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            };
        }
            Bitmap bm = getBitmapFromLruCache(path);
            if(bm != null){//����л��棬ֱ�ӷ��͸�handler����
                ImgBeanHolder holder = new ImgBeanHolder();
                holder.bitmap = bm;
                holder.imageView = imageView;
                holder.path = path;
                Message message = Message.obtain();
                message.obj = holder;
                mHandler.sendMessage(message);
            }else{//�޻���ʱ���ز��滺��ŷ��͸�handler����
                addTask(new Runnable(){
                    @Override
                    public void run() {
                        ImageSize imageSize = getImageViewWidth(imageView);

                        int reqWidth = imageSize.width;
                        int reqHeight = imageSize.height;

                        Bitmap bm = decodeSampledBitmapFromResource(path,reqWidth,reqHeight);
                        addBitmapToLruCache(path,bm);
                        ImgBeanHolder holder = new ImgBeanHolder();
                        holder.imageView = imageView;
                        holder.bitmap= bm;
                        holder.path = path;
                        Message message = Message.obtain();
                        message.obj = holder;
                        mHandler.sendMessage(message);
                        //�ͷ���Ϣ��
                        mPoolSemaphore.release();

                    }
                });
            }
    }

    /**
     * ���ݼ����inSampleSize,�õ�ѹ�����ͼƬ
     * @param pathName
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private Bitmap decodeSampledBitmapFromResource(String pathName, int reqWidth, int reqHeight) {
       final  BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName,options);
        options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(pathName,options);
        return bitmap;

    }

    /**
     * ����inSampleSize������ѹ��ͼƬ
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;
        if (width>reqWidth && height>reqHeight){
            int widthRatio = Math.round((float)width / (float)reqWidth);
            int heightRatio = Math.round((float)height / (float)reqHeight);
            inSampleSize = Math.max(widthRatio,heightRatio);

        }

        return inSampleSize;
    }

    /**
     * ���һ������
     * @param runnable
     */
    private synchronized void addTask(Runnable runnable){
        if(mPoolThreadHandler == null){
            //������Ϣ������ֹmPoolhreadHandlerΪ��
            try {
                mSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mTasks.add(runnable);
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    /**
     * �õ�һ������
     * @return
     */
    private synchronized Runnable getTask(){
        if (mType == Type.FIFO){
            return mTasks.removeFirst();
        }else if (mType == Type.LIFO){
            return mTasks.removeLast();
        }
        return null;
    }

    /**
     * ����imageView���ѹ�����ʵ��Ŀ�͸�
     * @param imageView
     * @return
     */
    private ImageSize getImageViewWidth(ImageView imageView){
        ImageSize imageSize = new ImageSize();
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
        LayoutParams params = imageView.getLayoutParams();
        int width = params.width == LayoutParams.WRAP_CONTENT?0:imageView.getWidth();
        if(width<0){
            width = params.width;
        }
        if (width<0){
            width = getImageViewFieldValue(imageView,"mMaxWidth");
        }
        if (width<0){
            width = displayMetrics.widthPixels;
        }

        int height = params.height == LayoutParams.WRAP_CONTENT?0:imageView.getHeight();
        if(height<0){
            height = params.height;
        }
        if (height<0){
            height = getImageViewFieldValue(imageView,"mMaxHeight");
        }
        if (height<0){
            height = displayMetrics.heightPixels;
        }
        imageSize.width = width;
        imageSize.height = height;
        return imageSize;
    }

    /**
     * ������ImageView���õ��������߶�
     * @param object
     * @param fieldName
     * @return
     */
    private static int getImageViewFieldValue(Object object, String fieldName) {
        int value = 0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);//���еĳ�Ա����Ϊprivate,�ʱ�����д˲���
            int fieldValue = (Integer)field.get(object);
            if(fieldValue>0 && fieldValue<Integer.MAX_VALUE){
                value = fieldValue;
                Log.e("value",value+"");
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return value;
    }

    /**
     * ��LruCache�л�ȡһ��ͼƬ�����û�з���null
     * @param key
     * @return
     */
    private Bitmap getBitmapFromLruCache(String key){
        return mLruCache.get(key);
    }

    /**
     * ���һ��ͼƬ��LruCache
     * @param key
     * @param bitmap
     */
    private void addBitmapToLruCache(String key,Bitmap bitmap){
        if(getBitmapFromLruCache(key) == null){
            if(bitmap != null){
                mLruCache.put(key,bitmap);
            }
        }
    }



    private class ImgBeanHolder{
        ImageView imageView;
        Bitmap bitmap;
        String path;
    }

    private class ImageSize{
        int width;
        int height;
    }

}
