package com.example.jenri

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.jenri.databinding.ActivityMainBinding
import com.kakao.sdk.common.util.Utility

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var keyHash = Utility.getKeyHash(this) //해시 키 값 가져오기
        Log.e("keyHasy",keyHash.toString())

        startActivity(Intent(this,LoginActivity::class.java))
    }
}
