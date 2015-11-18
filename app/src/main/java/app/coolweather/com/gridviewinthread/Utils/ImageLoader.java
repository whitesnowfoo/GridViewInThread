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
     * 图片缓存的核心类
     */
    private LruCache<String,Bitmap> mLruCache;

    /**
     * 线程池
     */
    private ExecutorService mThreadPool;

    /**
     * 线程池中线程的数量，默认为1
     */
    private int mThreadCount = 1;

    /**
     * 列队的调度方式
     */
    private Type mType =  Type.LIFO;

    /**
     * 任务队列
     */
    private LinkedList<Runnable>  mTasks;

    /**
     * 轮询的线程
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;
    /**
     * 运行在UI线程的Handler，用于给ImageView设置图片
     */
    private Handler mHandler;
    /**
     * 引入一个值为1的信息量，防止mThreadPoolHandler未初始化完成
     */
    private volatile Semaphore mSemaphore = new Semaphore(1);

    /**
     * 引入一个信息量，由于线程池内部也有一个堵塞线程，防止加入任务的速度过快，使LIFO不明显
     */
    private volatile Semaphore mPoolSemaphore;

    private static ImageLoader mInstance;

    public enum Type{
        FIFO,LIFO
    }


    /**
     * 单例获得该实例对象
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
     * 单例获得该实例对象
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
                   //请求一个信号量
                   mSemaphore.acquire();
               }catch (InterruptedException ie){

               }
                Looper.prepare();
                mPoolThreadHandler = new Handler(){
                    @Override
                    public void handleMessage(Message msg) {
                        mThreadPool.execute(getTask());
                        try {
                            //请求一个信号量
                            mPoolSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                //释放一个信号量
                mSemaphore.release();
                Looper.loop();
            }
        };
        mPoolThread.start();

        //获得应用程序最大内存,并获得缓存
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
     * 加载图片
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView){
        //防止图片错乱
        imageView.setTag(path);
        //UI线程
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
            if(bm != null){//如果有缓存，直接发送给handler加载
                ImgBeanHolder holder = new ImgBeanHolder();
                holder.bitmap = bm;
                holder.imageView = imageView;
                holder.path = path;
                Message message = Message.obtain();
                message.obj = holder;
                mHandler.sendMessage(message);
            }else{//无缓存时下载并存缓存才发送给handler加载
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
                        //释放信息量
                        mPoolSemaphore.release();

                    }
                });
            }
    }

    /**
     * 根据计算的inSampleSize,得到压缩后的图片
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
     * 计算inSampleSize，用于压缩图片
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
     * 添加一个任务
     * @param runnable
     */
    private synchronized void addTask(Runnable runnable){
        if(mPoolThreadHandler == null){
            //请求信息量，防止mPoolhreadHandler为空
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
     * 得到一个任务
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
     * 根据imageView获得压缩的适当的宽和高
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
     * 反射获得ImageView设置的最大宽度与高度
     * @param object
     * @param fieldName
     * @return
     */
    private static int getImageViewFieldValue(Object object, String fieldName) {
        int value = 0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);//类中的成员变量为private,故必须进行此操作
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
     * 从LruCache中获取一张图片，如果没有返回null
     * @param key
     * @return
     */
    private Bitmap getBitmapFromLruCache(String key){
        return mLruCache.get(key);
    }

    /**
     * 添加一张图片到LruCache
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
