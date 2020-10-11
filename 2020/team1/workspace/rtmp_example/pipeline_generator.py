import time
import os
import sys
import gi
import logging
import math
import numpy as np
import ctypes
import cairo

gi.require_version('Gst', '1.0')
gi.require_foreign('cairo')
from gi.repository import Gst, GObject

class NNStreamerExample:
    """PIPELINE GENERATOR SIMULATION"""

    def __init__(self, argv=None):
        self.loop = None
        self.pipeline = None
        self.running = False
        self.video_caps = None

        self.BOX_SIZE = 4
        self.LABEL_SIZE = 2
        self.DETECTION_MAX = 1917
        self.MAX_OBJECT_DETECTION = 10

        self.Y_SCALE = 10.0
        self.X_SCALE = 10.0
        self.H_SCALE = 5.0
        self.W_SCALE = 5.0

        self.VIDEO_WIDTH = 640
        self.VIDEO_HEIGHT = 480

        self.FACE_MODEL_WIDTH = 300
        self.FACE_MODEL_HEIGHT = 300
        self.POSE_MODEL_WIDTH = 257
        self.POSE_MODEL_HEIGHT = 257

        self.KEYPOINT_SIZE = 17
        self.OUTPUT_STRIDE = 32
        self.GRID_XSIZE = 9
        self.GRID_YSIZE = 9

        self.SCORE_THRESHOLD = 0.7

        self.tflite_face_model = ''
        self.tflite_face_labels = []
        self.tflite_pose_model = ''
        self.tflite_pose_labels = []
        self.tflite_object_model = ''
        self.tflite_object_labels = []
        self.tflite_box_priors = []

        self.detected_faces = []
        self.detected_objects = []
        self.kps = [list(), list(), list(), list(), list()]

        self.pattern = None
        self.AUTH_KEY = argv[0]
        if len(argv) != 1:
            raise Exception("Youtube Auth Key is required")

        if not self.tflite_init():
            raise Exception

        self.BASE_PIPELINE = ('v4l2src name=cam_src ! videoconvert ! videoscale ! '
            'video/x-raw,width=' + str(self.VIDEO_WIDTH) + ',height=' + str(self.VIDEO_HEIGHT) + ',format=RGB ! tee name=t_raw ')
        self.BASE_MODEL_PIPE = ('t_raw. ! queue leaky=2 max-size-buffers=2 ! videoscale ! videorate ! '
            'video/x-raw,width=' + str(self.VIDEO_WIDTH) + ',height=' + str(self.VIDEO_HEIGHT) + ',framerate=15/1 ! tee name=model_handler ')
        self.FACE_DETECT_PIPE = ('model_handler. ! queue leaky=2 max-size-buffers=2 ! tensor_filter framework=tensorflow-lite model=' + self.tflite_object_model + ' ! tensor_sink name=res_object ')
        self.EYETRACK_PIPE = ('model_handler. ! queue leaky=2 max-size-buffers=2 ! videoconvert ! tee name=pose_split '
            'pose_split. ! queue leaky=2 max-size-buffers=2 ! videobox name=object0 ! videoflip method=horizontal-flip ! videoscale ! '
            'video/x-raw,width=' + str(self.POSE_MODEL_WIDTH) + ',height=' + str(self.POSE_MODEL_HEIGHT) + ',format=RGB ! tensor_converter ! '
            'tensor_transform mode=arithmetic option=typecast:float32,add:-127.5,div:127.5 ! '
            'tensor_filter framework=tensorflow-lite model=' + self.tflite_pose_model + ' ! tensor_sink name=posesink_0 '
            'pose_split. ! queue leaky=2 max-size-buffers=2 ! videobox name=object1 ! videoflip method=horizontal-flip ! videoscale ! '
            'video/x-raw,width=' + str(self.POSE_MODEL_WIDTH) + ',height=' + str(self.POSE_MODEL_HEIGHT) + ',format=RGB ! tensor_converter ! '
            'tensor_transform mode=arithmetic option=typecast:float32,add:-127.5,div:127.5 ! '
            'tensor_filter framework=tensorflow-lite model=' + self.tflite_pose_model + ' ! tensor_sink name=posesink_1 '
            'pose_split. ! queue leaky=2 max-size-buffers=2 ! videobox name=object2 ! videoflip method=horizontal-flip ! videoscale ! '
            'video/x-raw,width=' + str(self.POSE_MODEL_WIDTH) + ',height=' + str(self.POSE_MODEL_HEIGHT) + ',format=RGB ! tensor_converter ! '
            'tensor_transform mode=arithmetic option=typecast:float32,add:-127.5,div:127.5 ! '
            'tensor_filter framework=tensorflow-lite model=' + self.tflite_pose_model + ' ! tensor_sink name=posesink_2 ')
        self.OBJECT_DETECT_PIPE = ('model_handler. ! queue leaky=2 max-size-buffers=2 ! tensor_filter framework=tensorflow-lite model=' + self.tflite_face_model + ' ! tensor_sink name=res_face ')        
        self.BASE_OUTPUT_PIPE = ('t_raw. ! queue ! videoconvert ! cairooverlay name=tensor_res ! tee name=output_handler ')
        self.LOCAL_OUTPUT_PIPE = ('output_handler. ! queue ! videoconvert ! ximagesink name=output_local ')
        self.RTMP_OUTPUT_PIPE = ('output_handler. ! queue ! videoconvert ! x264enc bitrate=2000 byte-stream=false key-int-max=60 bframes=0 aud=true tune=zerolatency ! '
            'video/x-h264,profile=main ! flvmux streamable=true name=rtmp_mux '
            'rtmp_mux. ! rtmpsink location=rtmp://a.rtmp.youtube.com/live2/x/' + self.AUTH_KEY + ' '
            'alsasrc name=audio_src ! audioconvert ! audio/x-raw,rate=16000,format=S16LE,channels=1 ! voaacenc bitrate=16000 ! rtmp_mux. ')

        self.OPTION_FM = False
        self.OPTION_EM = False
        self.OPTION_OD = False
        self.OPTION_DM = False
        self.OPTION_XV = True
        self.OPTION_RTMP = True

        print("BASE_PIPELINE: ", self.BASE_PIPELINE)
        print("BASE_MODEL_PIPE: ", self.BASE_MODEL_PIPE)
        print("BASE_OUTPUT_PIPE: ", self.BASE_OUTPUT_PIPE)
        print("FACE_DETECT_PIPE: ", self.FACE_DETECT_PIPE)
        print("EYETRACK_PIPE: ", self.EYETRACK_PIPE)
        print("OBJECT_DETECT_PIPE: ", self.OBJECT_DETECT_PIPE)
        print("LOCAL_OUTPUT_PIPE: ", self.LOCAL_OUTPUT_PIPE)
        print("RTMP_OUTPUT_PIPE: ", self.RTMP_OUTPUT_PIPE)


        
        GObject.threads_init()
        Gst.init(argv)

    def tflite_init(self):
        """
        :return: True if successfully initialized
        """
        tflite_face_model = 'detect_face.tflite'
        tflite_face_label = 'labels_face.txt'
        tflite_pose_model = 'posenet_mobilenet_v1_100_257x257_multi_kpt_stripped.tflite'
        tflite_pose_label = 'key_point_labels.txt'
        tflite_object_model = 'ssd_mobilenet_v2_coco.tflite'
        tflite_object_label = 'coco_labels_list.txt'
        tflite_box_prior = "box_priors.txt"

        current_folder = os.path.dirname(os.path.abspath(__file__))
        model_folder = os.path.join(current_folder, 'tflite_model')
        pose_folder = os.path.join(current_folder, 'tflite_pose_estimation')

        self.tflite_face_model = os.path.join(model_folder, tflite_face_model)
        if not os.path.exists(self.tflite_face_model):
            logging.error('cannot find tflite model [%s]', self.tflite_face_model)
            return False

        label_path = os.path.join(model_folder, tflite_face_label)
        try:
            with open(label_path, 'r') as label_file:
                for line in label_file.readlines():
                    self.tflite_face_labels.append(line)
        except FileNotFoundError:
            logging.error('cannot find tflite label [%s]', label_path)
            return False

        self.tflite_pose_model = os.path.join(pose_folder, tflite_pose_model)
        if not os.path.exists(self.tflite_pose_model):
            logging.error('cannot find tflite model [%s]', self.tflite_pose_model)
            return False

        label_path = os.path.join(pose_folder, tflite_pose_label)
        try:
            with open(label_path, 'r') as label_file:
                for line in label_file.readlines():
                    self.tflite_pose_labels.append(line)
        except FileNotFoundError:
            logging.error('cannot find tflite label [%s]', label_path)
            return False

        self.tflite_object_model = os.path.join(model_folder, tflite_object_model)
        if not os.path.exists(self.tflite_object_model):
            logging.error('cannot find tflite model [%s]', self.tflite_object_model)
            return False

        label_path = os.path.join(model_folder, tflite_object_label)
        try:
            with open(label_path, 'r') as label_file:
                for line in label_file.readlines():
                    self.tflite_object_labels.append(line)
        except FileNotFoundError:
            logging.error('cannot find tflite label [%s]', label_path)
            return False

        box_prior_path = os.path.join(model_folder, tflite_box_prior)
        try:
            with open(box_prior_path, 'r') as box_prior_file:
                for line in box_prior_file.readlines():
                    datas = list(map(float, line.split()))
                    self.tflite_box_priors.append(datas)
        except FileNotFoundError:
            logging.error('cannot find tflite label [%s]', box_prior_path)
            return False

        print('finished to face labels, total [{}]'.format(len(self.tflite_face_labels)))
        print('finished to load object labels, total [{}]'.format(len(self.tflite_object_labels)))
        print('finished to pose labels, total [{}]'.format(len(self.tflite_pose_labels)))
        print('finished to load box_priors, total [{}]'.format(len(self.tflite_box_priors)))
        return True

if __name__ == '__main__':
    example = NNStreamerExample(sys.argv[1:])