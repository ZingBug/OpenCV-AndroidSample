package com.example.lzh.tryopencv;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final int TAKE_PHOTO=1;
    public static final int CHOOSE_FROM_ALBUM=2;
    private ImageView picture;
    private Button grayPhoto;
    private Uri imageUri;
    private static boolean flag=false;
    private static boolean isFirst=true;
    private static final String TAG = "H.J.Y";
    private Bitmap srcBitmap;
    private Bitmap grayBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        picture=(ImageView) findViewById(R.id.picture_show);
        Button takePhoto=(Button)findViewById(R.id.take_photo);
        Button chooseFromAlbum=(Button)findViewById(R.id.choose_from_album);
        grayPhoto=(Button)findViewById(R.id.gray);
        takePhoto.setOnClickListener(this);
        chooseFromAlbum.setOnClickListener(this);
        grayPhoto.setOnClickListener(this);
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
            case R.id.take_photo: {
                File outputImage=new File(getExternalCacheDir(),"output_image.jpg");
                try{
                    if(outputImage.exists())
                    {
                        outputImage.delete();
                    }
                    outputImage.createNewFile();
                }catch (IOException e)
                {
                    e.printStackTrace();
                }
                if(Build.VERSION.SDK_INT>=24)
                {
                    imageUri= FileProvider.getUriForFile(MainActivity.this,"com.example.lzh.tryopencv.fileprovider",outputImage);
                }else
                {
                    imageUri=Uri.fromFile(outputImage);
                }
                openCamera();
                break;
            }
            case R.id.choose_from_album: {
                if(ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(MainActivity.this,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
                }
                else
                {
                    openAlbum();
                }
                break;
            }
            case R.id.gray:{
                if(isFirst)
                {
                    processRgb2Gray();
                    isFirst=false;
                }
                if(flag)
                {
                    picture.setImageBitmap(srcBitmap);
                    grayPhoto.setText("灰度化");
                    flag=false;
                }
                else
                {
                    picture.setImageBitmap(grayBitmap);
                    grayPhoto.setText("查看原图");
                    flag=true;
                }
                break;
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults)
    {
        switch (requestCode)
        {
            case 1:
                if(grantResults.length>0&&grantResults[0]== PackageManager.PERMISSION_GRANTED)
                {
                    openAlbum();
                }
                else
                {
                    Toast.makeText(this,"You denied the permission",Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }
    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data)
    {
        switch (requestCode)
        {
            case TAKE_PHOTO:
                if(resultCode==RESULT_OK)
                {
                    try
                    {
                        //将拍摄的照片显示出来
                        Bitmap bitmap= BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                        picture.setImageBitmap(bitmap);
                        isFirst=true;
                        grayPhoto.setText("灰度化");
                    }
                    catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                }
                break;
            case CHOOSE_FROM_ALBUM:
                if(resultCode==RESULT_OK)
                {
                    if(Build.VERSION.SDK_INT>=19)
                    {
                        handleImageOnkitkat(data);
                    }
                    else
                    {
                        handleImageBeforeKitKat(data);
                    }
                }
            default:
                break;
        }
    }
    @TargetApi(19)
    private void handleImageOnkitkat(Intent data)
    {
        String imagePath=null;
        Uri uri=data.getData();
        if(DocumentFile.isDocumentUri(this,uri))
        {
            //如果是document类型的Uri，则通过document id来处理
            String docId= DocumentsContract.getDocumentId(uri);
            if("com.android.providers.media.documents".equals(uri.getAuthority()))
            {
                String id=docId.split(":")[1];
                String selection=MediaStore.Images.Media._ID+"="+id;
                imagePath=getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,selection);
            }
            else if("com.android.providers.downloads.documents".equals(uri.getAuthority()))
            {
                Uri contentUri= ContentUris.withAppendedId(Uri.parse("content://downloads/pulblic_downloads"),Long.valueOf(docId));
                imagePath=getImagePath(contentUri,null);
            }
        }
        else if("content".equalsIgnoreCase(uri.getScheme()))
        {
            //如果是content类型的Uri，则通过普通方式来处理
            imagePath=getImagePath(uri,null);
        }
        else if("file".equalsIgnoreCase(uri.getScheme()))
        {
            //如果是file类型的Uri，直接获取图片路径即可
            imagePath=uri.getPath();
        }
        displayImage(imagePath);
    }
    private void handleImageBeforeKitKat(Intent data)
    {
        Uri uri=data.getData();
        String imagePath=getImagePath(uri,null);
        displayImage(imagePath);
    }
    private String getImagePath(Uri uri,String selection)
    {
        String path=null;
        //通过Uri和selection来获取真实的图片路径
        Cursor cursor=getContentResolver().query(uri,null,selection,null,null);
        if(cursor!=null)
        {
            if(cursor.moveToFirst())
            {
                path=cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }
    private void displayImage(String imagePath)
    {
        if(imagePath!=null)
        {
            Bitmap bitmap=BitmapFactory.decodeFile(imagePath);
            picture.setImageBitmap(bitmap);
            isFirst=true;
            grayPhoto.setText("灰度化");
        }
        else
        {
            Toast.makeText(this,"failed to get image",Toast.LENGTH_SHORT).show();
        }
    }
    private void openAlbum()
    {
        Intent intent=new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent,CHOOSE_FROM_ALBUM);
    }
    private void openCamera()
    {
        //启动相机程序
        Intent intent=new Intent("android.media.action.IMAGE_CAPTURE");
        intent.putExtra(MediaStore.EXTRA_OUTPUT,imageUri);
        startActivityForResult(intent,TAKE_PHOTO);
    }
    @Override
    protected void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug())
        {// 默认加载opencv_java.so库
        }
    }
    private void processRgb2Gray()
    {
        Mat rgbMat=new Mat();
        Mat grayMat=new Mat();
        srcBitmap=((BitmapDrawable)picture.getDrawable()).getBitmap();
        grayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.RGB_565);
        Utils.bitmapToMat(srcBitmap, rgbMat);//convert original bitmap to Mat, R G B.
        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);//rgbMat to gray grayMat
        Utils.matToBitmap(grayMat, grayBitmap); //convert mat to bitmap
        //Log.i(TAG, "procSrc2Gray sucess...");
    }

}
