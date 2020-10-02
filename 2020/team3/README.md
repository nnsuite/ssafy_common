# NNStreamer Tutorial Review

## 조건
```sh
- https://github.com/nnstreamer/nnstreamer-example/tree/master/android/example_app

자체 Android / ARM64 비트 대상 장치에 NNStreamer 기반 애플리케이션을 배포한다고 가정합니다. 또한 Android Studio를 사용하여 이미 Android 애플리케이션 개발을 경험했다고 가정합니다.
```


```sh
- PC, Device 조건
OS: Ubuntu 18.04.5
VM: VMware Workstation 16 Player

CPU : ARM 64bit (aarch64)
SDK : Min version 24 (Nougat)

# NDK 다운 경로 : https://developer.android.com/ndk/downloads/older_releases.html
NDK : Android NDK, Revision 18b (January 2019)

# GStreamer 다운 경로 : https://gstreamer.freedesktop.org/data/pkg/android/
GStreamer : gstreamer-1.0-android-universal-1.15.1.tar.bz2

# Git Clone 다운 경로 : git clone https://github.com/nnstreamer/nnstreamer
NNStreamer : cd nnstreamer / git reset --hard 6b27767fc0af96dcfd7dd3f84eef215bd50a7aaa

# Git Clone 다운 경로 : https://github.com/nnstreamer/nnstreamer-example
NNStreamer-example : cd nnstreamer-example
```


```sh
- PATH 설정 (사용자 경로에 맞게 설정해주세요)

$ export ANDROID_DEV_ROOT=$HOME/Android           # Set your own path (default location: $HOME/Android)
$ mkdir -p $ANDROID_DEV_ROOT/tools/sdk
$ mkdir -p $ANDROID_DEV_ROOT/tools/ndk
$ mkdir -p $ANDROID_DEV_ROOT/gstreamer-1.0
$ mkdir -p $ANDROID_DEV_ROOT/workspace
$ nano ~/.bashrc

export JAVA_HOME=/opt/android-studio/jre            # JRE path in Android Studio
export ANDROID_DEV_ROOT=$HOME/android               # Set your own path (default location: "$HOME/Android".)

export ANDROID_SDK=$ANDROID_DEV_ROOT/tools/sdk
export ANDROID_NDK=$ANDROID_DEV_ROOT/tools/ndk
export ANDROID_SDK_ROOT=$ANDROID_SDK
export ANDROID_NDK_ROOT=$ANDROID_NDK
export GSTREAMER_ROOT_ANDROID=$ANDROID_DEV_ROOT/gstreamer-1.0
export NNSTREAMER_ROOT=$ANDROID_DEV_ROOT/workspace/nnstreamer
```

```sh
- 설치 (Android Studio Install 생략)

# install
sudo apt install subversion curl pkg-config

# licence
cd $ANDROID_SDK/tools/bin
yes | ./sdkmanager --licenses

# NDK 다운 : https://developer.android.com/ndk/downloads/older_releases.html
NDK : Android NDK, Revision 18b (January 2019)

# GStreamer 다운 경로 : https://gstreamer.freedesktop.org/data/pkg/android/
GStreamer : gstreamer-1.0-android-universal-1.15.1.tar.bz2
mkdir $ANDROID_DEV_ROOT/gstreamer-1.0
cd $ANDROID_DEV_ROOT/gstreamer-1.0 -> 안에다가 내용물 넣어주세요

# 내용 수정
$GSTREAMER_ROOT_ANDROID/{Target-ABI}/share/gst-android/ndk-build/gstreamer-1.0.mk

@@ -127,2 +127,2 @@
GSTREAMER_PLUGINS_CLASSES    := $(strip \
            $(subst $(GSTREAMER_NDK_BUILD_PATH),, \
            $(foreach plugin,$(GSTREAMER_PLUGINS), \
-           $(wildcard $(GSTREAMER_NDK_BUILD_PATH)$(plugin)/*.java))))
+           $(wildcard $(GSTREAMER_NDK_BUILD_PATH)/$(plugin)/*.java))))

GSTREAMER_PLUGINS_WITH_CLASSES := $(strip \
            $(subst $(GSTREAMER_NDK_BUILD_PATH),, \
            $(foreach plugin, $(GSTREAMER_PLUGINS), \
-           $(wildcard $(GSTREAMER_NDK_BUILD_PATH)$(plugin)))))
+           $(wildcard $(GSTREAMER_NDK_BUILD_PATH)/$(plugin)))))

@@ -257,1 +257,1 @@

copyjavasource_$(TARGET_ARCH_ABI):
  $(hide)$(call host-mkdir,$(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer)
    $(hide)$(foreach plugin,$(GSTREAMER_PLUGINS_WITH_CLASSES), \
        $(call host-mkdir,$(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer/$(plugin)) && ) echo Done mkdir
    $(hide)$(foreach file,$(GSTREAMER_PLUGINS_CLASSES), \
-       $(call host-cp,$(GSTREAMER_NDK_BUILD_PATH)$(file),$(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer/$(file)) && ) echo Done cp
+       $(call host-cp,$(GSTREAMER_NDK_BUILD_PATH)/$(file),$(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer/$(file)) && ) echo Done cp


$GSTREAMER_ROOT_ANDROID/{Target-ABI}/share/gst-android/ndk-build/gstreamer_android-1.0.c.in
@@ -592,9 +592,10 @@

   if (!klass) {
     __android_log_print (ANDROID_LOG_ERROR, "GStreamer",
         "Could not retrieve class org.freedesktop.gstreamer.GStreamer");
-    return 0;
-  }
-  if ((*env)->RegisterNatives (env, klass, native_methods,
+
+    if ((*env)->ExceptionCheck (env))
+      (*env)->ExceptionClear (env);
+  } else if ((*env)->RegisterNatives (env, klass, native_methods,
           G_N_ELEMENTS (native_methods))) {
     __android_log_print (ANDROID_LOG_ERROR, "GStreamer",
         "Could not register native methods for org.freedesktop.gstreamer.GStreamer");


# Git Clone 다운 경로 : git clone https://github.com/nnstreamer/nnstreamer
cd $ANDROID_DEV_ROOT/workspace
git clone https://github.com/nnstreamer/nnstreamer.git
cd nnstreamer
git reset --hard 6b27767fc0af96dcfd7dd3f84eef215bd50a7aaa

# NNStramer Build
cd $NNSTREAMER_ROOT
bash ./api/android/build-android-lib.sh
```


