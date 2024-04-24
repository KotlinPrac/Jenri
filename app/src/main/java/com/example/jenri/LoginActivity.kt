package com.example.jenri

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.jenri.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.user.model.User

class LoginActivity : AppCompatActivity() {
    private lateinit var binding : ActivityLoginBinding
    private lateinit var emailLoginResult: ActivityResultLauncher<Intent>
    private lateinit var pendingUser : User

    private val callback: (OAuthToken?, Throwable?) -> Unit = {token, error ->
        if(error != null){
            //로그인 실패
            showErrorToast()
            error.printStackTrace()
        } else if(token != null){
            //로그인 성공
            getKakaoAccountInfo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        KakaoSdk.init(this, "86772f2069952931755af3e6aadfc77f")

        //카카오 토큰 발급 확인 여부 판단으로 카카오 로그인이 되어있는지 확인하는 코드.
        if(AuthApiClient.instance.hasToken()){
            //액세스 토큰이 있다면, 카카오 유저 정보를 가져와서 로그인과 파베 로그인 실행
            UserApiClient.instance.accessTokenInfo { tokenInfo, error ->
                if(error == null){
                    getKakaoAccountInfo()
                }
            }
            //토큰 없으면 카카오 로그인 버튼을 눌러서 로그인 하게끔 함.
        }

        emailLoginResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){

            //이전에는 resultCode를 받아서 어떤 인텐트와 주고받는지 확인이 되어야됬으나, 지금은 LoginActivity와 EmailLoginAcitivty가 인텐트를 주고 받는게 명확하기에 resultCode는 생략
            //대신 코드가 옳바른지에 대한 검증은 필요
            if(it.resultCode == RESULT_OK){
                val email = it.data?.getStringExtra("email")
                if(email == null){
                    showErrorToast()
                    return@registerForActivityResult
                } else{
                    signInFirebase(pendingUser,email)
                }
            }
        }

        binding.logintBtn.setOnClickListener {
            if( UserApiClient.instance.isKakaoTalkLoginAvailable(this)){
                //카카오 로그인
                UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                    if(error !=null){
                        //카카오 로그인 실패
                        //의도적인 로그인 취소 시
                        if(error is ClientError && error.reason == ClientErrorCause.Cancelled){
                            return@loginWithKakaoTalk
                        }
                        UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
                    }else if(token != null){ //로그인이 되었다면,
                        //파이어베이스에 로그인이 안되어있을 때
                        if(Firebase.auth.currentUser == null){
                            getKakaoAccountInfo() //파이어베이스 로그인 시키기
                        }else{
                            //파이어베이스 유저 정보가 있다면,
                            navigateToMainActivity()
                        }
                    }
                }
            }else{
                //카카오 앱이 깔려있지 않다면 웹상에서 카카오 계정으로 로그인
                UserApiClient.instance.loginWithKakaoAccount(this, callback = callback )
            }
        }
    }

    private fun getKakaoAccountInfo() {

        //유저 정보를 가져오기
        UserApiClient.instance.me { user, error ->
            if(error != null){
                //에러 상황
                showErrorToast()
                Log.e("LoginActivity","getKakaoAccountInfo : fail $error")
                error.printStackTrace()
            }else if(user != null){
                //사용자 정보 요청 성공
                Log.e("LoginActivity",
                    "user : 회원번호 : ${user.id} / 이메일 : ${user.kakaoAccount?.email} / 닉네임 : ${user.kakaoAccount?.profile?.nickname} 프로필 사진 : ${user.kakaoAccount?.profile?.thumbnailImageUrl}")
            }
            checkKakaoUserData(user)
        }
    }

    private fun showErrorToast(){
        Toast.makeText(this,"사용자 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun checkKakaoUserData(user: User?) {
        val kakaoEmail = user?.kakaoAccount?.email.orEmpty()

        //이메일이 없다면,
        if(kakaoEmail.isEmpty()){
            //추가로 이메일을 받는 작업
            if (user != null) {
                pendingUser = user
            }
            emailLoginResult.launch(Intent(this,EmailLoginActivity::class.java))

            return
        }

        //이메일이 있다면 user uid와 email로 파이어베이스 로그인
        signInFirebase(user, kakaoEmail)

    }


    //카카오 유저의 UID와 Email로 파베 로그인 구현
    private fun signInFirebase(user: User?, kakaoEmail: String) {
        val uId = user?.id.toString()

        //FB 유저로 가입시키기
        Firebase.auth.createUserWithEmailAndPassword(kakaoEmail,uId).addOnCompleteListener {
            if(it.isSuccessful){
                updateFirebaseDatabase(user)
            }
        }.addOnFailureListener { it ->
            //이미 가입된 계정이라면,
            if(it is FirebaseAuthUserCollisionException){
                Firebase.auth.signInWithEmailAndPassword(kakaoEmail,uId).addOnCompleteListener {
                    if(it.isSuccessful){
                        //로그인 성공하면 데이터 저장
                        updateFirebaseDatabase(user)
                    }else{
                        showErrorToast()
                    }
                }.addOnFailureListener {
                    it.printStackTrace()
                    showErrorToast()
                }

            }else {
                showErrorToast()
            }
        }

    }

    private fun updateFirebaseDatabase(user : User?){
            val uid = Firebase.auth.currentUser?.uid.orEmpty()

            val personMap = mutableMapOf<String, Any>()
            personMap["uid"] = uid
            personMap["name"] = user?.kakaoAccount?.profile?.nickname.orEmpty()
            personMap["profilePhoto"] = user?.kakaoAccount?.profile?.thumbnailImageUrl.orEmpty()

            Firebase.database.reference.child("Person").child(uid).updateChildren(personMap)

            navigateToMainActivity()
    }

    private fun navigateToMainActivity() {
            startActivity(Intent(this,MapActivity::class.java))
    }
}