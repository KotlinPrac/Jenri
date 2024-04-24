package com.example.jenri

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.jenri.databinding.ActivityEmailLoginBinding

class EmailLoginActivity : AppCompatActivity(){

    private lateinit var binding : ActivityEmailLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.doneBtn.setOnClickListener {
            //데이터를 입력함
            if(binding.emailEditText.text.isNotEmpty()){
                val data = Intent().apply {
                    putExtra("email",binding.emailEditText.text.toString())
                }
                setResult(RESULT_OK,data)
                finish()
            } else{
                Toast.makeText(this,"이메일을 입력해주세요",Toast.LENGTH_SHORT).show()
            }
        }
    }
}