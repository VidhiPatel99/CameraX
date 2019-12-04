package com.example.cameraxdemo.activity

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.cameraxdemo.R
import com.example.cameraxdemo.activity.MainActivity.Companion.MEDIA_PATH
import com.example.cameraxdemo.activity.MainActivity.Companion.MEDIA_TYPE
import kotlinx.android.synthetic.main.activity_image_preview.*

class ImagePreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val mediaType = intent.getStringExtra(MEDIA_TYPE)
        val imagePath = intent.getStringExtra(MEDIA_PATH)

        if (mediaType == "image") {
            Glide.with(this)
                .load(imagePath)
                .into(ivCapturedImage)
        } else {
            Toast.makeText(this, imagePath, Toast.LENGTH_SHORT).show()
            videoView.setVideoPath(imagePath)
            videoView.start()
        }
    }
}
