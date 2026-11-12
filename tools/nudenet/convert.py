"""Builds the phone's man/woman detector (nudenet320.tflite) from NudeNet's 320n model
(https://github.com/notAI-tech/NudeNet, AGPL-3.0) and checks it against NudeNet itself:
 - the phone model (TensorFlow Lite) must find the same things as the original (ONNX) on two
   public test photos (Ultralytics' bus.jpg: several people; scikit-image's NASA astronaut: a woman);
 - the phone's own reading of the answer (NudeDetect.java, through DetectCheck.java) must match;
 - the phone's own picture preparation must match NudeNet's.
Run by .github/workflows/nsfw-model.yml."""
import json
import os
import subprocess
import sys
import urllib.request

import cv2
import numpy as np
from nudenet import NudeDetector
from nudenet import nudenet as nn
from skimage import data

ONNX = os.path.join(os.path.dirname(nn.__file__), "320n.onnx")
urllib.request.urlretrieve("https://ultralytics.com/images/bus.jpg", "bus.jpg")
cv2.imwrite("astronaut.jpg", cv2.cvtColor(data.astronaut(), cv2.COLOR_RGB2BGR))

# NudeNet's own weights (same model as 320n.onnx), converted with Ultralytics' exporter (YOLOv8).
urllib.request.urlretrieve("https://github.com/notAI-tech/NudeNet/releases/download/v3.4-weights/320n.pt", "320n.pt")
from ultralytics import YOLO
print("labels in the weights:", YOLO("320n.pt").names)
exported = YOLO("320n.pt").export(format="tflite", imgsz=320, half=True)
print("exported:", exported)
import glob
import shutil
lite_path = sorted(glob.glob("320n_saved_model/*float16.tflite") or glob.glob("**/*float16.tflite", recursive=True))[0]
shutil.copy(lite_path, "nudenet320.tflite")
print("nudenet320.tflite:", os.path.getsize("nudenet320.tflite") // 1024, "KB")

import tensorflow as tf
interp = tf.lite.Interpreter(model_path="nudenet320.tflite")
interp.allocate_tensors()
inp, out = interp.get_input_details()[0], interp.get_output_details()[0]
print("input", inp["shape"], "output", out["shape"])
json.dump({"input": [int(x) for x in inp["shape"]], "output": [int(x) for x in out["shape"]]}, open("shapes.json", "w"))

det = NudeDetector()
failures = 0
for name in ["bus.jpg", "astronaut.jpg"]:
    ref = det.detect(name)
    print(name, "original:", [(d["class"], round(d["score"], 2), d["box"]) for d in ref])
    blob, *_ , w, h = nn._read_image(name, 320)
    x = np.transpose(blob, (0, 2, 3, 1)).astype(np.float32)  # NCHW -> NHWC
    interp.set_tensor(inp["index"], x)
    interp.invoke()
    y = interp.get_tensor(out["index"])[0]
    if y.shape[0] != 22:
        y = y.T
    # Ultralytics' phone models give box positions as 0..1 of the picture; the phone code handles both.
    print(name, "largest box value:", float(np.abs(y[:4]).max()))
    y.astype("<f4").tofile(name + ".out.f32")
    json.dump({"w": w, "h": h, "anchors": int(y.shape[1]), "ref": ref}, open(name + ".json", "w"))
    # The phone's own reading of the answer (Java), compared with NudeNet's.
    r = subprocess.run(["java", "-cp", "out", "DetectCheck", name], capture_output=True, text=True)
    print(r.stdout.strip())
    if r.returncode != 0:
        print(r.stderr)
        failures += 1
    if os.path.exists(name + ".prep.f32"):
        mine = np.fromfile(name + ".prep.f32", dtype="<f4").reshape(320, 320, 3)
        diff = float(np.abs(mine - x[0]).mean())
        print(name, "picture preparation difference:", round(diff, 4))
        if diff > 0.03:
            print("the phone prepares pictures differently from NudeNet")
            failures += 1
    faces = [d for d in ref if d["class"].startswith("FACE_")]
    if name == "astronaut.jpg" and not any(d["class"] == "FACE_FEMALE" for d in faces):
        print("the original does not find the woman's face in astronaut.jpg")
        failures += 1

if failures:
    sys.exit(f"{failures} check(s) failed")
print("OK: the phone's man/woman detector matches NudeNet.")
