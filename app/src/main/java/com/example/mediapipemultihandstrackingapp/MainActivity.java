package com.example.mediapipemultihandstrackingapp;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.anyractive.net.AgentServerMainThread;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.DetectionProto.DetectionList;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;

import java.util.List;

/* Main activity of MediaPipe example apps.
 * MediaPipe 예제 앱의 주요 활동.*/

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private static final String BINARY_GRAPH_NAME = "multi_hand_tracking_mobile_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "multi_hand_landmarks";
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;


    // 카메라 미리보기 프레임을 Frame Processor로 전송하기 전에 수직으로 뒤집습니다.
    // MediaPipe 그래프에서 처리되고 처리된 프레임이 표시될 때 다시 뒤집습니다.
    // OpenGL은 영상 원점이 왼쪽 아래에 있다고 가정한 영상을 나타내기 때문에 이 작업이 필요합니다.
    // 일반적으로 MediaPipe는 영상 원점이 왼쪽 상단에 있다고 가정합니다.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;


    static {
        // 앱에 필요한 모든 기본 라이브러리를 로드합니다.
        System.loadLibrary("mediapipe_jni");

        // OpenCV(Open Source Computer Vision)은 실시간 컴퓨터 비전을 목적으로 한 프로그래밍 라이브러리이다.
        // 원래는 인텔이 개발하였다. 실시간 이미지 프로세싱에 중점을 둔 라이브러리이다
        System.loadLibrary("opencv_java3");
    }


    // 카메라 프레임에 액세스할 수 있는 위치
    private SurfaceTexture previewFrameTexture;


    // MediaPipe 그래프에 의해 처리된 카메라 미리 보기 프레임을 표시합니다.
    private SurfaceView previewDisplayView;


    // {@link EGLcontext}을(를) 생성하고 관리합니다.
    private EglManager eglManager;


    // 처리할 카메라 미리 보기 프레임을 MediaPipe 그래프로 보내고 처리된 프레임을 표시합니다.
    // 프레임이 {@link Surface}에 있습니다.
    private FrameProcessor processor;


    // Android 카메라에서 GL_TECTURE_EXTERNAL_OES 텍스처를 {@link FrameProcessor} 및
    // 기본 MediaPipe 그래프에서 사용할 일반 텍스처로 변환합니다.
    private ExternalTextureConverter converter;


    // {@link CameraX} Jetpack 지원 라이브러리를 통해 카메라 액세스를 처리합니다.
    private CameraXPreviewHelper cameraHelper;


    private Boolean isFirst = false;
    private Boolean isChecking = false;
    private float total = 0;
    private int count = 0;
    private float distance = 0;
    private float previous_X = 0;
    final Handler handler = new Handler();
    private float pre_X = 0;
    private float pre_Y = 0;
    private float ppre_X = 0;
    private float ppre_Y = 0;

    private float average_x;
    private float average_y;
    private float dev_aver_x;
    private float dev_aver_y;
    private float dev_aver = 1;

    // finger states
    boolean thumbIsOpen;
    boolean firstFingerIsOpen;
    boolean secondFingerIsOpen;
    boolean thirdFingerIsOpen;
    boolean fourthFingerIsOpen;

    // atom, 20201113, 서버모듈
    private AgentServerMainThread server;
    private int index = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        // atom, 20201113, 서버모듈
        this.server = new AgentServerMainThread();
        try {
            this.server.start();
        } catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
            this.server.stop();
        }


        // MediaPipe 기본 라이브러리가 앱 자산에 액세스할 수 있도록 자산 관리자를 초기화합니다(예: 이항 그래프)
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    Log.d(TAG, "Received multi-hand landmarks packet.");
                    List<NormalizedLandmarkList> multiHandLandmarks =
                            PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());
                    List<DetectionList> parmMarks = PacketGetter.getProtoVector(packet, DetectionList.parser());
                    Log.e(TAG, "[TS:"
                            + packet.getTimestamp()
                            + "] "
                            + getMultiHandLandmarksDebugString(multiHandLandmarks));