```sh
# nnstreamer example download : https://github.com/nnstreamer/nnstreamer-example/tree/master/android/example_app
cd $ANDROID_DEV_ROOT/workspace
git clone https://github.com/nnstreamer/nnstreamer-example.git

# example setting
cd $ANDROID_DEV_ROOT/workspace/nnstreamer-example/android/example_app/common/jni
tar xJf ./extfiles.tar.xz
curl -O https://raw.githubusercontent.com/nnstreamer/nnstreamer-android-resource/master/external/tensorflow-lite-1.13.1.tar.xz
tar xJf ./tensorflow-lite-1.13.1.tar.xz # Check tensorflow-lite version and extract prebuilt library
ls ahc tensorflow-lite

# copy
cd $NNSTREAMER_ROOT/android_lib
cp nnstreamer-[DATE].aar $ANDROID_DEV_ROOT/workspace/nnstreamer-example/android/example_app/api-sample/libs
cp nnstreamer-[DATE].aar $ANDROID_DEV_ROOT/workspace/nnstreamer-example/android/example_app/use-camera-with-nnstreamer-java-api/libs

# build, copy
# https://github.com/nnstreamer/nnstreamer-example/blob/master/android/example_app/capi-sample/README.md
cd $NNSTREAMER_ROOT
bash ./api/android/build-android-lib.sh --enable_nnfw=no --enable_snpe=no

cd $NNSTREAMER_ROOT/android_lib
cp nnstreamer-native-[DATE].zip $ANDROID_DEV_ROOT/workspace/nnstreamer-example/android/example_app/capi-sample/src
cd $ANDROID_DEV_ROOT/workspace/nnstreamer-example/android/example_app/capi-sample/src
unzip nnstreamer-native-[DATE].zip
```


```sh
# https://github.com/nnstreamer/nnstreamer-example/tree/master/android/example_app
Run Android Studio.
Import project in Android Studio.
Check a target NDK version (File - Project Structure)
Install

build.gradle 내용 수정, NDK 경로 수정
build.gradle
buildscript {
    repositories {
        jcenter() // or mavenCentral()
        mavenCentral()
        maven{url "https://maven.google.com"}
        google()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.5.1'
    }
}
```
```sh
개발 현황 
### 2020.09.29

- 모든 팀원 안드로이드 개발 세팅완료 
- example commit 관련 문제 해결 (정상 작동하던 시점으로 revert)

### 2020.09.30

- hand-detection 모델에 구글 미디어 파이프라인에 존재하는 hand-landmark 모델을 붙이기로 결정 후 개발에 착수
- 기존 example에 있던 예시에서 좌표값 받아오기 작업에 착수

### 2020.10.01

- 좌표값 받아오기 성공
- landmark 모델을 이용한 제스처를 시도해 보았으나 실패 -> hand-detection의 좌표값을 이용해 스와이프 형식으로 변경 

### 2020.10.02

- 스와이프로 상하좌우 인식 성공 
- 각 제스쳐에 해당하는 명령어 매핑
- 로고 생성

```


