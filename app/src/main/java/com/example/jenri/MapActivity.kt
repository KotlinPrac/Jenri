package com.example.jenri

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.jenri.databinding.ActivityMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MapActivity : AppCompatActivity(), OnMapReadyCallback, OnMarkerClickListener {
    private lateinit var binding : ActivityMapBinding
    private lateinit var googleMap : GoogleMap
    private lateinit var fusedLocationClient : FusedLocationProviderClient

    private var trackingPersonId: String = ""
    private val markerMap = hashMapOf<String, Marker>()


    //권한 요청
    private val locationPermissionRequest  = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ->{
                //fine location 권한이 있다.
                getCurrentLocation()
            }

            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) ->{
                //Coase Location 권한이 있다.
                getCurrentLocation()
            }
            else ->{
                // TODO 설정으로 보내기 or 교육용 팝업을 띄워서 다시 권한 요청하기
            }
        }

    }

    private val locationCallback = object: LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {

            //새로 요청된 위치 정보
            for(location in locationResult.locations){

                Log.e("MapAcitivty","onLocationResult : ${location.latitude} ${location.longitude}")

                //내 위치를 가져오기 위해 사용자 정보 가져오기
                val uid = Firebase.auth.currentUser?.uid.orEmpty()

                //uid가 없다면 종료
                if(uid == ""){
                    finish()
                }
                val locationMap = mutableMapOf<String, Any>()
                locationMap["latitude"] = location.latitude
                locationMap["longitude"] = location.longitude
                Firebase.database.reference.child("Person").child(uid).updateChildren(locationMap)

                //파이어베이스에 새로 요청된 위치 정보를 저장하고 지도도 정보를 반영하는 로직

                //지도에 마커 움직이기
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //비동기로 이루어지므로 코드 순서는 크게 상관 없음
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestLocationPermission()
        setupFirebaseDatabase()
    }

    //다시 앱에 진입을 한다면, 중지한 locationRequest를 다시 시작하기 위해
    override fun onResume() {
        super.onResume()

        getCurrentLocation()
    }


    //oncreate에서 locationRequest를 요청했을 때, 앱이 백그라운드로 가면 locationRequest를 중지하기 위해
    override fun onPause() {
        super.onPause()

        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun getCurrentLocation(){
        /*
       * https://developer.android.com/develop/sensors-and-location/location/change-location-settings?hl=ko
       * Priority.PRIORITY_HIGH_ACCURACY는 가장 정확한 위치를 요청하는 것.
       * */
        val locationRequest = LocationRequest
            .Builder(Priority.PRIORITY_HIGH_ACCURACY,5 * 1000)
            .build()

        //위치 권한이 되었는지 검증 로직
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //권한을 얻지 못한다면,
            requestLocationPermission()
            return
        }

        //권한이 있는 상태
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        //마지막 위치를 가져와서 지도에 반영함
        fusedLocationClient.lastLocation.addOnSuccessListener {
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude),16F)
            )
        }
    }

    private fun requestLocationPermission(){
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun setupFirebaseDatabase(){
        Firebase.database.reference.child("Person")
            .addChildEventListener(object : ChildEventListener{

                //위치가 변경될때마다 작동하는 리스너
                //파이어베이스에서 데이터 가져와서 지도 마커 찍어줌
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val person = snapshot.getValue(Person::class.java) ?: return
                    val uid = person.uid ?: return

                    //마커 객체가 없다면,
                    if(markerMap[uid] == null){
                        markerMap[uid] = makeNewMarker(person,uid) ?: return //마커 객체 생성
                    }
                }

                //업데이트 객체 정보가 변경, 즉 객체의 위치가 변경된다면
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val person = snapshot.getValue(Person::class.java) ?: return
                    val uid = person.uid ?: return

                    //마커 객체가 없다면,
                    if(markerMap[uid] == null){
                        markerMap[uid] =  makeNewMarker(person,uid) ?: return //객체 생성
                    }else{
                        markerMap[uid]?.position = LatLng(person.latitude ?: 0.0,person.longitude ?: 0.0)
                    }

                    //내가 추적을 하고자하는 uid와 같다면,
                    if(uid == trackingPersonId){
                        googleMap.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(person.latitude ?: 0.0,person.longitude ?: 0.0))
                                    .zoom(16.0f)
                                    .build()
                            )
                        )
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {

                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {

                }

                override fun onCancelled(error: DatabaseError) {}

            })
    }

    //마커 객체 설정
    private fun makeNewMarker(person: Person, uid: String): Marker?{
        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(person.latitude ?: 0.0, person.longitude ?: 0.0))
                .title(person.name.orEmpty())
        ) ?: return null

        //마커 태그에 uid 입력
        marker.tag = uid


        //받아온 이미지를 overrid 메서드로 크기 조정
        Glide.with(this).asBitmap()
            .load(person.profilePhoto)
            //비트맵 이미지 커스텀은 transform으로 가능하고 내부에는 CircleCrop, CenterCrop 등이 있다.
            .transform(RoundedCorners(60))
            .override(200)
            .listener(object : RequestListener<Bitmap>{
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                //onLoadFailed 실행되면 실행
                //ui 작업이 아닌 네트워크 작업이기 때문에 thread 하나 만들어서 진행
                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: Target<Bitmap>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    //resource가 null 일 수 있기 때문에 let문을 이용해서 nullable 벗겨냄
                    resource?.let {
                        runOnUiThread {
                            marker.setIcon(
                                BitmapDescriptorFactory.fromBitmap(
                                    resource
                                )
                            )
                        }
                    }
                    return true
                }

            }).submit()


        return marker
    }


    override fun onMapReady(map: GoogleMap) {


        //구글맵 초기화
        googleMap = map
        //최대로 줌을 땡겼을 떄 레벨 설정
        googleMap.setMaxZoomPreference(20.0f)
        //최소로 줌을 떙겼을 때 레벨 설정
        googleMap.setMinZoomPreference(10.0f)


        googleMap.setOnMarkerClickListener(this)


        //맵 아무대나 클릭하면 추적 id를 빈값으로 두어 카메라 액션 멈춤
        googleMap.setOnMapClickListener {
            trackingPersonId = ""
        }



        //시드니 마커
//        googleMap = map
//        // Add a marker in Sydney and move the camera
//        val sydney = LatLng(-34.0, 151.0)
//        googleMap.addMarker(
//            MarkerOptions()
//            .position(sydney)
//            .title("Marker in Sydney"))
//        googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    //마커 클릭하면 실행하는 이벤트
    override fun onMarkerClick(marker: Marker): Boolean {
        //커스텀 이벤트

        //따라갈 사람 마커 추가
        trackingPersonId = marker.tag as? String ?: ""


        //true 리턴 시 기본동작이 되고, false하면 내가 커스텀한 기능이 실행.
        return false
    }
}
