echo "Add PPA repository"
sudo add-apt-repository ppa:nnstreamer/ppa
sudo add-apt-repository ppa:nnstreamer-example/ppa
sudo apt-get update

echo "Install Build tools"
sudo apt-get -y install git build-essential nnstreamer
sudo apt-get -y install ninja-build python-pip python3-pip libglib2.0-* python-gi python3-gi python-gst-1.0 \
	python3-gst-1.0 python-gst-1.0-dbg python3-gst-1.0-dbg libgstreamer-gl1.0-0 libgstreamer-opencv1.0-0 \
	libgstreamer-plugins-* libgstreamer1.0* libcairo-5c0 libcairo-gobject* libcairo2* \
	nnstreamer* tensorflow-* libprotobuf*

echo "Install Meson"
pip3 install --user meson

echo "Reboot the System and run build script"