#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import subprocess
import utils

def sign(unsigned_apk, signed_apk, keystore):
  print 'Signing (ignore the warnings)'
  cmd = ['zip', '-d', unsigned_apk, 'META-INF/*']
  utils.PrintCmd(cmd)
  subprocess.call(cmd)
  cmd = [
    'jarsigner',
    '-sigalg', 'SHA1withRSA',
    '-digestalg', 'SHA1',
    '-keystore', keystore,
    '-storepass', 'android',
    '-signedjar', signed_apk,
    unsigned_apk,
    'androiddebugkey'
  ]
  utils.PrintCmd(cmd)
  subprocess.check_call(cmd)
