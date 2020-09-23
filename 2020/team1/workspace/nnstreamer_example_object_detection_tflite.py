#!/usr/bin/env python

"""
@file		nnstreamer_example_object_detection_tflite.py
@date		13 Oct 2020
@brief		Python version of Tensor stream example with TF-Lite model for object detection
@see		https://github.com/nnsuite/nnstreamer
@author		SSAFY Team 1 <jangjongha.sw@gmail.com>
@bug		No known bugs.

This code is a Python port of Tensor stream example with TF-Lite model for object detection.

Pipeline :
v4l2src -- (TBU)

Get model by
$ cd $NNST_ROOT/bin
$ bash get-model.sh object-detection-tf

Run example :
Before running this example, GST_PLUGIN_PATH should be updated for nnstreamer plugin.
$ export GST_PLUGIN_PATH=$GST_PLUGIN_PATH:<nnstreamer plugin path>
$ python nnstreamer_example_object_detection_tflite.py

See https://lazka.github.io/pgi-docs/#Gst-1.0 for Gst API details.

Required model and resources are stored at below link
https://github.com/nnsuite/testcases/tree/master/DeepLearningModels/tensorflow-lite/ssd_mobilenet_v2_coco
"""

import os
import sys
import gi
import logging

gi.require_version('Gst', '1.0')
from gi.repository import Gst, GObject

class NNStreamerExample:
    """NNStreamer example for Object Detection."""
    
    def __init__(self, argv=None):
        self.loop = None
        self.pipeline = None
        self.running = False
        self.tflite_model = ''
        self.tflite_labels = []
        self.tflite_box_priors = []

        if not self.tflite_init():
            raise Exception

        GObject.threads_init()
        Gst.init(argv)

    def tflite_init(self):
        """
        :return: True if successfully initialized
        """
        tflite_model = 'ssd_mobilenet_v2_coco.tflite'
        tflite_label = 'coco_labels_list.txt'
        tflite_box_prior = "box_priors.txt"

        current_folder = os.path.dirname(os.path.abspath(__file__))
        model_folder = os.path.join(current_folder, 'tflite_model')

        self.tflite_model = os.path.join(model_folder, tflite_model)
        if not os.path.exists(self.tflite_model):
            logging.error('cannot find tflite model [%s]', self.tflite_model)
            return False

        label_path = os.path.join(model_folder, tflite_label)
        try:
            with open(label_path, 'r') as label_file:
                for line in label_file.readlines():
                    self.tflite_labels.append(line)
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

        print("{} {}".format(len(self.tflite_labels), len(self.tflite_box_priors)))

        logging.info('finished to load labels, total [%d]', len(self.tflite_labels))
        logging.info('finished to load box_priors, total [%d]', len(self.tflite_box_priors))
        return True

    # def run_example(self):
    #     """Init pipeline and run example.
    #     :return: None
    #     """

    #     print("Run: NNStreamer example for face detection.")

    #     # main loop
    #     self.loop = GObject.MainLoop()

    #     # init pipeline
    #     # Currently Only only runs video from webcam. More features TBU.
    #     self.pipeline = Gst.parse_launch(
    #         'v4l2src name=cam_src ! videoconvert ! videoscale ! '
    #         'video/x-raw,width=640,height=480,format=RGB ! videoconvert ! ximagesink name=img_tensor'
    #     )

    #     # bus and message callback
    #     bus = self.pipeline.get_bus()
    #     bus.add_signal_watch()
    #     bus.connect('message', self.on_bus_message)

    #     # start pipeline
    #     self.pipeline.set_state(Gst.State.PLAYING)
    #     self.running = True

    #     self.set_window_title('img_tensor', 'NNStreamer Face Detection Example')

    #     # run main loop
    #     self.loop.run()

    #     # quit when received eos or error message
    #     self.running = False
    #     self.pipeline.set_state(Gst.State.NULL)

    #     bus.remove_signal_watch()


    # def on_bus_message(self, bus, message):
    #     """Callback for message.
    #     :param bus: pipeline bus
    #     :param message: message from pipeline
    #     :return: None
    #     """
    #     if message.type == Gst.MessageType.EOS:
    #         logging.info('received eos message')
    #         self.loop.quit()
    #     elif message.type == Gst.MessageType.ERROR:
    #         error, debug = message.parse_error()
    #         logging.warning('[error] %s : %s', error.message, debug)
    #         self.loop.quit()
    #     elif message.type == Gst.MessageType.WARNING:
    #         error, debug = message.parse_warning()
    #         logging.warning('[warning] %s : %s', error.message, debug)
    #     elif message.type == Gst.MessageType.STREAM_START:
    #         logging.info('received start message')
    #     elif message.type == Gst.MessageType.QOS:
    #         data_format, processed, dropped = message.parse_qos_stats()
    #         format_str = Gst.Format.get_name(data_format)
    #         logging.debug('[qos] format[%s] processed[%d] dropped[%d]', format_str, processed, dropped)

    # def set_window_title(self, name, title):
    #     """Set window title.
    #     :param name: GstXImageasink element name
    #     :param title: window title
    #     :return: None
    #     """
    #     element = self.pipeline.get_by_name(name)
    #     if element is not None:
    #         pad = element.get_static_pad('sink')
    #         if pad is not None:
    #             tags = Gst.TagList.new_empty()
    #             tags.add_value(Gst.TagMergeMode.APPEND, 'title', title)
    #             pad.send_event(Gst.Event.new_tag(tags))

if __name__ == '__main__':
    example = NNStreamerExample(sys.argv[1:])
    # example.run_example()