//                    Log.e(TAG, "[TS:"
//                                    + packet.getTimestamp()
//                                    + "] "
//                                    + getDetectionDebugString(parmMarks));

                });


        PermissionHelper.checkAndRequestCameraPermissions(this);
        PermissionHelper.checkAndRequestPermissions(this, new String[]{"android.permission.ACCESS_WIFI_STATE", "android.permission.ACCESS_NETWORK_STATE", "android.permission.INTERNET"});

    }

    @Override
    protected void onResume() {
        super.onResume();
        converter = new ExternalTextureConverter(eglManager.getContext());
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // atom, 20201113, 서버모듈
        if (this.server != null) {
            this.server.stop();
            this.server = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);
        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                // 디스플레이를 포함하는 Surface View의 크기를 기준으로
                                // 카메라 미리보기 디스플레이의 이상적인 크기(카메라 미리보기 프레임이 렌더링되는 영역, 스케일링 및 회전 가능)를 계산합니다.
                                Size viewSize = new Size(width, height);
                                Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);

                                Log.e(TAG, "  123123    surfaceChanged:");
                                // 미리 보기 FrameTexture를 통해 카메라 미리 보기 프레임에 컨버터를 입력으로 연결하고
                                // 출력 폭과 높이를 계산된 디스플레이 크기로 구성합니다.
                                converter.setSurfaceTextureAndAttachToGLContext(
                                        previewFrameTexture, 640, 480);//displaySize.getWidth(), displaySize.getHeight());
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    private void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    previewFrameTexture = surfaceTexture;

                    // 미리 보기 표시를 시작하려면 표시 보기를 표시합니다.
                    // 그러면 미리 보기 DisplayView(의 홀더)에 추가된 SurfaceHolder Callback이 트리거됩니다.
                    previewDisplayView.setVisibility(View.VISIBLE);
                });
        cameraHelper.startCamera(this, CAMERA_FACING, /*surfaceTexture=*/ null);
    }

//    private String getDetectionDebugString(List<DetectionList> objects) {
//        if (objects.isEmpty()) {
//            return "No Object";
//        }
//        String objectsStr = "Number of objects detected: " + objects.size() + "\n";
//        int objectIndex = 0;
//        for (DetectionList object : objects) {
//            objectsStr +=
//                    "\t#Hand landmarks for hand[" + objectIndex + "]: " + object.getDetectionCount() + "\n";
//
//            int landmarkIndex = 0;
//            for (Detection landmark : object.getDetectionList()) {
//                if (landmarkIndex == 8) {
//                }
//                objectsStr +=
//                        "\t\tobject ["
//                                + landmarkIndex
//                                + "]"
//                                + " : landmark - " + landmark + "\n";
//
////                for (String name : landmark.getDisplayNameList()) {
////                    objectsStr += " - Name : " + name + "\n";
////                }
//                ++landmarkIndex;
//            }
//            ++objectIndex;
//        }
//        return objectsStr;
//    }

    private String getMultiHandLandmarksDebugString(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
//            isFirst = true;
            return "No hand landmarks";
        } else {
//            if (isFirst) {
//                isChecking = true;
//                handler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        //Do something after 100ms
//                        isChecking = false;
//                    }
//                }, 3000);
//                isFirst = false;
//            }
        }
        String multiHandLandmarksStr = "";
        int handIndex = 0;
        int nDir = 0; //
        int index = 0;
        for (NormalizedLandmarkList landmarks : multiHandLandmarks) {
//            multiHandLandmarksStr +=
//                    "#hand[" + handIndex + "]: " + landmarks.getLandmarkCount() + "\n";
            int landmarkIndex = 0;
            distance = 0;
            distance = Math.abs(landmarks.getLandmarkList().get(4).getY() - landmarks.getLandmarkList().get(1).getY());

            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
//                if (isChecking) {
//                    total += landmark.getX();
//                    count ++;
//                }

//                if(landmarkIndex == 8) {
//                    multiHandLandmarksStr +=
//                            "Landmark ["
//                                    + landmarkIndex
//                                    + "]: ("
//                                    + landmark.getX()
//                                    + ", "
//                                    + landmark.getY()
//                                    + ", "
//                                    + landmark.getZ()
//                                    + ")";
//                }
                ++landmarkIndex;
            }
//            if (distance > 0.3) {
//                multiHandLandmarksStr += "distance : " + distance;// + "\n";
//                if ((previous_X - landmarks.getLandmarkList().get(1).getX()) < 0)
//                    multiHandLandmarksStr += "direction up " + "\n";
//                else
//                    multiHandLandmarksStr += "direction down " + "\n";
//                previous_X = landmarks.getLandmarkList().get(1).getX();
//            }
//            ++handIndex;


            //거리 필터링
            float sum_x = 0;
            float sum_y = 0;
            float deviation_x = 0;
            float deviation_y = 0;

            for (int i = 0; i < 21; i++) {
                sum_x += landmarks.getLandmarkList().get(i).getX();
                sum_y += landmarks.getLandmarkList().get(i).getY();
            }

            // 트래킹 손의 중심 좌표 (average_x , average_y)
            average_x = sum_x / 21;
            average_y = sum_y / 21;

            for (int i = 0; i < 21; i++) {
                deviation_x += Math.abs(landmarks.getLandmarkList().get(i).getX() - average_x);
                deviation_y += Math.abs(landmarks.getLandmarkList().get(i).getY() - average_y);
            }

            //각 손가락포인트 지점과 중심좌표 지점의 차이나는 거리의 평균
            dev_aver_x = deviation_x / 21;
            dev_aver_y = deviation_y / 21;

            dev_aver = (dev_aver_x + dev_aver_y) / 2;

