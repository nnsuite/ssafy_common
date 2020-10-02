# NNStreamer Environment Setter Ver 0.7

# PPA must be added before installing meson. Default package manager in ubuntu offers older version of meson
echo "Add PPA repository"
sudo add-apt-repository ppa:nnstreamer/ppa
sudo add-apt-repository ppa:nnstreamer-example/ppa
sudo apt-get update

echo "Install Build tools"

sudo apt-get -y install git build-essential nnstreamer

if [[ $(lsb_release -rs) == "16.04" ]]; then
	echo "Ubuntu Version: 16.04: Run Additional Script"
	list=$(apt-cache --names-only search ^gstreamer1.0-* | awk '{ print $1 }' | grep -v gstreamer1.0-hybris)
	sudo apt-get install $list
	sudo apt-get -y install ninja-build meson python-pip python3-pip libglib2.0-* python-gi python3-gi python-gst-1.0 \
    python3-gst-1.0 python-gst-1.0-dbg python3-gst-1.0-dbg \
    libgirepository1.0-dev gir1.2-gstreamer-1.0 python-gst-1.0* \
    libgstreamer-plugins-* libgstreamer-plugins-base1.0-dev libgstreamer1.0* gstreamer1.0-plugins-* \
    libcairo-gobject* libcairo2* nnstreamer-* tensorflow-* libprotobuf* protobuf-compiler17 \
    libflatbuffers libflatbuffers-dev flatbuffers-compiler libjpeg-dev libgif-dev
else
	echo "Ubuntu Version: 18.04"
	sudo apt-get -y install ninja-build meson python-pip python3-pip libglib2.0-* python-gi python3-gi python-gst-1.0 \
		python3-gst-1.0 python-gst-1.0-dbg python3-gst-1.0-dbg libgstreamer-gl1.0-0 libgstreamer-opencv1.0-0 \
		libgirepository1.0-dev gir1.2-gstreamer-1.0 python-gst-1.0* \
		libgstreamer-plugins-* libgstreamer-plugins-base1.0-dev libgstreamer1.0* gstreamer1.0-plugins-* \
		libcairo-5c0 libcairo-gobject* libcairo2* nnstreamer-* tensorflow-* libprotobuf* protobuf-compiler17 \
		libflatbuffers libflatbuffers-dev flatbuffers-compiler libjpeg-dev libgif-dev 
fi

# echo "Install Meson"
# pip3 install --user meson

echo "Reboot the System and run build script"