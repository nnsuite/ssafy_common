# NNStreamer Tutorial for Hand Gesture app


```sh
- https://github.com/nnstreamer/nnstreamer-example/tree/master/android/example_app

We assume that you want to deploy a NNStreamer-based application on your own Android/ARM64bit target device. Also, we assume that you already have experienced Android application developments with Android Studio.
- Host PC : 
  -OS: Ubuntu 18.04 x86_64 LTS
  -VM: VMware Workstation 16 Player
  -Android Studio: Ubuntu version (However, the Window version will be compatible.)
- Target Device:
  - CPU : ARM 64bit (aarch64)
  - SDK : Min version 24 (Nougat)
  - ndroid NDK: Android NDK, Revision 18b (January 2019)
  - GStreamer : gstreamer-1.0-android-universal-1.15.1.tar.bz2
  
```


```sh

# NDK download url : https://developer.android.com/ndk/downloads/older_releases.html
NDK : Android NDK, Revision 18b (January 2019)

# GStreamer download url : https://gstreamer.freedesktop.org/data/pkg/android/
GStreamer : gstreamer-1.0-android-universal-1.15.1.tar.bz2

# Git Clone download url : git clone https://github.com/nnstreamer/nnstreamer
NNStreamer : cd nnstreamer / git reset --hard 6b27767fc0af96dcfd7dd3f84eef215bd50a7aaa

# Git Clone download url : https://github.com/nnstreamer/nnstreamer-example
NNStreamer-example : cd nnstreamer-example
```


```sh
- environment PATH 

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

# NDK download url : https://developer.android.com/ndk/downloads/older_releases.html
NDK : Android NDK, Revision 18b (January 2019)

# GStreamer download url : https://gstreamer.freedesktop.org/data/pkg/android/
GStreamer : gstreamer-1.0-android-universal-1.15.1.tar.bz2
mkdir $ANDROID_DEV_ROOT/gstreamer-1.0
cd $ANDROID_DEV_ROOT/gstreamer-1.0 
- move downloaded Gstreamer file in to gstreamer-1.0

# Modify content 
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


# Git Clone download url : git clone https://github.com/nnstreamer/nnstreamer
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


- Modify `build.gradle` 
- Modify default NDK PATH

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
development Status
### 2020.09.29

- Android development settings for all team members completed
- Solve related issues for example-tutorial (revert back to what was working normally)

### 2020.09.30

- After deciding to attach the hand-landmark model that exists in the Google Media Pipeline to the hand-detection model, development begins.
- Starting to get coordinate Values from NNstreamer-example application

### 2020.10.01

- Success in getting coordinates
- Tried agetting coordinates using the landmark model, but failed-> Change to swipe format using hand-detection coordinates

### 2020.10.02

- Swipe up, down, left, and right recognition success
- Command mapping for each gesture


```


