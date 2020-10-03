import sys
import ctypes
from ctypes import c_double, sizeof

bit_size = sizeof(c_double) * 8
signed_limit = 2 ** -(bit_size - 1)
print(signed_limit)