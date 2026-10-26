"""Do the Piper base voices actually produce audio for each language? Mirrors VoxCore.baseEngine()."""
import tarfile, urllib.request, os, sys
import numpy as np
import sherpa_onnx

REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
CASES = [
    ("vits-piper-en_US-lessac-medium-int8", "Hey Daniel, how are you doing?"),
    ("vits-piper-ar_JO-kareem-medium-int8", "مرحبا كيف حالك اليوم"),
    ("vits-piper-zh_CN-chaowen-medium-int8", "你好，今天怎么样？"),
]
bad = 0
for name, text in CASES:
    arc = f"{name}.tar.bz2"
    urllib.request.urlretrieve(f"{REL}/{arc}", arc)
    tarfile.open(arc).extractall(".")
    onnx = [f for f in os.listdir(name) if f.endswith(".onnx")][0]
    for data_dir in (os.path.abspath(f"{name}/espeak-ng-data"), ""):
        try:
            cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{name}/{onnx}", tokens=f"{name}/tokens.txt", data_dir=data_dir),
                num_threads=2))
            a = sherpa_onnx.OfflineTts(cfg).generate(text, sid=0, speed=1.0)
            print(f"{name} data_dir={'set' if data_dir else 'EMPTY'}: {len(a.samples)} samples @ {a.sample_rate} Hz, max={np.abs(np.array(a.samples)).max() if len(a.samples) else 0:.3f}", flush=True)
            if not data_dir and len(a.samples) == 0: pass
            elif data_dir and len(a.samples) == 0: bad += 1
        except Exception as e:
            print(f"{name} data_dir={'set' if data_dir else 'EMPTY'}: ERROR {e}", flush=True)
    print(sorted(os.listdir(name)))
print("BASE VOICES", "FAIL" if bad else "OK")
sys.exit(1 if bad else 0)
