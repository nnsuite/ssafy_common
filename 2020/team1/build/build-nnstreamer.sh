# Currently Working in Progress

# 0. Add ppa to Ubuntu and apt-get update (This is crucial)
sudo add-apt-repository ppa:nnstreamer/ppa
sudo apt-get update

# 1. Update to Python 3.8.5
sudo apt-get -y install git build-essential checkinstall
sudo apt-get -y install libreadline-gplv2-dev libncursesw5-dev libssl-dev libsqlite3-dev tk-dev libgdbm-dev libc6-dev libbz2-dev libffi-dev zlib1g-dev

cd /opt
sudo wget https://www.python.org/ftp/python/3.8.5/Python-3.8.5.tgz
sudo tar xzf Python-3.8.5.tgz
cd Python-3.8.5/
sudo ./configure --enable-optimizations
sudo make altinstall
sudo update-alternatives --install /usr/bin/python python /usr/local/bin/python3.8 1
cd ~

# 2. Install meson and ninja-build
# Warning: meson installed via pip to solve lexer issue
sudo apt-get -y install ninja-build cmake
pip install --user meson


# 3. Install GStreamer related libraries
sudo apt-get -y install python-gi python3-gi python-gst-1.0 python3-gst-1.0 python-gst-1.0-dbg python3-gst-1.0-dbg
sudo apt-get -y install libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-doc gstreamer1.0-tools gstreamer1.0-x gstreamer1.0-alsa gstreamer1.0-gl gstreamer1.0-gtk3 gstreamer1.0-qt5 gstreamer1.0-pulseaudio
sudo apt-get -y install gstreamer1.0-plugins-* gstreamer1.0-python3-plugin-loader gstreamer1.0-clutter-3.0 gstreamer1.0-fluendo-mp3 gstreamer1.0-packagekit gstreamer1.0-vaapi
sudo apt-get -y install libgstreamer-gl1.0-0 libgstreamer-ocaml libgstreamer-ocaml-dev libgstreamer-opencv1.0-0 libgstreamer-plugins-*

# 4. Build protobuf
sudo apt-get -y install autoconf automake libtool curl make g++ unzip

wget https://github.com/protocolbuffers/protobuf/releases/download/v3.13.0/protobuf-all-3.13.0.tar.gz
sudo tar xzf protobuf-all-3.13.0.tar.gz
cd protobuf-3.13.0/
git submodule update --init --recursive
sudo ./autogen.sh
sudo ./configure
sudo make
sudo make check
sudo make install
sudo ldconfig
cd ..

# 5. Build Flatbuffer
git clone https://github.com/google/flatbuffers.git
cd flatbuffers
cmake -G "Unix Makefiles"
sudo make install
cd ..


# Build One
sudo apt-get -y install build-essential clang-format-3.9 cmake doxygen git graphviz hdf5-tools lcov libatlas-base-dev \
libboost-all-dev libgflags-dev libgoogle-glog-dev libgtest-dev \
libhdf5-dev pylint python3 python3-pip python3-venv scons software-properties-common unzip wget
pip install yapf==0.22.0 numpy

# 5. Install Tensorflow and Tensorflow-lite
sudo apt-get install -y tensorflow-lite-dev tensorflow-c tensorflow-c-dev

# 6. Install other requirements
sudo apt-get install libflatbuffers libflatbuffers-dev bazel-dist libgtest-dev libcairo2-dev
sudo apt-get install pkg-config libcairo2-dev gcc python3-dev libgirepository1.0-dev
pip install gobject PyGObject

# 6. Set the Path to bashrc
echo -e "export NNST_ROOT=$HOME/nnstreamer\n\
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$NNST_ROOT/lib\n\
export GST_PLUGIN_PATH=$GST_PLUGIN_PATH:$NNST_ROOT/lib/gstreamer-1.0\n\
export C_INCLUDE_PATH=$C_INCLUDE_PATH:$NNST_ROOT/include\n\
export CPLUS_INCLUDE_PATH=$CPLUS_INCLUDE_PATH:$NNST_ROOT/include\n\
export PKG_CONFIG_PATH=$PKG_CONFIG_PATH:$NNST_ROOT/lib/pkgconfig" >> ~/.bashrc

# 7. Compile nnstreamer
git clone https://github.com/nnsuite/nnstreamer.git nnstreamer.git
cd nnstreamer.git
meson build
ninja -C build install
cd ..

# 8. Compile nnstreamer-example
git clone https://github.com/nnsuite/nnstreamer-example.git nnstreamer-example.git
cd nnstreamer-example.git
meson build
ninja -C build install
rm -rf build
cd ..

# Build result: Not working right now. tensor-converter not found