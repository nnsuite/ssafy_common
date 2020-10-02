import os
from ctypes import *
from typing import Tuple
from contextlib import contextmanager
import numpy as np
import gi
gi.require_version('Gst', '1.0')
from gi.repository import Gst

# This soultion is heavily relying on solution by lifestyletransfer
# src: http://lifestyletransfer.com/how-to-make-gstreamer-buffer-writable-in-python/

GST_PADDING = 4

class GstMapInfo(Structure):
    _fields_ = [("memory", c_void_p),        # GstMemory *memory
                ("flags", c_int),            # GstMapFlags flags
                ("data", POINTER(c_byte)),   # guint8 *data
                ("size", c_size_t),          # gsize size
                ("maxsize", c_size_t),       # gsize maxsize
                ("user_data", c_void_p * 4), # gpointer user_data[4]
                ("_gst_reserved", c_void_p * GST_PADDING)]

GST_MAP_INFO_POINTER = POINTER(GstMapInfo)

libgst = CDLL("libgstreamer-1.0.so.0")
libgst.gst_buffer_map.argtypes = [c_void_p, GST_MAP_INFO_POINTER, c_int]
libgst.gst_buffer_map.restype = c_int
libgst.gst_buffer_unmap.argtypes = [c_void_p, GST_MAP_INFO_POINTER]
libgst.gst_buffer_unmap.restype = None
libgst.gst_mini_object_is_writable.argtypes = [c_void_p]
libgst.gst_mini_object_is_writable.restype = c_int

@contextmanager
def map_gst_buffer(buffer) -> GST_MAP_INFO_POINTER:
    ptr = hash(buffer)
    mapping = GstMapInfo()
    get_buffer_map = libgst.gst_buffer_map(ptr, mapping, Gst.MapFlags.READ)

    if not get_buffer_map:
        raise RuntimeError("No buffer")
    try:
        yield cast(mapping.data, POINTER(c_byte * mapping.size)).contents
    finally:
        libgst.gst_buffer_unmap(ptr, mapping)

def ndarray_converter(buffer, width, height, channels):
    with map_gst_buffer(buffer) as map_data:
        result = np.ndarray(buffer.get_size(), buffer=map_data, dtype=np.uint8)
    result = result.reshape(height, width, channels).squeeze()
    return result
