"""Builds the phone's photo checker (nsfw.tflite) from opennsfw2 (MIT; Yahoo open_nsfw weights, BSD-2),
then checks it: the phone-sized model must give nearly the same answers as the original, and the
phone's own picture preparation (NudityPrep.java, run through PrepCheck.java) must match the
original's. Run by .github/workflows/nsfw-model.yml."""
import os
import subprocess
import sys

os.environ.setdefault("OPENNSFW2_KERAS", "tf-keras")
import numpy as np
import opennsfw2 as n2
import tensorflow as tf
from PIL import Image

model = n2.make_open_nsfw_model()
conv = tf.lite.TFLiteConverter.from_keras_model(model)
conv.optimizations = [tf.lite.Optimize.DEFAULT]  # weights stored as 8-bit: about a quarter of the size
open("nsfw.tflite", "wb").write(conv.convert())
print("nsfw.tflite:", os.path.getsize("nsfw.tflite") // 1024, "KB")

interp = tf.lite.Interpreter(model_path="nsfw.tflite")
interp.allocate_tensors()
inp, out = interp.get_input_details()[0], interp.get_output_details()[0]
print("input", inp["shape"], inp["dtype"], "output", out["shape"])


def lite(x):
    interp.set_tensor(inp["index"], x[None].astype(np.float32))
    interp.invoke()
    return float(interp.get_tensor(out["index"])[0][1])


# Test pictures: smooth colours, noise, and skin-coloured shapes (scores differ, so a mix-up shows).
rng = np.random.default_rng(1)
pics = []
yy, xx = np.mgrid[0:480, 0:640]
pics.append(np.stack([xx * 255 // 640, yy * 255 // 480, (xx + yy) * 255 // 1120], -1))
# Coloured blocks (pure noise would not do: the original squeezes pictures through JPEG first).
pics.append(np.kron(rng.integers(0, 256, (8, 6, 3)), np.ones((50, 50, 1))))
skin = np.zeros((500, 400, 3)); skin[:] = (60, 90, 140)
sy, sx = np.mgrid[0:500, 0:400]
skin[((sy - 250) ** 2 / 180 ** 2 + (sx - 200) ** 2 / 120 ** 2) < 1] = (224, 172, 140)
pics.append(skin)
worst_score, worst_prep = 0.0, 0.0
for i, p in enumerate(pics):
    img = Image.fromarray(np.uint8(p))
    img.save(f"t{i}.png")
    ref = n2.preprocess_image(img, n2.Preprocessing.YAHOO)
    keras_score = float(model.predict(ref[None], verbose=0)[0][1])
    lite_score = lite(ref)
    subprocess.run(["java", "-cp", "out", "PrepCheck", f"t{i}.png", f"t{i}.f32"], check=True)
    mine = np.fromfile(f"t{i}.f32", dtype="<f4").reshape(224, 224, 3)
    prep_diff = float(np.abs(mine - ref).mean())
    phone_score = lite(mine)
    print(f"picture {i}: original {keras_score:.4f}, phone model {lite_score:.4f}, phone prep {phone_score:.4f}, prep diff {prep_diff:.2f}")
    worst_score = max(worst_score, abs(keras_score - lite_score), abs(keras_score - phone_score))
    worst_prep = max(worst_prep, prep_diff)

# The original squeezes the picture through a JPEG first, so small differences are expected.
if worst_prep > 6 or worst_score > 0.1:
    sys.exit(f"The phone's photo checker does not match the original (prep {worst_prep:.2f}, score {worst_score:.3f}).")
print("OK: the phone's photo checker matches the original.")
