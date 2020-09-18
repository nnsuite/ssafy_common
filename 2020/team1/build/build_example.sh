echo -e "export NNST_ROOT=$HOME/nnstreamer\n\
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$NNST_ROOT/lib\n\
export GST_PLUGIN_PATH=$GST_PLUGIN_PATH:$NNST_ROOT/lib/gstreamer-1.0\n\
export C_INCLUDE_PATH=$C_INCLUDE_PATH:$NNST_ROOT/include\n\
export CPLUS_INCLUDE_PATH=$CPLUS_INCLUDE_PATH:$NNST_ROOT/include\n\
export PKG_CONFIG_PATH=$PKG_CONFIG_PATH:$NNST_ROOT/lib/pkgconfig\n" >> ~/.bashrc

source ~/.bashrc

git clone https://github.com/nnsuite/nnstreamer-example.git nnstreamer-example.git
cd nnstreamer-example.git
meson --prefix=${NNST_ROOT} --libdir=lib --bindir=bin --includedir=include build
ninja -C build install
rm -rf build

cd $NNST_ROOT/bin
./get-model.sh image-classification-tflite image-classification-caffe2 object-detection-tf object-detection-tflite speech-command image-segmentation-tflite text-classification-tflite pose-estimation-tflite

echo "Your NNStreamer develop environment is ready!"