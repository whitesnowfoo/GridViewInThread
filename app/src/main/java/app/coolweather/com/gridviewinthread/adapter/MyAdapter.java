package app.coolweather.com.gridviewinthread.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.List;

import app.coolweather.com.gridviewinthread.R;
import app.coolweather.com.gridviewinthread.Utils.ImageLoader;

/**
 * Created by Owner on 2015-11-14.
 */
public class MyAdapter extends BaseAdapter {

    private Context mContext;
    private List<String> mData;
    private String mDirPath;

    private LayoutInflater mInflater;
    private ImageLoader mImageLoader;

    public MyAdapter(Context context, List<String> mData, String mDirPath) {
        this.mContext = context;
        this.mData = mData;
        this.mDirPath = mDirPath;

        mInflater = LayoutInflater.from(mContext);
        mImageLoader = ImageLoader.getInstance(3, ImageLoader.Type.LIFO);
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if(convertView == null){
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.grid_item,parent,false);
            holder.mImageView = (ImageView)convertView.findViewById(R.id.id_item_image);
            convertView.setTag(holder);
        }else{
            holder = (ViewHolder) convertView.getTag();
        }

        holder.mImageView.setImageResource(R.drawable.friends_sends_pictures_no);

        mImageLoader.loadImage(mDirPath + "/" + mData.get(position),holder.mImageView);
        return convertView;
    }

    private final class ViewHolder{
        ImageView mImageView;
    }

}