//            Log.e(TAG, "  123123    deviation - average : " + dev_aver);


            // finger states
            thumbIsOpen = true;
            firstFingerIsOpen = false;
            secondFingerIsOpen = false;
            thirdFingerIsOpen = false;
            fourthFingerIsOpen = false;


            //손을 펼쳤는지 안펼쳤는지 제스처 필터링
            //안드로이드는 화면의 좌측 상단을 (0, 0)으로 하고 x좌표는 오른쪽으로 갈수록 증가하며 y좌표는 아래쪽으로 갈수록 증가하는 좌표계를 갖습니다.
            float pseudoFixKeyPoint;

            //엄지
            if (landmarks.getLandmarkList().get(4).getY() > landmarks.getLandmarkList().get(3).getY() - 0.025) {
                thumbIsOpen = false;
            }

            //검지
            pseudoFixKeyPoint = landmarks.getLandmarkList().get(6).getY();
            if (landmarks.getLandmarkList().get(7).getY() < pseudoFixKeyPoint && landmarks.getLandmarkList().get(8).getY() < pseudoFixKeyPoint) {
                firstFingerIsOpen = true;
            }

            //중지
            pseudoFixKeyPoint = landmarks.getLandmarkList().get(10).getY();
            if (landmarks.getLandmarkList().get(11).getY() < pseudoFixKeyPoint && landmarks.getLandmarkList().get(12).getY() < pseudoFixKeyPoint) {
                secondFingerIsOpen = true;
            }

            //약지
            pseudoFixKeyPoint = landmarks.getLandmarkList().get(14).getY();
            if (landmarks.getLandmarkList().get(15).getY() < pseudoFixKeyPoint && landmarks.getLandmarkList().get(16).getY() < pseudoFixKeyPoint) {
                thirdFingerIsOpen = true;
            }

            //새끼
            pseudoFixKeyPoint = landmarks.getLandmarkList().get(18).getY();
            if (landmarks.getLandmarkList().get(19).getY() < pseudoFixKeyPoint && landmarks.getLandmarkList().get(20).getY() < pseudoFixKeyPoint) {
                fourthFingerIsOpen = true;
            }

            if (firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
                break;
            }


//            Log.e(TAG, "  123123    4: " + landmarks.getLandmarkList().get(4).getY() + " 3: " + landmarks.getLandmarkList().get(3).getY() + "    thumsIsOpen: " + thumbIsOpen  );
//            Log.e(TAG, "  123123    1: " + thumbIsOpen + " 2: " + firstFingerIsOpen + " 3: " + secondFingerIsOpen + " 4: " + thirdFingerIsOpen + " 5: " + fourthFingerIsOpen);
//            Log.e(TAG, "  123123    6:" + landmarks.getLandmarkList().get(6).getY() + "    7: " + landmarks.getLandmarkList().get(7).getY() + "    8: " + landmarks.getLandmarkList().get(8).getY());

            ++index;
        }

        // sender using TCP/IP from atom //
        // 복수개의 손이 인식되었을 경우를 포함해서 배열의 첫번째 손을 꺼내고
        // 첫번째 손에서 검지손가락 스켈레톤의 데이터를 전송함.
        // 검지손가락 제외 나머지 손가락들의 중심값 추출


        // if (dev_aver > 0.072) {}
        // 거리로 제약


        if (multiHandLandmarks.size() > 0) {
            if (firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
                NormalizedLandmark landmark = multiHandLandmarks.get(index).getLandmarkList().get(8);
                int width = 1920;
                int height = 1080;
                float sum_x = 0;
                float sum_y = 0;
                for (int i = 0; i < 21; i++) {
                    if (i < 5 || i > 8) {
                        sum_x += multiHandLandmarks.get(index).getLandmarkList().get(i).getX();
                        sum_y += multiHandLandmarks.get(index).getLandmarkList().get(i).getY();
                    }
                }
                float x = sum_x / 16 * width;//landmark.getX() * width;
                float y = sum_y / 16 * height;//landmark.getY() * height;

                x = (ppre_X + pre_X + x) / 3;
                y = (ppre_Y + pre_Y + y) / 3;
                ppre_X = pre_X;
                ppre_Y = pre_Y;
                pre_X = x;
                pre_Y = y;

                Log.e(TAG, "@@@@@@@@@@@@@@@@@@@@@@@@@@@@get(0)    (" + x + " , " + y + ")");


                String head = String.format("api-command=%s&api-action=%s&api-method=%s", "tuio", "touch", "post");
                String body = String.format("x=%s&y=%s&z=%s&sz=%s&deviation=%s", x, y, distance, landmark.getZ(), "0");
                String packet = String.format("%s&content-length=%s\r\n\r\n%s", head, body.length(), body);
                this.server.broadcast(packet);
            }
        }
        return multiHandLandmarksStr;
    }
}