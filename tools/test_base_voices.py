"""Which Piper archive variants actually produce audio in sherpa-onnx? Mirrors VoxCore.baseEngine()."""
import tarfile, urllib.request, os, sys
import numpy as np
import sherpa_onnx

REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
TEXT = {"en": "Hey Daniel, how are you doing?", "ar": "مرحبا كيف حالك اليوم", "zh": "你好，今天怎么样？"}
CASES = [
    ("en", "vits-piper-en_US-lessac-medium-int8"),
    ("ar", "vits-piper-ar_JO-kareem-medium-int8"),
    ("ar", "vits-piper-ar_JO-kareem-medium"),
    ("zh", "vits-piper-zh_CN-chaowen-medium-int8"),
]
print("sherpa-onnx", sherpa_onnx.__version__ if hasattr(sherpa_onnx, "__version__") else "?", flush=True)
for lang, name in CASES:
    arc = f"{name}.tar.bz2"
    try:
        urllib.request.urlretrieve(f"{REL}/{arc}", arc)
    except Exception as e:
        print(f"RESULT {name}: DOWNLOAD FAILED {e}", flush=True); continue
    tarfile.open(arc).extractall(".")
    files = sorted(os.listdir(name))
    onnx = [f for f in files if f.endswith(".onnx")][0]
    size = os.path.getsize(f"{name}/{onnx}") // 1_000_000
    lex = f"{name}/lexicon.txt" if os.path.exists(f"{name}/lexicon.txt") else ""
    try:
        cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{name}/{onnx}", tokens=f"{name}/tokens.txt",
                                                      lexicon=lex, data_dir=os.path.abspath(f"{name}/espeak-ng-data") if "espeak-ng-data" in files else ""),
            num_threads=2))
        a = sherpa_onnx.OfflineTts(cfg).generate(TEXT[lang], sid=0, speed=1.0)
        n = len(a.samples)
        print(f"RESULT {name}: {'OK' if n > 1000 else 'EMPTY'} {n} samples @ {a.sample_rate} Hz, model {size} MB, files {files}", flush=True)
    except Exception as e:
        print(f"RESULT {name}: ERROR {e}; files {files}", flush=True)


# ---- end to end: English reference clip -> Arabic speech in that voice (what the app does)
import subprocess
from scipy.io import wavfile
from huggingface_hub import hf_hub_download
import onnxruntime as ort
sys.path.insert(0, os.path.dirname(__file__))
from test_openvoice import spec, load, SR  # same STFT the Kotlin code mirrors

ext = ort.InferenceSession(hf_hub_download("eugenehp/openvoice", "tone_extract.onnx"))
cvt = ort.InferenceSession(hf_hub_download("eugenehp/openvoice", "tone_color.onnx"))
def embed(y):
    return ext.run(None, {"input": np.ascontiguousarray(spec(y)[None].transpose(0, 2, 1))})[0].reshape(1, 256, 1)

name = "vits-piper-ar_JO-kareem-medium-int8"
tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{name}/ar_JO-kareem-medium.onnx", tokens=f"{name}/tokens.txt",
                                              data_dir=os.path.abspath(f"{name}/espeak-ng-data")))))
a = tts.generate("مرحبا، هذا اختبار لقراءة الروايات بصوتي.", sid=0, speed=1.0)
base = np.array(a.samples, dtype=np.float32)
subprocess.run(["espeak-ng", "-v", "en-us+f3", "-p", "70", "-w", "ref_en.wav", "This is my English reference voice for the reader app."], check=True)
ref = load("ref_en.wav")
sp = spec(base)[None]
out = cvt.run(None, {"audio": sp, "audio_length": np.array([sp.shape[2]], dtype=np.int64),
                     "src_tone": embed(base), "dest_tone": embed(ref), "tau": np.array([0.3], dtype=np.float32)})[0][0, 0]
print(f"E2E arabic base {len(base)} samples -> converted {len(out)} samples, peak {np.abs(out).max():.3f}", flush=True)
assert len(out) > 10000 and np.isfinite(out).all() and np.abs(out).max() > 0.01
print("E2E OK")
