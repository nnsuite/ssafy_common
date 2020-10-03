# SSAFY NNStreamer Team 1 ssafy_common page

This directory stores files related to the SSAFY NNStreamer Team 1 project.

### Directory description

- build: This Directory stores shell scripts for setting nnstreamer develop environments and build nnstreamer-examples. (https://github.com/nnstreamer/nnstreamer-example)

    If you want to set develop environment using this scripts, Please use this in the following order.
  
    1) Run set_environment.sh first
    2) Run build_example.sh

- pixelation: This directory contains image processing analysis for gausian blur and pixelation to mask the faces. Most of analysis are based on OpenCV
- workspace: This directory stores working in progress source codes or waiting for a Pull Request availability due to licensing issues.
  - custom_overlay: Due to many requests for enabling OpenCV2 as overlay, We made customized OpenCV Overlay Plugin for use in nnstreamer_example. This overlay turns your video into np.ndarray, allowing you to use image post-processing features in OpenCV. The solutions to make this happen is based on here(http://lifestyletransfer.com/how-to-make-gstreamer-buffer-writable-in-python/). As a result of actually applying it, Image Post Processing is much easier than using cairo overlay. However, due to performance issues, it was put on hold for actual use. And also We had to check this code's Pull request availability. 
  - (Active) Dynamic Property: By manipulating properties in pipelines, This scripts can send multiple face detection datas to multiple ximagesinks, appsinks or even tensor_sink. This directory is really getting attention. By dynamically setting the properties of the plug-in connected to the pipeline, it is expected that face detection can be stored in multiple sinks, as well as some performance issues can be reduced in the process of applying Gaussian blur for each.
    In addition, as one of the main source codes, the results of face detection results can be processed with Tensor Filters, so it seems that Pose Estimation, which can only be supported by a single person, can be applied to multiple people. However, we have a memory leak issue, and we have a problem that needs to calibrate each coordinate for the detected human face, so we have to keep working.
  - Python_Practice: This Directory contains trial and errors while developing codes using python.