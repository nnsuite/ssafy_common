import timeit
import numpy as np
import cv2, os
import gi
import logging
import numpy as np

gi.require_version('Gst', '1.0')
gi.require_version('GstBase', '1.0')
from gi.repository import Gst, GObject, GstBase
from gi.repository.GObject import Signal
from .gst_mapptr import ndarray_converter

class GstCustomCV2Overlay(GstBase.BaseTransform):
    __gstmetadata__ = ("Custom CV2 overlay for Face Detection Application",
                        "custom_overlay/gst_custom_cv2_overlay.py",
                        "gst.Custom cv2 overlay",
                        "Custom cv2 overlay for object detection")

    __gsttemplates__ = (Gst.PadTemplate.new("src",
                                            Gst.PadDirection.SRC,
                                            Gst.PadPresence.ALWAYS,
                                            Gst.Caps.from_string("video/x-raw,format=RGB")),
                        Gst.PadTemplate.new("sink",
                                            Gst.PadDirection.SINK,
                                            Gst.PadPresence.ALWAYS,
                                            Gst.Caps.from_string("video/x-raw,format=RGB")))

    __gsignals__ = {
        'draw': (GObject.SIGNAL_RUN_FIRST, None,
                      (object,))
    }

    def __init__(self):
        super(GstCustomCV2Overlay, self).__init__()
        self.current_caps = None
        self.VIDEO_WIDTH = 640
        self.VIDEO_HEIGHT = 480
        self.CHANNELS = 3

    def do_transform_ip(self, buffer: Gst.Buffer) -> Gst.FlowReturn:
        if self.current_caps == None:
            self.current_caps = self.sinkpad.get_current_caps()
        try:
            image = ndarray_converter(buffer, self.VIDEO_WIDTH, self.VIDEO_HEIGHT, self.CHANNELS)
            self.emit('draw', image)
        except Exception as e:
            logging.error(e)
        return Gst.FlowReturn.OK

def register(plugin):
    type_to_register = GObject.type_register(GstCustomCV2Overlay)
    return Gst.Element.register(plugin, "gstcustomcv2overlay", 0, type_to_register)

def register_plugin(plugin_name):
    name = plugin_name
    description = "gst.Custom cv2 overlay"
    version = '0.0.3'
    gst_license = 'LGPL'
    source_module = 'gstreamer'
    package = 'gstcustomcv2overlay'
    origin = 'null'
    if not Gst.Plugin.register_static(Gst.VERSION_MAJOR, Gst.VERSION_MINOR,
                                      name, description,
                                      register, version, gst_license,
                                      source_module, package, origin):
        raise ImportError("Plugin {} not registered".format(plugin_name)) 
    return True

register_plugin("gstcustomcv2overlay")